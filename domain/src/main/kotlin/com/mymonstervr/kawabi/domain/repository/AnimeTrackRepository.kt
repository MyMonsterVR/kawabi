package com.mymonstervr.kawabi.domain.repository

import com.mymonstervr.kawabi.domain.model.AnimeTrack

interface AnimeTrackRepository {
    suspend fun getForAnime(animeId: Long): List<AnimeTrack>
    suspend fun getByAnimeAndTracker(animeId: Long, trackerId: String): AnimeTrack?
    suspend fun link(track: AnimeTrack): Long
    suspend fun unlink(animeId: Long, trackerId: String)

    /** Monotonic-max, same rule as the manga side's updateChaptersRead. */
    suspend fun updateEpisodesWatched(trackId: Long, episodesWatched: Double)
    suspend fun updateTotalEpisodes(trackId: Long, totalEpisodes: Double)

    /** Explicit user edit (manual episode/status/score correction) -- not monotonic-max. */
    suspend fun updateTrackDetails(trackId: Long, episodesWatched: Double, status: String, score: Double)
}
