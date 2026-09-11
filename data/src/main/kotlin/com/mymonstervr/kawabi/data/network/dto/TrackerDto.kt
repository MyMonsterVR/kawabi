package com.mymonstervr.kawabi.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class TrackerConnectMalRequest(val code: String, val codeVerifier: String)

@Serializable
data class TrackerConnectKitsuRequest(val email: String, val password: String)

@Serializable
data class TrackerConnectResponse(val userName: String)

@Serializable
data class TrackerStatusDto(val tracker: String, val userName: String)

@Serializable
data class TrackerSearchResultDto(
    val remoteId: String,
    val title: String,
    val coverUrl: String? = null,
    val totalEpisodes: Double = 0.0,
)

@Serializable
data class AnimeTrackerSearchResultDto(
    val remote_id: String,
    val title: String,
    val cover_url: String? = null,
    val total_episodes: Double = 0.0,
)

@Serializable
data class TrackerEntryDto(
    val remoteId: String,
    val status: String,
    val chaptersRead: Double = 0.0,
    val totalChapters: Double = 0.0,
    val score: Double = 0.0,
)

@Serializable
data class AnimeTrackerEntryDto(
    val remote_id: String,
    val status: String,
    val episodes_watched: Double = 0.0,
    val total_episodes: Double = 0.0,
    val score: Double = 0.0,
)

@Serializable
data class TrackerUpsertEntryRequest(
    val remoteId: String,
    val status: String? = null,
    val chaptersRead: Double? = null,
    val score: Double? = null,
)

@Serializable
data class AnimeTrackerUpsertEntryRequest(
    val remote_id: String,
    val type: String,
    val status: String? = null,
    val episodes_watched: Double? = null,
    val score: Double? = null,
)
