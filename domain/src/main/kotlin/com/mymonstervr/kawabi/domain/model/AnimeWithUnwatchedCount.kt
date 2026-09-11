package com.mymonstervr.kawabi.domain.model

data class AnimeWithUnwatchedCount(
    val anime: Anime,
    val unwatchedCount: Int,
    // Null when nothing's been watched yet. Queried directly (MAX episode_number where
    // watched=1) for the same reason MangaWithUnreadCount does it -- deriving it from
    // totalEpisodes minus unwatchedCount breaks on any non-contiguous episode numbering.
    val lastWatchedEpisodeNumber: Double?,
)
