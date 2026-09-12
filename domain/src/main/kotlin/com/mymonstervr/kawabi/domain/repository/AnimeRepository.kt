package com.mymonstervr.kawabi.domain.repository

import com.mymonstervr.kawabi.domain.model.Anime
import com.mymonstervr.kawabi.domain.model.AnimeLibraryEntry
import kotlinx.coroutines.flow.Flow

interface AnimeRepository {
    fun observeFavorites(): Flow<List<Anime>>
    fun observeLibraryEntries(): Flow<List<AnimeLibraryEntry>>
    suspend fun getFavorites(): List<Anime>
    suspend fun getById(id: Long): Anime?
    suspend fun getByKey(key: String): Anime?
    suspend fun upsert(anime: Anime): Long
    suspend fun setFavorite(id: Long, favorite: Boolean)

    /**
     * Moves an existing library row onto the same show on another source, keeping its id
     * (and therefore its episodes, tracker links and history). Episodes still have to be
     * re-listed from the new key afterwards -- see `SwitchAnimeSource`, which owns the
     * whole operation; this is only the row rewrite.
     */
    suspend fun switchSource(
        animeId: Long,
        newKey: String,
        newSource: String,
        newUrl: String,
        newTitle: String? = null,
        cover: String? = null,
    )

    suspend fun delete(id: Long)
    suspend fun setTotalEpisodes(id: Long, totalEpisodes: Double)
    suspend fun touchLastWatched(id: Long, timestamp: Long)

    /** No-op when a cover is already stored -- repair path for rows imported without one. */
    suspend fun fillMissingThumbnail(id: Long, thumbnailUrl: String)
    suspend fun getDueForUpdate(now: Long): List<Anime>
    suspend fun updateUpdateSchedule(id: Long, lastUpdate: Long, nextUpdate: Long, calculateInterval: Int)
}
