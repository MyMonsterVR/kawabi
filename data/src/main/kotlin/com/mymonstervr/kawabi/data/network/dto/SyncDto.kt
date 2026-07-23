package com.mymonstervr.kawabi.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class EntryDto(
    val tracker_id: Long = 0,
    val remote_id: Long,
    val title: String,
    val chapters_read: Double,
    val source_url: String? = null,
    val last_synced_chapters: Double = 0.0,
    val cover_url: String? = null,
    val updated_at: Long = 0,
    val deleted_at: Long? = null,
    val last_read_at: Long? = null,
    val manga_key: String? = null,
    val asura_url: String? = null,
    val total_chapters: Double = 0.0,
    val preferred_source: String? = null,
    val preferred_url: String? = null,
)

@Serializable
data class EntriesResponse(
    val server_time_ms: Long = 0,
    val entries: List<EntryDto> = emptyList(),
)

@Serializable
data class EntriesRequest(val entries: List<EntryDto>)

@Serializable
data class ProgressDto(
    val source_url: String,
    val chapter_id: String,
    val chapter_number: Double,
    val last_page_read: Int,
    val read: Boolean,
    val updated_at: Long = 0,
)

@Serializable
data class ProgressResponse(val entries: List<ProgressDto> = emptyList())

@Serializable
data class ProgressRequest(val entries: List<ProgressDto>)

@Serializable
data class LibraryAddRequest(val url: String, val title: String, val cover_url: String? = null)

@Serializable
data class LibraryAddResponse(val created: Boolean)
