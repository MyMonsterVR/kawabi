package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.TrackerTokenStore
import com.mymonstervr.kawabi.data.track.dto.TrackSearchResult
import com.mymonstervr.kawabi.data.track.kitsu.KitsuApi
import com.mymonstervr.kawabi.data.track.kitsu.KitsuTracker
import com.mymonstervr.kawabi.data.track.myanimelist.MyAnimeListApi
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
    private val malApi: MyAnimeListApi,
    private val kitsuApi: KitsuApi,
    private val kitsuTracker: KitsuTracker,
) {
    suspend fun search(trackerId: String, query: String): Result<List<TrackSearchResult>> = runCatching {
        when (trackerId) {
            TrackerTokenStore.TRACKER_MAL -> malApi.search(query)
            TrackerTokenStore.TRACKER_KITSU -> kitsuApi.search(query)
            else -> error("Unknown tracker: $trackerId")
        }
    }

    /**
     * Binds [mangaId] to [result] on [trackerId]. Checks for an existing remote
     * list entry first (the standard tracker `bind()` pattern) rather than blindly creating
     * one -- if the user already has progress on MAL/Kitsu from elsewhere,
     * that's adopted via `max(local, remote)` instead of being overwritten.
     */
    suspend fun link(mangaId: Long, trackerId: String, result: TrackSearchResult): Result<Track> = runCatching {
        val localChaptersRead = currentLocalChaptersRead(mangaId)

        val track = when (trackerId) {
            TrackerTokenStore.TRACKER_MAL -> linkMal(mangaId, result, localChaptersRead)
            TrackerTokenStore.TRACKER_KITSU -> linkKitsu(mangaId, result, localChaptersRead)
            else -> error("Unknown tracker: $trackerId")
        }

        if (track.lastChapterRead > localChaptersRead) {
            chapterRepository.markReadUpToNumber(mangaId, track.lastChapterRead)
        }
        track
    }

    private suspend fun linkMal(mangaId: Long, result: TrackSearchResult, localChaptersRead: Double): Track {
        val existing = malApi.findListItem(result.remoteId)
        val chaptersRead = maxOf(localChaptersRead, existing?.chaptersRead ?: 0.0)
        val status = existing?.status ?: statusFor(chaptersRead, result.totalChapters)
        val score = existing?.score ?: 0.0
        malApi.upsertListItem(result.remoteId, chaptersRead, status, score)
        val trackId = trackRepository.link(
            Track(
                id = 0,
                mangaId = mangaId,
                trackerId = TrackerTokenStore.TRACKER_MAL,
                remoteId = result.remoteId,
                libraryId = null,
                title = result.title,
                trackingUrl = "https://myanimelist.net/manga/${result.remoteId}",
                totalChapters = existing?.totalChapters?.takeIf { it > 0 } ?: result.totalChapters,
                lastChapterRead = chaptersRead,
                score = score,
                status = status,
            ),
        )
        return trackRepository.getForManga(mangaId).first { it.id == trackId }
    }

    private suspend fun linkKitsu(mangaId: Long, result: TrackSearchResult, localChaptersRead: Double): Track {
        val userId = kitsuTracker.userId ?: error("Kitsu: not authenticated")
        val existing = kitsuApi.findLibManga(result.remoteId, userId)
        val chaptersRead = maxOf(localChaptersRead, existing?.chaptersRead ?: 0.0)
        val status = existing?.status ?: statusFor(chaptersRead, result.totalChapters)
        val score = existing?.score ?: 0.0
        val libraryId = existing?.libraryId?.also { kitsuApi.updateLibManga(it, chaptersRead, status, score) }
            ?: kitsuApi.addLibManga(result.remoteId, userId, chaptersRead, status, score)
        val trackId = trackRepository.link(
            Track(
                id = 0,
                mangaId = mangaId,
                trackerId = TrackerTokenStore.TRACKER_KITSU,
                remoteId = result.remoteId,
                libraryId = libraryId,
                title = result.title,
                trackingUrl = "https://kitsu.app/manga/${result.remoteId}",
                totalChapters = existing?.totalChapters?.takeIf { it > 0 } ?: result.totalChapters,
                lastChapterRead = chaptersRead,
                score = score,
                status = status,
            ),
        )
        return trackRepository.getForManga(mangaId).first { it.id == trackId }
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
        when (track.trackerId) {
            TrackerTokenStore.TRACKER_MAL -> malApi.upsertListItem(track.remoteId, chaptersRead, status, score)
            TrackerTokenStore.TRACKER_KITSU -> {
                val libraryId = track.libraryId ?: error("Kitsu: missing library id")
                kitsuApi.updateLibManga(libraryId, chaptersRead, status, score)
            }
            else -> error("Unknown tracker: ${track.trackerId}")
        }
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
                when (track.trackerId) {
                    TrackerTokenStore.TRACKER_MAL -> malApi.upsertListItem(track.remoteId, localChaptersRead, track.status, track.score)
                    TrackerTokenStore.TRACKER_KITSU -> track.libraryId?.let {
                        kitsuApi.updateLibManga(it, localChaptersRead, track.status, track.score)
                    }
                }
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
        val remoteChaptersRead = when (track.trackerId) {
            TrackerTokenStore.TRACKER_MAL -> malApi.findListItem(track.remoteId)?.chaptersRead ?: track.lastChapterRead
            TrackerTokenStore.TRACKER_KITSU -> {
                val userId = kitsuTracker.userId ?: return
                kitsuApi.findLibManga(track.remoteId, userId)?.chaptersRead ?: track.lastChapterRead
            }
            else -> return
        }

        val merged = maxOf(localChaptersRead, remoteChaptersRead, track.lastChapterRead)
        if (merged > localChaptersRead) chapterRepository.markReadUpToNumber(mangaId, merged)
        if (merged > track.lastChapterRead) trackRepository.updateChaptersRead(track.id, merged)

        if (merged > remoteChaptersRead) {
            when (track.trackerId) {
                TrackerTokenStore.TRACKER_MAL -> malApi.upsertListItem(track.remoteId, merged, track.status, track.score)
                TrackerTokenStore.TRACKER_KITSU -> track.libraryId?.let { kitsuApi.updateLibManga(it, merged, track.status, track.score) }
            }
        }
    }

    private suspend fun currentLocalChaptersRead(mangaId: Long): Double =
        chapterRepository.getForManga(mangaId).filter { it.read }.maxOfOrNull { it.chapterNumber } ?: 0.0

    private fun statusFor(chaptersRead: Double, totalChapters: Double): String =
        if (totalChapters > 0 && chaptersRead >= totalChapters) TrackStatus.COMPLETED else TrackStatus.READING
}
