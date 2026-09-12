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
    suspend fun findFavorite(title: String, malId: String? = null, excludeKey: String? = null): Anime? {
        val target = animeIdentityOf(title, malId)
        val candidates = animeRepository.getFavorites().filter { it.key != excludeKey }
        candidates.firstOrNull { animeIdentityOf(it.title).matches(target) }?.let { return it }
        if (target.malId == null) return null
        return candidates.firstOrNull { malIdOf(it.id) == target.malId }
    }
}
