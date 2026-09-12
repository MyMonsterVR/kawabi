package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.domain.model.Episode
import com.mymonstervr.kawabi.domain.repository.EpisodeRepository

private const val UNKNOWN_EPISODE_NUMBER = -1.0

/**
 * Carries watch state from one episode list onto another anime's episodes, matched on
 * episode number -- the only thing two sources agree on, since urls and keys are
 * per-source. Monotonic: an episode already watched on the target is never unmarked, and a
 * resume position is only applied to an episode that isn't watched yet.
 */
internal suspend fun carryWatchedByNumber(
    episodeRepository: EpisodeRepository,
    from: List<Episode>,
    toAnimeId: Long,
) {
    val sourceByNumber = from
        .filter { it.episodeNumber != UNKNOWN_EPISODE_NUMBER && (it.watched || it.positionMs > 0) }
        .groupBy { it.episodeNumber }
    if (sourceByNumber.isEmpty()) return
    for (target in episodeRepository.getForAnime(toAnimeId)) {
        val matches = sourceByNumber[target.episodeNumber] ?: continue
        val watched = target.watched || matches.any { it.watched }
        val positionMs = if (watched) target.positionMs else maxOf(target.positionMs, matches.maxOf { it.positionMs })
        val durationMs = maxOf(target.durationMs, matches.maxOf { it.durationMs })
        if (watched != target.watched || positionMs != target.positionMs || durationMs != target.durationMs) {
            episodeRepository.setProgress(target.id, watched, positionMs, durationMs)
        }
    }
}
