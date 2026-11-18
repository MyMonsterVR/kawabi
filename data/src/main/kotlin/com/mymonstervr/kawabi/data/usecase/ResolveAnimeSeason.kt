package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.dto.AnimeSeasonDto
import com.mymonstervr.kawabi.domain.model.animeIdentityOf

/**
 * Finds a season from `/anime/seasons` on the user's enabled sources. Port of kawabi-web's
 * resolveSeasonKey: search each of the season's names in turn and take the first query
 * with a title match, preferring [currentSource] so changing season doesn't change site.
 */
class ResolveAnimeSeason(private val animeApi: AnimeApi) {
    suspend fun resolveKey(season: AnimeSeasonDto, currentSource: String, adult: Boolean = false): String? {
        val names = (listOf(season.english, season.romaji, season.title) + season.alt_titles)
            .filterNotNull().filter { it.isNotBlank() }
        val identities = names.map { animeIdentityOf(it) }
        for (query in names.distinct().take(MAX_QUERIES)) {
            val results = animeApi.search(query, adult).getOrNull()?.results ?: continue
            val matches = results.filter { card ->
                val identity = animeIdentityOf(card.title)
                identities.any { it.matches(identity) }
            }
            if (matches.isNotEmpty()) {
                return (matches.firstOrNull { it.source == currentSource } ?: matches.first()).key
            }
        }
        return null
    }

    private companion object {
        const val MAX_QUERIES = 6
    }
}
