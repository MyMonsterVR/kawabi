package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.dto.AnimeDetailResponse
import com.mymonstervr.kawabi.data.network.toSourceEpisodes
import com.mymonstervr.kawabi.domain.interactor.SyncEpisodesWithSource
import com.mymonstervr.kawabi.domain.model.Anime
import com.mymonstervr.kawabi.domain.model.Episode
import com.mymonstervr.kawabi.domain.repository.AnimeRepository

/** Anime counterpart of [RefreshMangaChapters]. */
class RefreshAnimeEpisodes(
    private val animeApi: AnimeApi,
    private val animeRepository: AnimeRepository,
    private val syncEpisodesWithSource: SyncEpisodesWithSource,
) {
    suspend fun refresh(anime: Anime): Result<List<Episode>> {
        val response = animeApi.getAnime(anime.key).getOrElse { return Result.failure(it) }
        return applyResponse(anime, response)
    }

    suspend fun applyResponse(anime: Anime, response: AnimeDetailResponse): Result<List<Episode>> {
        val sourceEpisodes = response.toSourceEpisodes()
        return runCatching { syncEpisodesWithSource.await(anime.id, sourceEpisodes) }
            .onSuccess { animeRepository.setTotalEpisodes(anime.id, sourceEpisodes.size.toDouble()) }
    }
}
