package com.mymonstervr.kawabi.domain.model

/** Canonical status values, tracker-agnostic -- mapped to MAL/Kitsu's own strings at the API boundary. */
object TrackStatus {
    const val READING = "reading"
    const val COMPLETED = "completed"
    const val ON_HOLD = "on_hold"
    const val DROPPED = "dropped"
    const val PLAN_TO_READ = "plan_to_read"

    val ALL = listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ)
}
