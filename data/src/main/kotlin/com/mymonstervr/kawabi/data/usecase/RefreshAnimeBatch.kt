package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.domain.model.Anime
import com.mymonstervr.kawabi.domain.model.Episode
import java.io.IOException

/**
 * Anime counterpart of [RefreshLibraryBatch]: refreshes many anime in one network round trip
 * via `POST /anime/cached` (near-instant, backend-cached) instead of one live `GET /anime`
 * per anime. [AnimeApi.getAnimeCached] already chunks at the backend's 100-key cap
 * internally, so this just calls it once per [refresh].
 */
class RefreshAnimeBatch(
    private val animeApi: AnimeApi,
    private val refreshAnimeEpisodes: RefreshAnimeEpisodes,
) {
    suspend fun refresh(animes: List<Anime>): Map<Long, Result<List<Episode>>> {
        if (animes.isEmpty()) return emptyMap()
        val response = animeApi.getAnimeCached(animes.map { it.key }).getOrElse { error ->
            return animes.associate { it.id to Result.failure(error) }
        }
        val byKey = response.animes.associateBy { it.key }

        return animes.associate { anime ->
            val entry = byKey[anime.key]
            val result = when {
                entry != null -> refreshAnimeEpisodes.applyResponse(anime, entry)
                response.errors.containsKey(anime.key) ->
                    Result.failure(IOException(response.errors[anime.key]))
                else -> Result.failure(IOException("missing from batch response"))
            }
            anime.id to result
        }
    }
}
