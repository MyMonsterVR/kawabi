package com.mymonstervr.kawabi.data.repository

import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.db.KawabiDatabase
import com.mymonstervr.kawabi.data.db.toDomain
import com.mymonstervr.kawabi.domain.model.AnimeTrack
import com.mymonstervr.kawabi.domain.repository.AnimeTrackRepository
import kotlinx.coroutines.withContext

class SqlDelightAnimeTrackRepository(
    private val db: KawabiDatabase,
    private val dispatchers: AppDispatchers,
) : AnimeTrackRepository {

    private val trackQueries = db.animeTracksQueries

    override suspend fun getForAnime(animeId: Long): List<AnimeTrack> = withContext(dispatchers.io) {
        trackQueries.selectByAnime(animeId).executeAsList().map { it.toDomain() }
    }

    override suspend fun getByAnimeAndTracker(animeId: Long, trackerId: String): AnimeTrack? = withContext(dispatchers.io) {
        trackQueries.selectByAnimeAndTracker(animeId, trackerId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getRemoteIdsByTracker(trackerId: String): Map<Long, String> = withContext(dispatchers.io) {
        trackQueries.selectRemoteIdsByTracker(trackerId).executeAsList()
            .filter { it.remote_id.isNotBlank() }
            .associate { it.anime_id to it.remote_id }
    }

    override suspend fun link(track: AnimeTrack): Long = withContext(dispatchers.io) {
        db.transactionWithResult {
            trackQueries.insertAnimeTrack(
                track.animeId,
                track.trackerId,
                track.remoteId,
                track.libraryId,
                track.title,
                track.trackingUrl,
                track.totalEpisodes,
                track.lastEpisodeWatched,
                track.score,
                track.status,
                track.updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            )
            trackQueries.lastInsertAnimeTrackRowId().executeAsOne()
        }
    }

    override suspend fun unlink(animeId: Long, trackerId: String): Unit = withContext(dispatchers.io) {
        trackQueries.deleteByAnimeAndTracker(animeId, trackerId)
    }

    override suspend fun updateEpisodesWatched(trackId: Long, episodesWatched: Double): Unit = withContext(dispatchers.io) {
        trackQueries.updateEpisodesWatched(episodesWatched, System.currentTimeMillis(), trackId)
    }

    override suspend fun updateTotalEpisodes(trackId: Long, totalEpisodes: Double): Unit = withContext(dispatchers.io) {
        trackQueries.updateTotalEpisodes(totalEpisodes, System.currentTimeMillis(), trackId)
    }

    override suspend fun updateTrackDetails(trackId: Long, episodesWatched: Double, status: String, score: Double): Unit =
        withContext(dispatchers.io) {
            trackQueries.updateTrackDetails(episodesWatched, status, score, System.currentTimeMillis(), trackId)
        }
}
