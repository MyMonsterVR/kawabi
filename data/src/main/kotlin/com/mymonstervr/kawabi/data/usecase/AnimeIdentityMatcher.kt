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
 */
class AnimeIdentityMatcher(
    private val animeRepository: AnimeRepository,
    private val animeTrackRepository: AnimeTrackRepository,
) {
    suspend fun malIdOf(animeId: Long): String? =
        animeTrackRepository.getByAnimeAndTracker(animeId, TrackerTokenStore.TRACKER_MAL)
            ?.remoteId
            ?.takeIf { it.isNotBlank() }

    suspend fun identityOf(anime: Anime): AnimeIdentity =
        animeIdentityOf(anime.title, malIdOf(anime.id))

    /** The favorite that is the same show as [title]/[malId], ignoring the row for [excludeKey]. */
    suspend fun findFavorite(title: String, malId: String? = null, excludeKey: String? = null): Anime? =
        findBest(animeRepository.getFavorites(), title, malId, excludeKey)

    /**
     * The local row -- favorite or not -- that is the same show as [title]/[malId], ignoring
     * [excludeKey]. Used to keep opening the source the user last switched to instead of
     * re-caching a fresh row under a stale key (PLAN-anime.md section 18 follow-up): when
     * several local rows match, the favorite wins, otherwise the most recently touched one
     * does, since that's the source the user most recently chose.
     */
    suspend fun findAny(title: String, malId: String? = null, excludeKey: String? = null): Anime? =
        findBest(animeRepository.getAll(), title, malId, excludeKey)

    private suspend fun findBest(pool: List<Anime>, title: String, malId: String?, excludeKey: String?): Anime? {
        val target = animeIdentityOf(title, malId)
        val candidates = pool.filter { it.key != excludeKey }
        pickBest(candidates.filter { animeIdentityOf(it.title).matches(target) })?.let { return it }
        if (target.malId == null) return null
        return pickBest(candidates.filter { malIdOf(it.id) == target.malId })
    }

    private fun pickBest(matches: List<Anime>): Anime? =
        matches.firstOrNull { it.favorite } ?: matches.maxByOrNull { it.lastModifiedAt }
}
