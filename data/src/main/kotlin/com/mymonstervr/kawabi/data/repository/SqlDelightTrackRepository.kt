package com.mymonstervr.kawabi.data.repository

import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.db.KawabiDatabase
import com.mymonstervr.kawabi.data.db.toDomain
import com.mymonstervr.kawabi.domain.model.Track
import com.mymonstervr.kawabi.domain.repository.TrackRepository
import kotlinx.coroutines.withContext

class SqlDelightTrackRepository(
    private val db: KawabiDatabase,
    private val dispatchers: AppDispatchers,
) : TrackRepository {

    private val trackQueries = db.tracksQueries

    override suspend fun getForManga(mangaId: Long): List<Track> = withContext(dispatchers.io) {
        trackQueries.selectByManga(mangaId).executeAsList().map { it.toDomain() }
    }

    override suspend fun getByMangaAndTracker(mangaId: Long, trackerId: String): Track? = withContext(dispatchers.io) {
        trackQueries.selectByMangaAndTracker(mangaId, trackerId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun link(track: Track): Long = withContext(dispatchers.io) {
        db.transactionWithResult {
            trackQueries.insertTrack(
                track.mangaId,
                track.trackerId,
                track.remoteId,
                track.libraryId,
                track.title,
                track.trackingUrl,
                track.totalChapters,
                track.lastChapterRead,
            )
            trackQueries.lastInsertTrackRowId().executeAsOne()
        }
    }

    override suspend fun unlink(mangaId: Long, trackerId: String): Unit = withContext(dispatchers.io) {
        trackQueries.deleteByMangaAndTracker(mangaId, trackerId)
    }

    override suspend fun updateChaptersRead(trackId: Long, chaptersRead: Double): Unit = withContext(dispatchers.io) {
        trackQueries.updateChaptersRead(chaptersRead, trackId)
    }

    override suspend fun updateTotalChapters(trackId: Long, totalChapters: Double): Unit = withContext(dispatchers.io) {
        trackQueries.updateTotalChapters(totalChapters, trackId)
    }

    override suspend fun updateTrackDetails(trackId: Long, chaptersRead: Double, status: String, score: Double): Unit =
        withContext(dispatchers.io) {
            trackQueries.updateTrackDetails(chaptersRead, status, score, trackId)
        }
}
