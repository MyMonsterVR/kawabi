package com.mymonstervr.kawabi.tv.common

import com.mymonstervr.kawabi.domain.model.Episode

/** Same rule as the phone app's AnimeDetailViewModel.kt (private there, :tv can't import :app). */
fun resumeEpisode(episodes: Collection<Episode>): Episode? {
    val numbered = episodes.filter { it.episodeNumber >= 0 }
    return numbered.filter { !it.watched && it.positionMs > 0 }.minByOrNull { it.episodeNumber }
        ?: numbered.filter { !it.watched }.minByOrNull { it.episodeNumber }
}
