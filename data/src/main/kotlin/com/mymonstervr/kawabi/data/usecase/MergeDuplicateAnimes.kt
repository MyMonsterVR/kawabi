package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.domain.model.Anime
import com.mymonstervr.kawabi.domain.repository.AnimeRepository
import com.mymonstervr.kawabi.domain.repository.AnimeTrackRepository
import com.mymonstervr.kawabi.domain.repository.EpisodeRepository

/**
 * One-off repair for libraries built before cross-source identity existed (PLAN-anime.md
 * section 18): favorites that are the same show reached from different sources collapse
 * into one row. The row with the most watched episodes wins, the others hand over their
 * watch marks (by episode number, never unmarking), their tracker links and their
 * last-watched time before being deleted along with their server entry.
 *
 * Cheap to run repeatedly -- a library with no duplicates does no writes at all.
 */
class MergeDuplicateAnimes(
    private val animeApi: AnimeApi,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val animeTrackRepository: AnimeTrackRepository,
    private val identityMatcher: AnimeIdentityMatcher,
) {
    suspend fun merge() {
        val favorites = animeRepository.getFavorites()
        if (favorites.size < 2) return
        val identities = favorites.associateWith { identityMatcher.identityOf(it) }

        val groups = mutableListOf<MutableList<Anime>>()
        for (anime in favorites) {
            val identity = identities.getValue(anime)
            val group = groups.firstOrNull { existing -> existing.any { identities.getValue(it).matches(identity) } }
            if (group != null) group += anime else groups += mutableListOf(anime)
        }

        for (group in groups) {
            if (group.size < 2) continue
            val watchedCounts = group.associateWith { anime ->
                episodeRepository.getForAnime(anime.id).count { it.watched }
            }
            val keeper = group.maxBy { watchedCounts.getValue(it) }
            for (duplicate in group) {
                if (duplicate.id == keeper.id) continue
                absorb(keeper, duplicate)
            }
        }
    }

    private suspend fun absorb(keeper: Anime, duplicate: Anime) {
        carryWatchedByNumber(episodeRepository, episodeRepository.getForAnime(duplicate.id), keeper.id)
        for (track in animeTrackRepository.getForAnime(duplicate.id)) {
            if (animeTrackRepository.getByAnimeAndTracker(keeper.id, track.trackerId) != null) continue
            animeTrackRepository.link(track.copy(id = 0, animeId = keeper.id))
        }
        if (duplicate.lastWatchedAt > 0) animeRepository.touchLastWatched(keeper.id, duplicate.lastWatchedAt)
        duplicate.thumbnailUrl?.let { animeRepository.fillMissingThumbnail(keeper.id, it) }
        deleteAnimeRow(animeRepository, episodeRepository, animeTrackRepository, duplicate.id)
        animeApi.deleteEntry(duplicate.key)
    }
}

