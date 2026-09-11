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
    // PLAN-anime.md section 10 spells the anime search result's episode count
    // `total_episodes`, while the manga wire this endpoint already speaks is camelCase.
    // Both spellings are accepted so whichever the backend actually emits is read, rather
    // than silently showing 0 episodes -- see TrackerApi.progressOf for the same reasoning
    // on entry progress.
    val totalEpisodes: Double = 0.0,
    val total_episodes: Double = 0.0,
)

@Serializable
data class TrackerEntryDto(
    val remoteId: String,
    val status: String,
    val chaptersRead: Double = 0.0,
    val totalChapters: Double = 0.0,
    val episodesWatched: Double = 0.0,
    val episodes_watched: Double = 0.0,
    val totalEpisodes: Double = 0.0,
    val total_episodes: Double = 0.0,
    val score: Double = 0.0,
)

@Serializable
data class TrackerUpsertEntryRequest(
    val remoteId: String,
    val status: String? = null,
    val chaptersRead: Double? = null,
    val score: Double? = null,
    // Media axis. Null for manga so the manga request body stays byte-identical to what
    // it has always been (networkJson doesn't encode defaults, so a null field is omitted
    // entirely, not sent as `null`); "anime" on the anime axis.
    val type: String? = null,
    // Anime progress under the contract's own field name, sent alongside chaptersRead
    // for the same accept-both reason as TrackerEntryDto above.
    val episodesWatched: Double? = null,
    val episodes_watched: Double? = null,
)
