package com.mymonstervr.kawabi.domain.model

/** One unwatched, recently published episode plus the anime it belongs to. */
data class NewEpisode(
    val episodeId: Long,
    val episodeKey: String,
    val name: String,
    val episodeNumber: Double,
    val dateUpload: Long,
    val animeId: Long,
    val animeTitle: String,
    val animeThumbnailUrl: String?,
    val animeSource: String,
)
