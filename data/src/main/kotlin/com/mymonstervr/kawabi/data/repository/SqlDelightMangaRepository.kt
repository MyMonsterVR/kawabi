package com.mymonstervr.kawabi.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.db.KawabiDatabase
import com.mymonstervr.kawabi.data.db.toDomain
import com.mymonstervr.kawabi.domain.model.Manga
import com.mymonstervr.kawabi.domain.model.MangaWithUnreadCount
import com.mymonstervr.kawabi.domain.repository.MangaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightMangaRepository(
    private val db: KawabiDatabase,
    private val dispatchers: AppDispatchers,
) : MangaRepository {

    private val queries = db.mangasQueries

    override fun observeFavorites(): Flow<List<Manga>> =
        queries.selectFavorites().asFlow().mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeFavoritesWithUnreadCount(): Flow<List<MangaWithUnreadCount>> =
        queries.selectFavoritesWithUnreadCount().asFlow().mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getFavorites(): List<Manga> = withContext(dispatchers.io) {
        queries.selectFavorites().executeAsList().map { it.toDomain() }
    }

    override suspend fun getById(id: Long): Manga? = withContext(dispatchers.io) {
        queries.selectMangaById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getByUrl(url: String): Manga? = withContext(dispatchers.io) {
        queries.selectByUrl(url).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun upsert(manga: Manga): Long = withContext(dispatchers.io) {
        db.transactionWithResult {
            val existing = queries.selectByUrl(manga.url).executeAsOneOrNull()
            if (existing != null) {
                queries.updateMetadata(
                    source = manga.source,
                    site_key = manga.siteKey,
                    title = manga.title,
                    artist = manga.artist,
                    author = manga.author,
                    description = manga.description,
                    genre = manga.genres,
                    status = manga.status,
                    thumbnail_url = manga.thumbnailUrl,
                    _id = existing._id,
                )
                queries.setMangaSyncing(manga.isSyncing, existing._id)
                existing._id
            } else {
                queries.insertManga(
                    source = manga.source,
                    site_key = manga.siteKey,
                    url = manga.url,
                    title = manga.title,
                    artist = manga.artist,
                    author = manga.author,
                    description = manga.description,
                    genre = manga.genres,
                    status = manga.status,
                    thumbnail_url = manga.thumbnailUrl,
                    favorite = manga.favorite,
                    last_update = manga.lastUpdate,
                    next_update = manga.nextUpdate,
                    initialized = manga.initialized,
                    chapter_flags = manga.chapterFlags.toLong(),
                    viewer = manga.viewer.toLong(),
                    date_added = manga.dateAdded,
                    calculate_interval = manga.calculateInterval.toLong(),
                    last_modified_at = manga.lastModifiedAt,
                    version = manga.version,
                    is_syncing = manga.isSyncing,
                    total_chapters = manga.totalChapters,
                    notes = manga.notes,
                    last_read_at = manga.lastReadAt,
                )
                queries.lastInsertMangaRowId().executeAsOne()
            }
        }
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean): Unit = withContext<Unit>(dispatchers.io) {
        queries.updateFavorite(favorite, id)
    }

    override suspend fun setInitialized(id: Long, initialized: Boolean): Unit = withContext<Unit>(dispatchers.io) {
        queries.updateInitialized(initialized, id)
    }

    override suspend fun setTotalChapters(id: Long, totalChapters: Double): Unit = withContext<Unit>(dispatchers.io) {
        queries.updateTotalChapters(totalChapters, id)
    }

    override suspend fun setViewer(id: Long, viewer: Int): Unit = withContext<Unit>(dispatchers.io) {
        queries.updateViewer(viewer.toLong(), id)
    }

    override suspend fun updateSiteKey(id: Long, siteKey: String?): Unit = withContext<Unit>(dispatchers.io) {
        queries.updateSiteKey(siteKey, id)
    }

    override suspend fun touchLastRead(id: Long, timestamp: Long): Unit = withContext<Unit>(dispatchers.io) {
        queries.touchLastRead(timestamp, id)
    }

    override suspend fun getDueForUpdate(now: Long): List<Manga> = withContext(dispatchers.io) {
        queries.selectDueForUpdate(now).executeAsList().map { it.toDomain() }
    }

    override suspend fun updateUpdateSchedule(id: Long, lastUpdate: Long, nextUpdate: Long, calculateInterval: Int): Unit =
        withContext<Unit>(dispatchers.io) {
            queries.updateUpdateSchedule(lastUpdate, nextUpdate, calculateInterval.toLong(), id)
        }
}
