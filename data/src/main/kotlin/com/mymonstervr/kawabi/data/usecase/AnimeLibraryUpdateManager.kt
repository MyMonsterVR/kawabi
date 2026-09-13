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
 * cost-control reason -- every refresh used to be a live engine fetch through WARP.
 *
 * Refreshes every due anime in one [RefreshAnimeBatch] call against `/anime/cached` (the
 * backend's background-refreshed persisted cache), not one live call per anime -- same
 * change as [LibraryUpdateManager]'s move from per-manga `/manga` to batched `/manga/cached`.
 */
class AnimeLibraryUpdateManager(
    private val animeRepository: AnimeRepository,
    private val refreshAnimeBatch: RefreshAnimeBatch,
) {
    suspend fun updateDue(now: Long = System.currentTimeMillis()): Int {
        val due = animeRepository.getDueForUpdate(now)
        val results = refreshAnimeBatch.refresh(due)
        for (anime in due) {
            val foundNew = results[anime.id]?.getOrNull()?.isNotEmpty() == true
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
