package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.TokenStore
import com.mymonstervr.kawabi.data.network.dto.AnimeEntryDto
import com.mymonstervr.kawabi.data.network.dto.AnimeProgressDto
import com.mymonstervr.kawabi.domain.repository.AnimeRepository
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
    private val addAnimeToLibrary: AddAnimeToLibrary,
    private val tokenStore: TokenStore,
) {
    suspend fun sync(): Result<Unit> = runCatching {
        if (!tokenStore.isLoggedIn.value) return@runCatching
        push()
        pull()
    }

    private suspend fun push() {
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

            val anime = animeRepository.getByKey(entry.key)
                ?: addAnimeToLibrary.add(entry.key).getOrNull()
                ?: continue

            if (entry.episodes_watched > 0) {
                episodeRepository.markWatchedUpToNumber(anime.id, entry.episodes_watched)
            }
            entry.last_watched_at?.let { animeRepository.touchLastWatched(anime.id, it) }
            animeIdByKey[entry.key] = anime.id
        }
        applyAllProgress(animeIdByKey)
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
