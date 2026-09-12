package com.mymonstervr.kawabi.domain.repository

import com.mymonstervr.kawabi.domain.model.Anime
import com.mymonstervr.kawabi.domain.model.AnimeWithUnwatchedCount
import kotlinx.coroutines.flow.Flow

interface AnimeRepository {
    fun observeFavorites(): Flow<List<Anime>>
    fun observeFavoritesWithUnwatchedCount(): Flow<List<AnimeWithUnwatchedCount>>
    suspend fun getFavorites(): List<Anime>
    suspend fun getById(id: Long): Anime?
    suspend fun getByKey(key: String): Anime?
    suspend fun upsert(anime: Anime): Long
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun setTotalEpisodes(id: Long, totalEpisodes: Double)
    suspend fun touchLastWatched(id: Long, timestamp: Long)

    /** No-op when a cover is already stored -- repair path for rows imported without one. */
    suspend fun fillMissingThumbnail(id: Long, thumbnailUrl: String)
    suspend fun getDueForUpdate(now: Long): List<Anime>
    suspend fun updateUpdateSchedule(id: Long, lastUpdate: Long, nextUpdate: Long, calculateInterval: Int)
}
