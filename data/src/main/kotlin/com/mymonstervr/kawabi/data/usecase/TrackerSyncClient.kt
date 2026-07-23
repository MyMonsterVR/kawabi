package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.TrackEntry
import com.mymonstervr.kawabi.data.network.TrackerApi
import com.mymonstervr.kawabi.data.network.TrackerTokenStore
import com.mymonstervr.kawabi.data.track.dto.TrackSearchResult
import com.mymonstervr.kawabi.domain.model.Track
import com.mymonstervr.kawabi.domain.model.TrackStatus
import com.mymonstervr.kawabi.domain.repository.ChapterRepository
import com.mymonstervr.kawabi.domain.repository.TrackRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Per-manga tracker linking (title-search -> bind) and chapter-read
 * push/pull, mirroring [SyncClient]'s monotonic-max rule: a pulled tracker
 * count never regresses local read state, and vice versa -- only the higher
 * of the two ever wins. Status/score are explicit-user-action only (see
 * [updateTrackDetails]) -- background sync never touches them.
 */
class TrackerSyncClient(
    private val trackRepository: TrackRepository,
    private val chapterRepository: ChapterRepository,
    private val trackerApi: TrackerApi,
) {
    suspend fun search(trackerId: String, query: String): Result<List<TrackSearchResult>> = runCatching {
        trackerApi.search(trackerId, query)
    }

    /**
     * Binds [mangaId] to [result] on [trackerId]. Checks for an existing remote
     * list entry first (the standard tracker `bind()` pattern) rather than blindly creating
     * one -- if the user already has progress on MAL/Kitsu from elsewhere,
     * that's adopted via `max(local, remote)` instead of being overwritten.
     */
    suspend fun link(mangaId: Long, trackerId: String, result: TrackSearchResult): Result<Track> = runCatching {
        val localChaptersRead = currentLocalChaptersRead(mangaId)

        val existing = trackerApi.findEntry(trackerId, result.remoteId)
        val chaptersRead = maxOf(localChaptersRead, existing?.chaptersRead ?: 0.0)
        val status = existing?.status ?: statusFor(chaptersRead, result.totalChapters)
        val score = existing?.score ?: 0.0
        trackerApi.upsertEntry(trackerId, result.remoteId, status, chaptersRead, score)

        val trackId = trackRepository.link(
            Track(
                id = 0,
                mangaId = mangaId,
                trackerId = trackerId,
                remoteId = result.remoteId,
                libraryId = null,
                title = result.title,
                trackingUrl = trackingUrlFor(trackerId, result.remoteId),
                totalChapters = existing?.totalChapters?.takeIf { it > 0 } ?: result.totalChapters,
                lastChapterRead = chaptersRead,
                score = score,
                status = status,
            ),
        )
        val track = trackRepository.getForManga(mangaId).first { it.id == trackId }

        if (track.lastChapterRead > localChaptersRead) {
            chapterRepository.markReadUpToNumber(mangaId, track.lastChapterRead)
        }
        track
    }

    /** Local-only -- does not delete the remote MAL/Kitsu list entry, see PLAN. */
    suspend fun unlink(mangaId: Long, trackerId: String) {
        trackRepository.unlink(mangaId, trackerId)
    }

    /**
     * Explicit user edit (manual chapter/status/score correction, mirrors the standard
     * `TrackInfoDialog` pattern) -- always pushes exactly what's given, no monotonic-max
     * (the user might legitimately lower the count).
     */
    suspend fun updateTrackDetails(track: Track, chaptersRead: Double, status: String, score: Double): Result<Unit> = runCatching {
        trackerApi.upsertEntry(track.trackerId, track.remoteId, status, chaptersRead, score)
        trackRepository.updateTrackDetails(track.id, chaptersRead, status, score)
        if (chaptersRead > currentLocalChaptersRead(track.mangaId)) {
            chapterRepository.markReadUpToNumber(track.mangaId, chaptersRead)
        }
    }

    /**
     * Explicit local read-state change (mark read *or unread*, via chapter taps/
     * long-press) always wins -- sets every linked tracker's chapter count to
     * exactly the current local max-read, in either direction. Unlike [syncManga]
     * this is NOT monotonic-max: marking a chapter unread must actually lower a
     * linked tracker's count, not have the very next sync silently push it back up
     * (owner feedback -- "if something is unread, it's unread").
     */
    suspend fun pushLocalChaptersRead(mangaId: Long) {
        val localChaptersRead = currentLocalChaptersRead(mangaId)
        for (track in trackRepository.getForManga(mangaId)) {
            if (track.lastChapterRead == localChaptersRead) continue
            runCatching {
                trackerApi.upsertEntry(track.trackerId, track.remoteId, track.status, localChaptersRead, track.score)
                trackRepository.updateTrackDetails(track.id, localChaptersRead, track.status, track.score)
            }
        }
    }

    /** Pulls every linked tracker's chapter count for [mangaId], pushes back up whichever side was behind. */
    suspend fun syncManga(mangaId: Long) = coroutineScope {
        val localChaptersRead = currentLocalChaptersRead(mangaId)
        trackRepository.getForManga(mangaId)
            .map { track -> async { runCatching { syncTrack(mangaId, track, localChaptersRead) } } }
            .awaitAll()
    }

    private suspend fun syncTrack(mangaId: Long, track: Track, localChaptersRead: Double) {
        val entry: TrackEntry? = trackerApi.findEntry(track.trackerId, track.remoteId)
        val remoteChaptersRead = entry?.chaptersRead ?: track.lastChapterRead

        val merged = maxOf(localChaptersRead, remoteChaptersRead, track.lastChapterRead)
        if (merged > localChaptersRead) chapterRepository.markReadUpToNumber(mangaId, merged)
        if (merged > track.lastChapterRead) trackRepository.updateChaptersRead(track.id, merged)

        if (merged > remoteChaptersRead) {
            trackerApi.upsertEntry(track.trackerId, track.remoteId, track.status, merged, track.score)
        }
    }

    private suspend fun currentLocalChaptersRead(mangaId: Long): Double =
        chapterRepository.getForManga(mangaId).filter { it.read }.maxOfOrNull { it.chapterNumber } ?: 0.0

    private fun statusFor(chaptersRead: Double, totalChapters: Double): String =
        if (totalChapters > 0 && chaptersRead >= totalChapters) TrackStatus.COMPLETED else TrackStatus.READING

    private fun trackingUrlFor(trackerId: String, remoteId: String): String = when (trackerId) {
        TrackerTokenStore.TRACKER_MAL -> "https://myanimelist.net/manga/$remoteId"
        TrackerTokenStore.TRACKER_KITSU -> "https://kitsu.app/manga/$remoteId"
        else -> error("Unknown tracker: $trackerId")
    }
}
