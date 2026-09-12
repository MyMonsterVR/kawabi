package com.mymonstervr.kawabi.domain.model

data class AnimeTrack(
    val id: Long,
    val animeId: Long,
    val trackerId: String,
    val remoteId: String,
    val libraryId: String?,
    val title: String,
    val trackingUrl: String,
    val totalEpisodes: Double,
    val lastEpisodeWatched: Double,
    val score: Double,
    // Canonical internal values, anime variants of TrackStatus: "watching" | "completed" |
    // "on_hold" | "dropped" | "plan_to_watch".
    val status: String,
    /** Wire clock for cross-device track sync (`/anime/entries` `tracks[].updated_at`). */
    val updatedAt: Long = 0,
)
