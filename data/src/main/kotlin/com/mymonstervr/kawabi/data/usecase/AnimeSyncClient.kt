package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.TokenStore
import com.mymonstervr.kawabi.data.network.dto.AnimeEntryDto
import com.mymonstervr.kawabi.data.network.dto.AnimeProgressDto
import com.mymonstervr.kawabi.data.network.dto.AnimeTrackDto
import com.mymonstervr.kawabi.data.track.trackingUrlFor
import com.mymonstervr.kawabi.domain.model.AnimeTrack
import com.mymonstervr.kawabi.domain.model.MediaType
import com.mymonstervr.kawabi.domain.model.TrackStatus
import com.mymonstervr.kawabi.domain.repository.AnimeRepository
import com.mymonstervr.kawabi.domain.repository.AnimeTrackRepository
import com.mymonstervr.kawabi.domain.repository.EpisodeRepository

/**
 * Anime counterpart of [SyncClient], against `/anime/entries` + `/anime/progress`. Same
 * push-everything-then-pull-and-merge shape, and the same asymmetric safety rule: a pulled
 * state may only ever raise local `watched`/`episodes_watched`, never lower it.
 *
 * The one deliberate difference from the manga client is `position_ms`/`duration_ms`: the
 * contract makes those last-write-wins by `updated_at` rather than monotonic-max, because a
 * resume position legitimately moves backwards (re-watching, seeking back). Since the
 * server row carries the authoritative `updated_at` and the local row doesn't track one,
 * the pulled position is only applied to an episode that isn't already marked watched --
 * enough to resume on a second device without ever undoing a finished episode.
 */
