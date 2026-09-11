package com.mymonstervr.kawabi.domain.model

/**
 * Parallel to [Manga] (PLAN-anime.md decision D8 -- separate tables/models rather than a
 * `media_type` discriminator on the manga stack). Identity is [key]
 * (`"<engineSourceId>:<relativeUrl>"`, from the backend contract), not [url] alone:
 * the same relative url can exist on two engine sources.
 */
data class Anime(
    val id: Long,
    val source: String,
    val key: String,
    val url: String,
    val title: String,
    val author: String?,
    val description: String?,
    val genres: List<String>,
    val status: String,
    val thumbnailUrl: String?,
    val favorite: Boolean,
    val lastUpdate: Long?,
    val nextUpdate: Long?,
    val initialized: Boolean,
    val dateAdded: Long,
    val calculateInterval: Int,
    val lastModifiedAt: Long,
    val version: Long,
    val isSyncing: Boolean,
    val totalEpisodes: Double,
    val lastWatchedAt: Long,
)
