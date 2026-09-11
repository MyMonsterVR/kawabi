package com.mymonstervr.kawabi.domain.interactor

import com.mymonstervr.kawabi.domain.model.Episode
import com.mymonstervr.kawabi.domain.model.SourceEpisode
import com.mymonstervr.kawabi.domain.repository.EpisodeRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private const val UNKNOWN_EPISODE_NUMBER = -1.0

/**
 * Anime port of [SyncChaptersWithSource]: diffs an anime's locally stored episodes against
 * a fresh listing from a source, matching on episode `url` so local watch state
 * (watched/position_ms/duration_ms) survives a re-listing. New episodes are inserted,
 * removed ones dropped, and a re-uploaded episode (same number, previously removed in this
 * same pass) inherits the old row's watch state instead of showing up as unwatched.
 */
class SyncEpisodesWithSource(
    private val episodeRepository: EpisodeRepository,
) {
    suspend fun await(animeId: Long, sourceEpisodes: List<SourceEpisode>): List<Episode> {
        val mutex = animeLocks.getOrPut(animeId) { Mutex() }
        return mutex.withLock { awaitLocked(animeId, sourceEpisodes) }
    }

    private suspend fun awaitLocked(animeId: Long, sourceEpisodes: List<SourceEpisode>): List<Episode> {
        if (sourceEpisodes.isEmpty()) throw NoEpisodesException()

        val fresh = sourceEpisodes.distinctBy { it.url }
            .mapIndexed { index, episode -> episode.url to (episode to index) }
            .toMap()

        val existing = episodeRepository.getForAnime(animeId)
        val existingByUrl = existing.associateBy { it.url }

        val newEntries = fresh.filterKeys { it !in existingByUrl }
        val removedEpisodes = existing.filter { it.url !in fresh }
        val matchedUrls = fresh.keys intersect existingByUrl.keys

        val updatedEntries = matchedUrls.mapNotNull { url ->
            val (sourceEpisode, index) = fresh.getValue(url)
            val current = existingByUrl.getValue(url)
            val changed = current.name != sourceEpisode.name ||
                current.key != sourceEpisode.key ||
                current.episodeNumber != sourceEpisode.episodeNumber ||
                current.dateUpload != sourceEpisode.dateUpload ||
                current.sourceOrder != index
            if (changed) current to (sourceEpisode to index) else null
        }

        if (newEntries.isEmpty() && removedEpisodes.isEmpty() && updatedEntries.isEmpty()) {
            return emptyList()
        }

        val carryoverByNumber = removedEpisodes
            .filter { it.episodeNumber != UNKNOWN_EPISODE_NUMBER }
            .associateBy { it.episodeNumber }

        val now = System.currentTimeMillis()
        var fetchOffset = newEntries.size
        val newlyAdded = mutableListOf<Episode>()

        for ((url, entry) in newEntries) {
            val (sourceEpisode, index) = entry
            val carryover = carryoverByNumber[sourceEpisode.episodeNumber]

            val episode = Episode(
                id = 0,
                animeId = animeId,
                key = sourceEpisode.key,
                url = url,
                name = sourceEpisode.name,
                watched = carryover?.watched ?: false,
                positionMs = carryover?.positionMs ?: 0,
                durationMs = carryover?.durationMs ?: 0,
                episodeNumber = sourceEpisode.episodeNumber,
                sourceOrder = index,
                dateUpload = sourceEpisode.dateUpload,
                dateFetch = carryover?.dateFetch ?: (now + fetchOffset--),
                lastModifiedAt = 0,
                version = 0,
                isSyncing = false,
            )
            val id = episodeRepository.insert(episode)
            if (carryover == null) newlyAdded += episode.copy(id = id)
        }

        for ((current, entry) in updatedEntries) {
            val (sourceEpisode, index) = entry
            episodeRepository.updateDetails(
                id = current.id,
                key = sourceEpisode.key,
                name = sourceEpisode.name,
                episodeNumber = sourceEpisode.episodeNumber,
                sourceOrder = index,
                dateUpload = sourceEpisode.dateUpload,
            )
        }

        if (removedEpisodes.isNotEmpty()) {
            episodeRepository.deleteByIds(removedEpisodes.map { it.id })
        }

        return newlyAdded
    }

    private companion object {
        val animeLocks = ConcurrentHashMap<Long, Mutex>()
    }
}