class AnimeSyncClient(
    private val animeApi: AnimeApi,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val animeTrackRepository: AnimeTrackRepository,
    private val addAnimeToLibrary: AddAnimeToLibrary,
    private val mergeDuplicateAnimes: MergeDuplicateAnimes,
    private val tokenStore: TokenStore,
) {
    suspend fun sync(): Result<Unit> = runCatching {
        if (!tokenStore.isLoggedIn.value) return@runCatching
        mergeDuplicateAnimes.merge()
        push()
        pull()
    }

    private suspend fun push() {
        val now = System.currentTimeMillis()
        val favorites = animeRepository.getFavorites()
        val entries = favorites.map { anime ->
            val episodes = episodeRepository.getForAnime(anime.id)
            AnimeEntryDto(
                key = anime.key,
                source = anime.source,
                url = anime.url,
                title = anime.title,
                cover_url = anime.thumbnailUrl,
                status = anime.status,
                total_episodes = anime.totalEpisodes,
                episodes_watched = episodes.filter { it.watched }.maxOfOrNull { it.episodeNumber } ?: 0.0,
                favorite = true,
                last_watched_at = anime.lastWatchedAt.takeIf { it > 0 },
                tracks = animeTrackRepository.getForAnime(anime.id).map { track ->
                    AnimeTrackDto(
                        tracker = track.trackerId,
                        remote_id = track.remoteId,
                        status = track.status,
                        progress = track.lastEpisodeWatched,
                        total = track.totalEpisodes,
                        score = track.score,
                        updated_at = track.updatedAt.takeIf { it > 0 }
                            ?: anime.lastModifiedAt.takeIf { it > 0 }
                            ?: now,
                    )
                },
            )
        }
        if (entries.isNotEmpty()) animeApi.postEntries(entries)

        val progress = favorites.flatMap { anime ->
            episodeRepository.getForAnime(anime.id)
                .filter { it.watched || it.positionMs > 0 }
                .map { episode ->
                    AnimeProgressDto(
                        anime_key = anime.key,
                        episode_key = episode.key,
                        episode_number = episode.episodeNumber,
                        position_ms = episode.positionMs,
                        duration_ms = episode.durationMs,
                        watched = episode.watched,
                    )
                }
        }
        if (progress.isNotEmpty()) animeApi.postProgress(progress)
    }

    private suspend fun pull() {
        val response = animeApi.getEntries().getOrElse { return }
        val animeIdByKey = mutableMapOf<String, Long>()
        for (entry in response.entries) {
            if (entry.deleted_at != null) continue

            // A stored row for this key isn't necessarily a library row any more -- the
            // details screen keeps a non-favorite one for every key it opens -- so a
            // server entry has to (re)assert favorite rather than assume it.
            val anime = animeRepository.getByKey(entry.key)
                ?.also { if (!it.favorite) animeRepository.setFavorite(it.id, true) }
                ?: addAnimeToLibrary.add(entry.key, entry.cover_url).getOrNull()
                ?: continue

            if (entry.episodes_watched > 0) {
                episodeRepository.markWatchedUpToNumber(anime.id, entry.episodes_watched)
            }
            entry.last_watched_at?.let { animeRepository.touchLastWatched(anime.id, it) }
            entry.cover_url?.let { animeRepository.fillMissingThumbnail(anime.id, it) }
            applyTracks(anime.id, entry)
            animeIdByKey[entry.key] = anime.id
        }
        applyAllProgress(animeIdByKey)
    }

    /**
     * Restores/merges the server's tracker links for one anime, so a fresh device gets its
     * MAL/Kitsu/AniList links back from `/anime/entries` instead of needing a re-import.
     * `progress` is monotonic-max like everywhere else; the rest is last-write-wins on the
     * row's `updated_at`, and a tombstoned row unlinks locally.
     */
    private suspend fun applyTracks(animeId: Long, entry: AnimeEntryDto) {
        for (dto in entry.tracks) {
            if (dto.tracker.isBlank()) continue
            val existing = animeTrackRepository.getByAnimeAndTracker(animeId, dto.tracker)
            if (dto.deleted_at != null) {
                if (existing != null) animeTrackRepository.unlink(animeId, dto.tracker)
                continue
            }
            val remoteWins = existing == null || dto.updated_at > existing.updatedAt
            val remoteId = (if (remoteWins) dto.remote_id else existing!!.remoteId).ifBlank { existing?.remoteId.orEmpty() }
            if (remoteId.isBlank()) continue
            val trackingUrl = runCatching { trackingUrlFor(dto.tracker, remoteId, MediaType.ANIME) }.getOrNull()
                ?: existing?.trackingUrl
                ?: continue
            animeTrackRepository.link(
                AnimeTrack(
                    id = existing?.id ?: 0,
                    animeId = animeId,
                    trackerId = dto.tracker,
                    remoteId = remoteId,
                    libraryId = existing?.libraryId,
                    title = existing?.title?.takeIf { it.isNotBlank() } ?: entry.title,
                    trackingUrl = trackingUrl,
                    totalEpisodes = if (remoteWins) dto.total else existing!!.totalEpisodes,
                    lastEpisodeWatched = maxOf(dto.progress, existing?.lastEpisodeWatched ?: 0.0),
                    score = if (remoteWins) dto.score else existing!!.score,
                    status = if (remoteWins) {
                        dto.status.ifBlank { existing?.status?.takeIf { it.isNotBlank() } ?: TrackStatus.WATCHING }
                    } else {
                        existing!!.status
                    },
                    updatedAt = maxOf(dto.updated_at, existing?.updatedAt ?: 0L),
                ),
            )
        }
    }

    private suspend fun applyAllProgress(animeIdByKey: Map<String, Long>) {
        val response = animeApi.getProgress().getOrElse { return }
        for (progress in response.entries) {
            if (progress.anime_key !in animeIdByKey) continue
            val local = episodeRepository.getByKey(progress.episode_key) ?: continue
            val nextWatched = local.watched || progress.watched
            val nextPosition = if (nextWatched) local.positionMs else maxOf(local.positionMs, progress.position_ms)
            val nextDuration = maxOf(local.durationMs, progress.duration_ms)
            if (nextWatched != local.watched || nextPosition != local.positionMs || nextDuration != local.durationMs) {
                episodeRepository.setProgress(local.id, nextWatched, nextPosition, nextDuration)
            }
        }
    }
}
