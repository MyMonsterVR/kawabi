package com.mymonstervr.kawabi.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class MangaSourcesResponse(
    val sources: List<MangaSourceOptionDto> = emptyList(),
    val selected: String? = null,
)

@Serializable
data class MangaSourceOptionDto(
    val key: String,
    val name: String,
    // "available" | "unavailable" | "unknown"
    val status: String,
    val mapped_url: String? = null,
    val selected: Boolean = false,
)

@Serializable
data class SetMangaSourceRequest(
    val url: String,
    val site_key: String,
    val title: String? = null,
)
