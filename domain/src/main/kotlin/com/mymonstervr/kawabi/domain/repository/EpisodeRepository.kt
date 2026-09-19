package com.mymonstervr.kawabi.domain.repository

import com.mymonstervr.kawabi.domain.model.Episode
import com.mymonstervr.kawabi.domain.model.EpisodeUpdate
import com.mymonstervr.kawabi.domain.model.NewEpisode
import kotlinx.coroutines.flow.Flow

interface EpisodeRepository {
    fun observeForAnime(animeId: Long): Flow<List<Episode>>

    /** Unwatched episodes published at or after [since], newest first, across the library. */
    fun observeRecentUnwatched(since: Long, limit: Long): Flow<List<NewEpisode>>
    suspend fun getForAnime(animeId: Long): List<Episode>
    suspend fun getById(id: Long): Episode?
    suspend fun getByKey(key: String): Episode?
    suspend fun getByAnimeAndUrl(animeId: Long, url: String): Episode?
    suspend fun upsert(episode: Episode): Long
    suspend fun insert(episode: Episode): Long
    suspend fun updateDetails(id: Long, key: String, name: String, episodeNumber: Double, sourceOrder: Int, dateUpload: Long)
    /**
     * Applies a whole sync pass (new episodes, changed-field updates, removed episodes) in one
     * DB transaction instead of a separate transaction per row -- see
     * [ChapterRepository.applySync]'s doc comment, same reasoning, anime side. Returns
     * [inserts]' assigned ids, same order.
     */
    suspend fun applySync(inserts: List<Episode>, updates: List<EpisodeUpdate>, deleteIds: List<Long>): List<Long>
    suspend fun setWatched(id: Long, watched: Boolean)
    suspend fun setProgress(id: Long, watched: Boolean, positionMs: Long, durationMs: Long)
    suspend fun markWatchedUpToNumber(animeId: Long, episodeNumber: Double)
    suspend fun deleteForAnime(animeId: Long)
    suspend fun deleteByIds(ids: List<Long>)
}
