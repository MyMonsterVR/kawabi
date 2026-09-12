package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.domain.model.Anime
import com.mymonstervr.kawabi.domain.repository.AnimeRepository
import com.mymonstervr.kawabi.domain.repository.AnimeTrackRepository
import com.mymonstervr.kawabi.domain.repository.EpisodeRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Collapses rows that are the same show reached from different sources (PLAN-anime.md
 * section 18) into one, so identity resolution only ever sees a single row per show and
 * can't drift between sources. Covers non-favorites too: the details screen stores a row
 * for every key it opens, and two of those for one show is exactly how a chosen source
 * silently gets replaced by a stale one.
 *
 * Survivor, in order: the row whose source the user chose most recently, then a favorite,
 * then the most watched, then the oldest row, then the furthest resume position, then the
 * most recently touched. Age outranks a resume position because rows predating
 * `source_chosen_at` record no pick, and age is the one signal a switch leaves behind:
 * switching rewrites the row in place, while the duplicate is always the later insert --
 * whereas a few seconds of resume position on the wrong source is noise. Real progress
 * (watched episodes) still outranks both.
 *
 * Library membership never dies with a loser -- if any row in the group was a favorite the
 * survivor becomes one. The losers hand over their watch marks (by episode number, never
 * unmarking), tracker links, cover and last-watched time first.
 *
 * Cheap to run repeatedly -- a library with no duplicates does no writes and no network
 * calls at all, which is why every identity lookup can afford to call it.
 */
class MergeDuplicateAnimes(
    private val animeApi: AnimeApi,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val animeTrackRepository: AnimeTrackRepository,
    private val identityMatcher: AnimeIdentityMatcher,
) {
    private val mutex = Mutex()

    suspend fun merge() = mutex.withLock { mergeLocked() }

    private suspend fun mergeLocked() {
        val all = animeRepository.getAll()
        if (all.size < 2) return
        val identities = identityMatcher.identitiesFor(all)

        val groups = mutableListOf<MutableList<Anime>>()
        for (anime in all) {
            val identity = identities.getValue(anime.id)
            val group = groups.firstOrNull { existing -> existing.any { identities.getValue(it.id).matches(identity) } }
            if (group != null) group += anime else groups += mutableListOf(anime)
        }

        for (group in groups) {
            if (group.size < 2) continue
            val stats = group.associateWith { anime ->
                val episodes = episodeRepository.getForAnime(anime.id)
                episodes.count { it.watched } to (episodes.maxOfOrNull { it.positionMs } ?: 0L)
            }
            val keeper = group.maxWith(
                compareBy<Anime>(
                    { it.sourceChosenAt },
                    { it.favorite },
                    { stats.getValue(it).first },
                    { -it.dateAdded },
                    { stats.getValue(it).second },
                    { it.lastModifiedAt },
                ),
            )
            val anyFavorite = group.any { it.favorite }
            for (duplicate in group) {
                if (duplicate.id == keeper.id) continue
                absorb(keeper, duplicate)
            }
            if (anyFavorite && !keeper.favorite) animeRepository.setFavorite(keeper.id, true)
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
        // Only a favorite ever had a server entry to tombstone; a cached non-favorite row
        // was never pushed, so asking the backend to delete it is a pointless round trip.
        if (duplicate.favorite) animeApi.deleteEntry(duplicate.key)
    }
}
