package com.mymonstervr.kawabi.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.db.KawabiDatabase
import com.mymonstervr.kawabi.data.db.toDomain
import com.mymonstervr.kawabi.domain.model.Chapter
import com.mymonstervr.kawabi.domain.repository.ChapterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightChapterRepository(
    private val db: KawabiDatabase,
    private val dispatchers: AppDispatchers,
) : ChapterRepository {

    private val queries = db.chaptersQueries

    override fun observeForManga(mangaId: Long): Flow<List<Chapter>> =
        queries.selectByManga(mangaId).asFlow().mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getForManga(mangaId: Long): List<Chapter> = withContext(dispatchers.io) {
        queries.selectByManga(mangaId).executeAsList().map { it.toDomain() }
    }

    override suspend fun getById(id: Long): Chapter? = withContext(dispatchers.io) {
        queries.selectChapterById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getByMangaAndUrl(mangaId: Long, url: String): Chapter? = withContext(dispatchers.io) {
        queries.selectByMangaAndUrl(mangaId, url).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun upsert(chapter: Chapter): Long = withContext(dispatchers.io) {
        db.transactionWithResult {
            val existing = queries.selectByMangaAndUrl(chapter.mangaId, chapter.url).executeAsOneOrNull()
            existing?._id ?: insertRow(chapter)
        }
    }

    override suspend fun insert(chapter: Chapter): Long = withContext(dispatchers.io) {
        db.transactionWithResult { insertRow(chapter) }
    }

    private fun insertRow(chapter: Chapter): Long {
        queries.insertChapter(
            manga_id = chapter.mangaId,
            url = chapter.url,
            name = chapter.name,
            scanlator = chapter.scanlator,
            read = chapter.read,
            bookmark = chapter.bookmark,
            last_page_read = chapter.lastPageRead.toLong(),
            chapter_number = chapter.chapterNumber,
            source_order = chapter.sourceOrder.toLong(),
            date_upload = chapter.dateUpload,
            date_fetch = chapter.dateFetch,
            last_modified_at = chapter.lastModifiedAt,
            version = chapter.version,
            is_syncing = chapter.isSyncing,
        )
        return queries.lastInsertChapterRowId().executeAsOne()
    }

    override suspend fun updateDetails(
        id: Long,
        name: String,
        scanlator: String?,
        chapterNumber: Double,
        sourceOrder: Int,
        dateUpload: Long,
    ): Unit = withContext<Unit>(dispatchers.io) {
        queries.updateDetails(name, scanlator, chapterNumber, sourceOrder.toLong(), dateUpload, id)
    }

    override suspend fun setRead(id: Long, read: Boolean): Unit = withContext<Unit>(dispatchers.io) {
        queries.updateReadState(read, id)
    }

    override suspend fun setProgress(id: Long, read: Boolean, lastPageRead: Int): Unit = withContext<Unit>(dispatchers.io) {
        queries.updateProgress(read, lastPageRead.toLong(), id)
    }

    override suspend fun markReadUpToNumber(mangaId: Long, chapterNumber: Double): Unit = withContext<Unit>(dispatchers.io) {
        queries.markReadUpToNumber(mangaId, chapterNumber)
    }

    override suspend fun deleteForManga(mangaId: Long): Unit = withContext<Unit>(dispatchers.io) {
        queries.deleteChaptersByManga(mangaId)
    }

    override suspend fun deleteByIds(ids: List<Long>): Unit = withContext<Unit>(dispatchers.io) {
        if (ids.isNotEmpty()) queries.deleteChaptersByIds(ids)
    }
}
