package com.mymonstervr.kawabi.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class MangaResponse(
    val source: String,
    val served_from: String? = null,
    // Non-null only when this response was served via an explicit per-manga source pin
    // (empty/absent for the auto-pick chain) -- lets the UI show "Comick" instead of
    // "Comick (auto)" without a separate GET /manga/sources round trip.
    val preferred_source: String? = null,
    val url: String,
    val title: String,
    val cover_url: String? = null,
    val description: String? = null,
    val author: String? = null,
    val status: String = "",
    val genres: List<String> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
data class ChapterDto(
    val id: String,
    val number: Double,
    val title: String,
    val date_upload: Long? = null,
)
