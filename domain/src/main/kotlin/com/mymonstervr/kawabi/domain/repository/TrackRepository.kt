package com.mymonstervr.kawabi.domain.repository

import com.mymonstervr.kawabi.domain.model.Track

interface TrackRepository {
    suspend fun getForManga(mangaId: Long): List<Track>
    suspend fun getByMangaAndTracker(mangaId: Long, trackerId: String): Track?
    suspend fun link(track: Track): Long
    suspend fun unlink(mangaId: Long, trackerId: String)
    suspend fun updateChaptersRead(trackId: Long, chaptersRead: Double)
    suspend fun updateTotalChapters(trackId: Long, totalChapters: Double)

    /** Explicit user edit (manual chapter/status/score correction) -- not monotonic-max. */
    suspend fun updateTrackDetails(trackId: Long, chaptersRead: Double, status: String, score: Double)
}
