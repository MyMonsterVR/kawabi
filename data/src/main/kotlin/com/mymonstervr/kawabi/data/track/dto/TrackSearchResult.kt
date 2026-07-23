package com.mymonstervr.kawabi.data.track.dto

/** A title-search hit on a tracker, shown in the per-manga tracker-linking sheet. */
data class TrackSearchResult(
    val remoteId: String,
    val title: String,
    val totalChapters: Double,
    val coverUrl: String?,
)
