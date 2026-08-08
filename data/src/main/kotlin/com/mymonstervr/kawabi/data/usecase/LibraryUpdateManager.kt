package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.domain.model.Manga
import com.mymonstervr.kawabi.domain.repository.MangaRepository

private const val DEFAULT_INTERVAL_DAYS = 3
private const val MIN_INTERVAL_DAYS = 1
private const val MAX_INTERVAL_DAYS = 14
private const val DAY_MS = 24 * 60 * 60 * 1000L

/**
 * PLAN.md step 9: background update job. Two well-established ideas, ported:
 *
 * 1. Smart-interval skip logic (`mangas.next_update`/`calculate_interval`) -- only manga
 *    actually due get refreshed. Finding new chapters halves the interval (it's an active
 *    ongoing series, check again sooner); finding none doubles it, up to a cap (it's
 *    probably hiatus/slow, don't waste WARP/Suwayomi egress polling it every run).
 * 2. One batched fetch instead of one HTTP call per manga ([RefreshLibraryBatch], backed by
 *    `POST /manga/batch`) -- previously a client-side `Semaphore` here rationed concurrency
 *    against the backend's per-request rate limiter; batching sidesteps that limiter
 *    entirely (one request in, server fans out with its own bounded concurrency), so the
 *    semaphore no longer does anything useful.
 *
 * This is the actual cost-control mechanism PLAN.md's "Background update job" section
 * calls out -- Suwayomi/WARP egress isn't free, so an unthrottled "refresh everything
 * every run" job would be a real ongoing cost, not just a UX nicety to avoid.
 */
class LibraryUpdateManager(
    private val mangaRepository: MangaRepository,
    private val refreshLibraryBatch: RefreshLibraryBatch,
) {
    suspend fun updateDue(now: Long = System.currentTimeMillis()): Int {
        val due = mangaRepository.getDueForUpdate(now)
        val results = refreshLibraryBatch.refresh(due)
        for (manga in due) {
            updateSchedule(manga, now, foundNew = results[manga.id]?.getOrNull()?.isNotEmpty() == true)
        }
        return due.size
    }

    private suspend fun updateSchedule(manga: Manga, now: Long, foundNew: Boolean) {
        val currentInterval = manga.calculateInterval.takeIf { it > 0 } ?: DEFAULT_INTERVAL_DAYS
        val nextInterval = if (foundNew) {
            (currentInterval / 2).coerceAtLeast(MIN_INTERVAL_DAYS)
        } else {
            (currentInterval + 1).coerceAtMost(MAX_INTERVAL_DAYS)
        }
        mangaRepository.updateUpdateSchedule(
            id = manga.id,
            lastUpdate = now,
            nextUpdate = now + nextInterval * DAY_MS,
            calculateInterval = nextInterval,
        )
    }
}
