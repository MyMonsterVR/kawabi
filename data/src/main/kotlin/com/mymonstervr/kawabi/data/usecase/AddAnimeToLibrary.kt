package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.dto.AnimeDetailResponse
import com.mymonstervr.kawabi.data.network.toDomain
import com.mymonstervr.kawabi.domain.model.Anime
import com.mymonstervr.kawabi.domain.repository.AnimeRepository

class AddAnimeToLibrary(
    private val animeApi: AnimeApi,
    private val animeRepository: AnimeRepository,
    private val refreshAnimeEpisodes: RefreshAnimeEpisodes,
) {
    suspend fun add(key: String, cardCover: String? = null): Result<Anime> {
        val response = animeApi.getAnime(key).getOrElse { return Result.failure(it) }
        return addWithDetail(response, cardCover)
    }

    /**
     * Same as [add], but for a detail payload the caller already fetched (e.g. a batch import).
     * [cardCover] is the cover of the search/browse/import card the caller came from: engine
     * details pages frequently carry no thumbnail at all, and an empty cover must never win
     * over one we already know (stored row first, then the card).
     */
    suspend fun addWithDetail(response: AnimeDetailResponse, cardCover: String? = null): Result<Anime> = runCatching {
        val fetched = response.toDomain()
        val anime = fetched.copy(
            thumbnailUrl = fetched.thumbnailUrl?.takeIf { it.isNotBlank() }
                ?: cardCover?.takeIf { it.isNotBlank() },
        )
        val id = animeRepository.upsert(anime)
        animeRepository.setFavorite(id, true)
        anime.thumbnailUrl?.let { animeRepository.fillMissingThumbnail(id, it) }
        val stored = anime.copy(id = id)
        refreshAnimeEpisodes.applyResponse(stored, response)
        stored
    }
}
