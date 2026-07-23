package com.mymonstervr.kawabi.data.track.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Trimmed to what title-search + chapter-read linking needs -- no synopsis,
// authors, score, or dates (out of scope per planning/TODO.md).

@Serializable
data class MalMangaCover(val large: String = "")

@Serializable
data class MalManga(
    val id: Long,
    val title: String,
    @SerialName("num_chapters") val numChapters: Long = 0,
    @SerialName("main_picture") val mainPicture: MalMangaCover? = null,
    @SerialName("media_type") val mediaType: String = "",
)

@Serializable
data class MalSearchNode(val node: MalManga)

@Serializable
data class MalSearchResult(val data: List<MalSearchNode> = emptyList())

@Serializable
data class MalListItemStatus(
    val status: String = "reading",
    @SerialName("num_chapters_read") val numChaptersRead: Double = 0.0,
    val score: Int = 0,
)

@Serializable
data class MalListItem(
    @SerialName("num_chapters") val numChapters: Long = 0,
    @SerialName("my_list_status") val myListStatus: MalListItemStatus? = null,
)
