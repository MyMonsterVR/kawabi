package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.domain.model.Anime
import com.mymonstervr.kawabi.domain.repository.AnimeRepository

private const val DEFAULT_INTERVAL_DAYS = 3
private const val MIN_INTERVAL_DAYS = 1
private const val MAX_INTERVAL_DAYS = 14
private const val DAY_MS = 24 * 60 * 60 * 1000L

/**
 * Anime counterpart of [LibraryUpdateManager], same smart-interval skip logic (finding new
 * episodes halves the interval, finding none grows it up to a cap) for the same
 * cost-control reason -- every refresh is an engine fetch through WARP.
 *
 * Unlike the manga side there is no batch endpoint under `/anime`, so this refreshes one
 * anime per call; the interval logic is what keeps that bounded.
 */
class AnimeLibraryUpdateManager(
    private val animeRepository: AnimeRepository,
    private val refreshAnimeEpisodes: RefreshAnimeEpisodes,
) {
    suspend fun updateDue(now: Long = System.currentTimeMillis()): Int {
        val due = animeRepository.getDueForUpdate(now)
        for (anime in due) {
            val foundNew = refreshAnimeEpisodes.refresh(anime).getOrNull()?.isNotEmpty() == true
            updateSchedule(anime, now, foundNew)
        }
        return due.size
    }

    private suspend fun updateSchedule(anime: Anime, now: Long, foundNew: Boolean) {
        val currentInterval = anime.calculateInterval.takeIf { it > 0 } ?: DEFAULT_INTERVAL_DAYS
        val nextInterval = if (foundNew) {
            (currentInterval / 2).coerceAtLeast(MIN_INTERVAL_DAYS)
        } else {
            (currentInterval + 1).coerceAtMost(MAX_INTERVAL_DAYS)
        }
        animeRepository.updateUpdateSchedule(
            id = anime.id,
            lastUpdate = now,
            nextUpdate = now + nextInterval * DAY_MS,
            calculateInterval = nextInterval,
        )
    }
}
