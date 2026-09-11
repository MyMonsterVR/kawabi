package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.TrackEntry
import com.mymonstervr.kawabi.data.network.TrackerApi
import com.mymonstervr.kawabi.data.track.dto.TrackSearchResult
import com.mymonstervr.kawabi.data.track.trackingUrlFor
import com.mymonstervr.kawabi.domain.model.AnimeTrack
import com.mymonstervr.kawabi.domain.model.MediaType
import com.mymonstervr.kawabi.domain.model.TrackStatus
import com.mymonstervr.kawabi.domain.repository.AnimeTrackRepository
import com.mymonstervr.kawabi.domain.repository.EpisodeRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Anime axis of [TrackerSyncClient] -- same monotonic-max/explicit-user-action split, the
 * anime canonical statuses, and every [TrackerApi] call carrying [MediaType.ANIME] so the
 * backend hits MAL `/anime/...` / Kitsu `kind=anime`. Kept as its own class rather than
 * generalizing [TrackerSyncClient] over a media axis: the two differ in which repositories
 * they touch, not just in the wire field, and decision D8 explicitly favours a parallel
 * stack over threading a discriminator through the manga code.
 */
class AnimeTrackerSyncClient(
    private val animeTrackRepository: AnimeTrackRepository,
    private val episodeRepository: EpisodeRepository,
    private val trackerApi: TrackerApi,
) {
    suspend fun search(trackerId: String, query: String): Result<List<TrackSearchResult>> = runCatching {
        trackerApi.search(trackerId, query, MediaType.ANIME)
    }

    suspend fun link(animeId: Long, trackerId: String, result: TrackSearchResult): Result<AnimeTrack> = runCatching {
        val localEpisodesWatched = currentLocalEpisodesWatched(animeId)

        val existing = trackerApi.findEntry(trackerId, result.remoteId, MediaType.ANIME)
        val episodesWatched = maxOf(localEpisodesWatched, existing?.chaptersRead ?: 0.0)
        val status = existing?.status ?: statusFor(episodesWatched, result.totalChapters)
        val score = existing?.score ?: 0.0
        trackerApi.upsertEntry(trackerId, result.remoteId, status, episodesWatched, score, MediaType.ANIME)

        val trackId = animeTrackRepository.link(
            AnimeTrack(
                id = 0,
                animeId = animeId,
                trackerId = trackerId,
                remoteId = result.remoteId,
                libraryId = null,
                title = result.title,
                trackingUrl = trackingUrlFor(trackerId, result.remoteId, MediaType.ANIME),
                totalEpisodes = existing?.totalChapters?.takeIf { it > 0 } ?: result.totalChapters,
                lastEpisodeWatched = episodesWatched,
                score = score,
                status = status,
            ),
        )
        val track = animeTrackRepository.getForAnime(animeId).first { it.id == trackId }

        if (track.lastEpisodeWatched > localEpisodesWatched) {
            episodeRepository.markWatchedUpToNumber(animeId, track.lastEpisodeWatched)
        }
        track
    }

    /** Local-only -- does not delete the remote MAL/Kitsu list entry, same as the manga side. */
    suspend fun unlink(animeId: Long, trackerId: String) {
        animeTrackRepository.unlink(animeId, trackerId)
    }

    /** Explicit user edit -- always pushes exactly what's given, no monotonic-max. */
    suspend fun updateTrackDetails(track: AnimeTrack, episodesWatched: Double, status: String, score: Double): Result<Unit> =
        runCatching {
            trackerApi.upsertEntry(track.trackerId, track.remoteId, status, episodesWatched, score, MediaType.ANIME)
            animeTrackRepository.updateTrackDetails(track.id, episodesWatched, status, score)
            if (episodesWatched > currentLocalEpisodesWatched(track.animeId)) {
                episodeRepository.markWatchedUpToNumber(track.animeId, episodesWatched)
            }
        }

    /**
     * Explicit local watch-state change (mark watched *or unwatched*) always wins -- sets
     * every linked tracker to exactly the current local max, in either direction. Same
     * deliberate non-monotonic behaviour as the manga side's `pushLocalChaptersRead`.
     */
    suspend fun pushLocalEpisodesWatched(animeId: Long) {
        val localEpisodesWatched = currentLocalEpisodesWatched(animeId)
        for (track in animeTrackRepository.getForAnime(animeId)) {
            if (track.lastEpisodeWatched == localEpisodesWatched) continue
            runCatching {
                trackerApi.upsertEntry(track.trackerId, track.remoteId, track.status, localEpisodesWatched, track.score, MediaType.ANIME)
                animeTrackRepository.updateTrackDetails(track.id, localEpisodesWatched, track.status, track.score)
            }
        }
    }

    /** Pulls every linked tracker's episode count, pushes back up whichever side was behind. */
    suspend fun syncAnime(animeId: Long) = coroutineScope {
        val localEpisodesWatched = currentLocalEpisodesWatched(animeId)
        animeTrackRepository.getForAnime(animeId)
            .map { track -> async { runCatching { syncTrack(animeId, track, localEpisodesWatched) } } }
            .awaitAll()
    }

    private suspend fun syncTrack(animeId: Long, track: AnimeTrack, localEpisodesWatched: Double) {
        val entry: TrackEntry? = trackerApi.findEntry(track.trackerId, track.remoteId, MediaType.ANIME)
        val remoteEpisodesWatched = entry?.chaptersRead ?: track.lastEpisodeWatched

        val merged = maxOf(localEpisodesWatched, remoteEpisodesWatched, track.lastEpisodeWatched)
        if (merged > localEpisodesWatched) episodeRepository.markWatchedUpToNumber(animeId, merged)
        if (merged > track.lastEpisodeWatched) animeTrackRepository.updateEpisodesWatched(track.id, merged)

        if (merged > remoteEpisodesWatched) {
            trackerApi.upsertEntry(track.trackerId, track.remoteId, track.status, merged, track.score, MediaType.ANIME)
        }
    }

    private suspend fun currentLocalEpisodesWatched(animeId: Long): Double =
        episodeRepository.getForAnime(animeId).filter { it.watched }.maxOfOrNull { it.episodeNumber } ?: 0.0

    private fun statusFor(episodesWatched: Double, totalEpisodes: Double): String =
        if (totalEpisodes > 0 && episodesWatched >= totalEpisodes) TrackStatus.COMPLETED else TrackStatus.WATCHING
}
