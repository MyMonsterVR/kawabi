package com.mymonstervr.kawabi.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.db.KawabiDatabase
import com.mymonstervr.kawabi.data.db.toDomain
import com.mymonstervr.kawabi.domain.model.Anime
import com.mymonstervr.kawabi.domain.model.AnimeWithUnwatchedCount
import com.mymonstervr.kawabi.domain.repository.AnimeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightAnimeRepository(
    private val db: KawabiDatabase,
    private val dispatchers: AppDispatchers,
) : AnimeRepository {

    private val queries = db.animesQueries

    override fun observeFavorites(): Flow<List<Anime>> =
        queries.selectFavorites().asFlow().mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeFavoritesWithUnwatchedCount(): Flow<List<AnimeWithUnwatchedCount>> =
        queries.selectFavoritesWithUnwatchedCount().asFlow().mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getFavorites(): List<Anime> = withContext(dispatchers.io) {
        queries.selectFavorites().executeAsList().map { it.toDomain() }
    }

    override suspend fun getById(id: Long): Anime? = withContext(dispatchers.io) {
        queries.selectAnimeById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getByKey(key: String): Anime? = withContext(dispatchers.io) {
        queries.selectByKey(key).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun upsert(anime: Anime): Long = withContext(dispatchers.io) {
        db.transactionWithResult {
            val existing = queries.selectByKey(anime.key).executeAsOneOrNull()
            if (existing != null) {
                queries.updateMetadata(
                    source = anime.source,
                    url = anime.url,
                    title = anime.title,
                    author = anime.author,
                    description = anime.description,
                    genre = anime.genres,
                    status = anime.status,
                    thumbnail_url = anime.thumbnailUrl,
                    _id = existing._id,
                )
                queries.setAnimeSyncing(anime.isSyncing, existing._id)
                existing._id
            } else {
                queries.insertAnime(
                    source = anime.source,
                    key = anime.key,
                    url = anime.url,
                    title = anime.title,
                    author = anime.author,
                    description = anime.description,
                    genre = anime.genres,
                    status = anime.status,
                    thumbnail_url = anime.thumbnailUrl,
                    favorite = anime.favorite,
                    last_update = anime.lastUpdate,
                    next_update = anime.nextUpdate,
                    initialized = anime.initialized,
                    date_added = anime.dateAdded,
                    calculate_interval = anime.calculateInterval.toLong(),
                    last_modified_at = anime.lastModifiedAt,
                    version = anime.version,
                    is_syncing = anime.isSyncing,
                    total_episodes = anime.totalEpisodes,
                    last_watched_at = anime.lastWatchedAt,
                )
                queries.lastInsertAnimeRowId().executeAsOne()
            }
        }
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean): Unit = withContext<Unit>(dispatchers.io) {
        queries.updateFavorite(favorite, id)
    }

    override suspend fun setTotalEpisodes(id: Long, totalEpisodes: Double): Unit = withContext<Unit>(dispatchers.io) {
        queries.updateTotalEpisodes(totalEpisodes, id)
    }

    override suspend fun touchLastWatched(id: Long, timestamp: Long): Unit = withContext<Unit>(dispatchers.io) {
        queries.touchLastWatched(timestamp, id)
    }

    override suspend fun getDueForUpdate(now: Long): List<Anime> = withContext(dispatchers.io) {
        queries.selectDueForUpdate(now).executeAsList().map { it.toDomain() }
    }

    override suspend fun updateUpdateSchedule(id: Long, lastUpdate: Long, nextUpdate: Long, calculateInterval: Int): Unit =
        withContext<Unit>(dispatchers.io) {
            queries.updateUpdateSchedule(lastUpdate, nextUpdate, calculateInterval.toLong(), id)
        }
}
