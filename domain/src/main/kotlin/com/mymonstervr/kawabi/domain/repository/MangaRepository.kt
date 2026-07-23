package com.mymonstervr.kawabi.domain.repository

import com.mymonstervr.kawabi.domain.model.Manga
import com.mymonstervr.kawabi.domain.model.MangaWithUnreadCount
import kotlinx.coroutines.flow.Flow

interface MangaRepository {
    fun observeFavorites(): Flow<List<Manga>>
    fun observeFavoritesWithUnreadCount(): Flow<List<MangaWithUnreadCount>>
    suspend fun getFavorites(): List<Manga>
    suspend fun getById(id: Long): Manga?
    suspend fun getByUrl(url: String): Manga?
    suspend fun upsert(manga: Manga): Long
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun setInitialized(id: Long, initialized: Boolean)
    suspend fun setTotalChapters(id: Long, totalChapters: Double)
    suspend fun setViewer(id: Long, viewer: Int)
    suspend fun updateSiteKey(id: Long, siteKey: String?)
    suspend fun touchLastRead(id: Long, timestamp: Long)
    suspend fun getDueForUpdate(now: Long): List<Manga>
    suspend fun updateUpdateSchedule(id: Long, lastUpdate: Long, nextUpdate: Long, calculateInterval: Int)
}
