package com.mymonstervr.kawabi.domain.model

/** Canonical status values, tracker-agnostic -- mapped to MAL/Kitsu's own strings at the API boundary. */
object TrackStatus {
    const val READING = "reading"
    const val COMPLETED = "completed"
    const val ON_HOLD = "on_hold"
    const val DROPPED = "dropped"
    const val PLAN_TO_READ = "plan_to_read"

    // Anime axis: the backend contract's canonical anime statuses (MAL names; Kitsu maps
    // current/planned). Only "reading"/"plan_to_read" differ between the two axes.
    const val WATCHING = "watching"
    const val PLAN_TO_WATCH = "plan_to_watch"

    val ALL = listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ)
    val ALL_ANIME = listOf(WATCHING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_WATCH)

    fun allFor(mediaType: MediaType): List<String> = if (mediaType == MediaType.ANIME) ALL_ANIME else ALL
}
