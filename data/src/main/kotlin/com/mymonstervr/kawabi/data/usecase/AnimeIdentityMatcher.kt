package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.TrackerTokenStore
import com.mymonstervr.kawabi.domain.model.Anime
import com.mymonstervr.kawabi.domain.model.AnimeIdentity
import com.mymonstervr.kawabi.domain.model.animeIdentityOf
import com.mymonstervr.kawabi.domain.repository.AnimeRepository
import com.mymonstervr.kawabi.domain.repository.AnimeTrackRepository

/**
 * Answers "is this show already in the library, under any source?" using [AnimeIdentity]
 * (PLAN-anime.md section 18). The normalized title is checked first because it needs no
 * extra query; the MAL id only comes into play when the titles differ across sources
 * (localized/romanized spellings), which is exactly where a tracker link saves us.
 *
 * Every lookup merges duplicates first, so resolution always sees at most one row per
 * show and never has to guess which of several rows the user meant. [mergeDuplicates] is
 * a provider rather than the use case itself only to break the construction cycle -- the
 * merge needs this matcher to group rows.
 */
class AnimeIdentityMatcher(
    private val animeRepository: AnimeRepository,
    private val animeTrackRepository: AnimeTrackRepository,
    private val mergeDuplicates: () -> MergeDuplicateAnimes,
) {
    suspend fun malIdOf(animeId: Long): String? =
        animeTrackRepository.getByAnimeAndTracker(animeId, TrackerTokenStore.TRACKER_MAL)
            ?.remoteId
            ?.takeIf { it.isNotBlank() }

    suspend fun identityOf(anime: Anime): AnimeIdentity =
        animeIdentityOf(anime.title, malIdOf(anime.id))

    /** Identities for a whole pool with one tracker query instead of one lookup per row. */
    suspend fun identitiesFor(pool: List<Anime>): Map<Long, AnimeIdentity> {
        val malIds = animeTrackRepository.getRemoteIdsByTracker(TrackerTokenStore.TRACKER_MAL)
        return pool.associate { it.id to animeIdentityOf(it.title, malIds[it.id]) }
    }

    /** The favorite that is the same show as [title]/[malId], ignoring the row for [excludeKey]. */
    suspend fun findFavorite(title: String, malId: String? = null, excludeKey: String? = null): Anime? {
        mergeDuplicates().merge()
        return findBest(animeRepository.getFavorites(), title, malId, excludeKey)
    }

    /**
     * The local row -- favorite or not -- that is the same show as [title]/[malId], ignoring
     * [excludeKey]. Used to keep opening the source the user last switched to instead of
     * re-caching a fresh row under a stale key (PLAN-anime.md section 18 follow-up).
     */
    suspend fun findAny(title: String, malId: String? = null, excludeKey: String? = null): Anime? {
        mergeDuplicates().merge()
        return findBest(animeRepository.getAll(), title, malId, excludeKey)
    }

    /**
     * Every row that is the same show as [title]/[malId], [excludeId] aside. Unlike
     * [findAny] this does not merge first: the source switch calls it precisely to collect
     * the rows it is about to fold into the row being switched.
     */
    suspend fun matching(title: String, malId: String? = null, excludeId: Long? = null): List<Anime> {
        val target = animeIdentityOf(title, malId)
        val pool = animeRepository.getAll().filter { it.id != excludeId }
        val identities = identitiesFor(pool)
        return pool.filter { identities.getValue(it.id).matches(target) }
    }

    private suspend fun findBest(pool: List<Anime>, title: String, malId: String?, excludeKey: String?): Anime? {
        val target = animeIdentityOf(title, malId)
        val candidates = pool.filter { it.key != excludeKey }
        val identities = identitiesFor(candidates)
        val matches = candidates.filter { identities.getValue(it.id).matches(target) }
        return matches.firstOrNull { it.favorite } ?: matches.firstOrNull()
    }
}
