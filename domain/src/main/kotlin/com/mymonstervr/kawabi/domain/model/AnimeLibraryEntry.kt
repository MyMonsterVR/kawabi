package com.mymonstervr.kawabi.domain.model

/**
 * Where an anime sits in the user's watch flow. Derived, never stored: a tracker link is
 * authoritative when one exists (it is what the user maintains on MAL/Kitsu/AniList),
 * otherwise it falls out of the local episode tallies.
 */
enum class AnimeWatchStatus {
    WATCHING,
    COMPLETED,
    PLAN_TO_WATCH,
    ON_HOLD,
    DROPPED,
}

data class AnimeLibraryEntry(
    val anime: Anime,
    val status: AnimeWatchStatus,
    val episodeCount: Int,
    val watchedCount: Int,
    /** Lowest-numbered unwatched episode, null once every numbered episode is watched. */
    val nextUnwatchedNumber: Double?,
    val lastWatchedEpisodeNumber: Double?,
    val latestEpisodeAt: Long,
    val trackStatus: String?,
) {
    val unwatchedCount: Int get() = (episodeCount - watchedCount).coerceAtLeast(0)
    val watchedFraction: Float get() = if (episodeCount > 0) watchedCount.toFloat() / episodeCount else 0f
}

fun animeWatchStatusOf(trackStatus: String?, episodeCount: Int, watchedCount: Int): AnimeWatchStatus {
    val mapped = when (trackStatus) {
        TrackStatus.WATCHING, TrackStatus.READING -> AnimeWatchStatus.WATCHING
        TrackStatus.COMPLETED -> AnimeWatchStatus.COMPLETED
        TrackStatus.PLAN_TO_WATCH, TrackStatus.PLAN_TO_READ -> AnimeWatchStatus.PLAN_TO_WATCH
        TrackStatus.ON_HOLD -> AnimeWatchStatus.ON_HOLD
        TrackStatus.DROPPED -> AnimeWatchStatus.DROPPED
        else -> null
    }
    if (mapped != null) return mapped
    return if (episodeCount > 0 && watchedCount >= episodeCount) {
        AnimeWatchStatus.COMPLETED
    } else {
        AnimeWatchStatus.WATCHING
    }
}
