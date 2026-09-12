package com.mymonstervr.kawabi.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.db.KawabiDatabase
import com.mymonstervr.kawabi.data.db.toDomain
import com.mymonstervr.kawabi.domain.model.Episode
import com.mymonstervr.kawabi.domain.model.NewEpisode
import com.mymonstervr.kawabi.domain.repository.EpisodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightEpisodeRepository(
    private val db: KawabiDatabase,
    private val dispatchers: AppDispatchers,
) : EpisodeRepository {

    private val queries = db.episodesQueries

    override fun observeForAnime(animeId: Long): Flow<List<Episode>> =
        queries.selectByAnime(animeId).asFlow().mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeRecentUnwatched(since: Long, limit: Long): Flow<List<NewEpisode>> =
        queries.selectRecentUnwatched(since, limit).asFlow().mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getForAnime(animeId: Long): List<Episode> = withContext(dispatchers.io) {
        queries.selectByAnime(animeId).executeAsList().map { it.toDomain() }
    }

    override suspend fun getById(id: Long): Episode? = withContext(dispatchers.io) {
        queries.selectEpisodeById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getByKey(key: String): Episode? = withContext(dispatchers.io) {
        queries.selectByKey(key).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getByAnimeAndUrl(animeId: Long, url: String): Episode? = withContext(dispatchers.io) {
        queries.selectByAnimeAndUrl(animeId, url).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun upsert(episode: Episode): Long = withContext(dispatchers.io) {
        db.transactionWithResult {
            val existing = queries.selectByAnimeAndUrl(episode.animeId, episode.url).executeAsOneOrNull()
            existing?._id ?: insertRow(episode)
        }
    }

    override suspend fun insert(episode: Episode): Long = withContext(dispatchers.io) {
        db.transactionWithResult { insertRow(episode) }
    }

    private fun insertRow(episode: Episode): Long {
        queries.insertEpisode(
            anime_id = episode.animeId,
            key = episode.key,
            url = episode.url,
            name = episode.name,
            watched = episode.watched,
            position_ms = episode.positionMs,
            duration_ms = episode.durationMs,
            episode_number = episode.episodeNumber,
            source_order = episode.sourceOrder.toLong(),
            date_upload = episode.dateUpload,
            date_fetch = episode.dateFetch,
            last_modified_at = episode.lastModifiedAt,
            version = episode.version,
            is_syncing = episode.isSyncing,
        )
        return queries.lastInsertEpisodeRowId().executeAsOne()
    }

    override suspend fun updateDetails(
        id: Long,
        key: String,
        name: String,
        episodeNumber: Double,
        sourceOrder: Int,
        dateUpload: Long,
    ): Unit = withContext<Unit>(dispatchers.io) {
        queries.updateDetails(key, name, episodeNumber, sourceOrder.toLong(), dateUpload, id)
    }

    override suspend fun setWatched(id: Long, watched: Boolean): Unit = withContext<Unit>(dispatchers.io) {
        queries.updateWatchedState(watched, id)
    }

    override suspend fun setProgress(id: Long, watched: Boolean, positionMs: Long, durationMs: Long): Unit =
        withContext<Unit>(dispatchers.io) {
            queries.updateProgress(watched, positionMs, durationMs, id)
        }

    override suspend fun markWatchedUpToNumber(animeId: Long, episodeNumber: Double): Unit = withContext<Unit>(dispatchers.io) {
        queries.markWatchedUpToNumber(animeId, episodeNumber)
    }

    override suspend fun deleteForAnime(animeId: Long): Unit = withContext<Unit>(dispatchers.io) {
        queries.deleteEpisodesByAnime(animeId)
    }

    override suspend fun deleteByIds(ids: List<Long>): Unit = withContext<Unit>(dispatchers.io) {
        if (ids.isNotEmpty()) queries.deleteEpisodesByIds(ids)
    }
}
