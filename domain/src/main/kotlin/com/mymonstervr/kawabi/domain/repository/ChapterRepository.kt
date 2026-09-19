package com.mymonstervr.kawabi.domain.repository

import com.mymonstervr.kawabi.domain.model.Chapter
import com.mymonstervr.kawabi.domain.model.ChapterUpdate
import kotlinx.coroutines.flow.Flow

interface ChapterRepository {
    fun observeForManga(mangaId: Long): Flow<List<Chapter>>
    suspend fun getForManga(mangaId: Long): List<Chapter>
    suspend fun getById(id: Long): Chapter?
    suspend fun getByMangaAndUrl(mangaId: Long, url: String): Chapter?
    suspend fun upsert(chapter: Chapter): Long
    suspend fun insert(chapter: Chapter): Long
    suspend fun updateDetails(id: Long, name: String, scanlator: String?, chapterNumber: Double, sourceOrder: Int, dateUpload: Long)
    /**
     * Applies a whole sync pass (new chapters, changed-field updates, removed chapters) in one
     * DB transaction instead of a separate transaction per row -- [SyncChaptersWithSource] used
     * to call [insert]/[updateDetails]/[deleteByIds] in loops, which for a manga with hundreds
     * of chapters (a first sync, or a big backlog) meant hundreds of sequential transactions and
     * was the real client-side cost of a library refresh, not the network call. Returns
     * [inserts]' assigned ids, same order.
     */
    suspend fun applySync(inserts: List<Chapter>, updates: List<ChapterUpdate>, deleteIds: List<Long>): List<Long>
    suspend fun setRead(id: Long, read: Boolean)
    suspend fun setProgress(id: Long, read: Boolean, lastPageRead: Int)
    suspend fun markReadUpToNumber(mangaId: Long, chapterNumber: Double)
    suspend fun deleteForManga(mangaId: Long)
    suspend fun deleteByIds(ids: List<Long>)
}
