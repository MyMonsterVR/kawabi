package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.toDomain
import com.mymonstervr.kawabi.domain.model.Anime
import com.mymonstervr.kawabi.domain.repository.AnimeRepository

class AddAnimeToLibrary(
    private val animeApi: AnimeApi,
    private val animeRepository: AnimeRepository,
    private val refreshAnimeEpisodes: RefreshAnimeEpisodes,
) {
    suspend fun add(key: String): Result<Anime> {
        val response = animeApi.getAnime(key).getOrElse { return Result.failure(it) }
        val anime = response.toDomain()
        val id = animeRepository.upsert(anime)
        animeRepository.setFavorite(id, true)
        val stored = anime.copy(id = id)
        refreshAnimeEpisodes.applyResponse(stored, response)
        return Result.success(stored)
    }
}
