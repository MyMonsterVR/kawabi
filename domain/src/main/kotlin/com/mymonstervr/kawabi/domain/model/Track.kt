package com.mymonstervr.kawabi.domain.model

data class Track(
    val id: Long,
    val mangaId: Long,
    val trackerId: String,
    val remoteId: String,
    val libraryId: String?,
    val title: String,
    val trackingUrl: String,
    val totalChapters: Double,
    val lastChapterRead: Double,
    // 0-10 scale regardless of tracker -- converted at the API boundary (MAL is
    // already 0-10 int; Kitsu's ratingTwenty is this * 2).
    val score: Double,
    // Canonical internal values: "reading" | "completed" | "on_hold" | "dropped" |
    // "plan_to_read" -- mapped to each tracker's own status strings at the API boundary.
    val status: String,
)
