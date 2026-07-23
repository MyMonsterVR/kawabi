package com.mymonstervr.kawabi.domain.repository

import com.mymonstervr.kawabi.domain.model.Chapter
import kotlinx.coroutines.flow.Flow

interface ChapterRepository {
    fun observeForManga(mangaId: Long): Flow<List<Chapter>>
    suspend fun getForManga(mangaId: Long): List<Chapter>
    suspend fun getById(id: Long): Chapter?
    suspend fun getByMangaAndUrl(mangaId: Long, url: String): Chapter?
    suspend fun upsert(chapter: Chapter): Long
    suspend fun insert(chapter: Chapter): Long
    suspend fun updateDetails(id: Long, name: String, scanlator: String?, chapterNumber: Double, sourceOrder: Int, dateUpload: Long)
    suspend fun setRead(id: Long, read: Boolean)
    suspend fun setProgress(id: Long, read: Boolean, lastPageRead: Int)
    suspend fun markReadUpToNumber(mangaId: Long, chapterNumber: Double)
    suspend fun deleteForManga(mangaId: Long)
    suspend fun deleteByIds(ids: List<Long>)
}
