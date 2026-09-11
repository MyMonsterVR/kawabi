package com.mymonstervr.kawabi.domain.model

data class Episode(
    val id: Long,
    val animeId: Long,
    val key: String,
    val url: String,
    val name: String,
    val watched: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val episodeNumber: Double,
    val sourceOrder: Int,
    val dateUpload: Long,
    val dateFetch: Long,
    val lastModifiedAt: Long,
    val version: Long,
    val isSyncing: Boolean,
)
