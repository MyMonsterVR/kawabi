package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.domain.model.Anime
import com.mymonstervr.kawabi.domain.repository.AnimeRepository
import com.mymonstervr.kawabi.domain.repository.AnimeTrackRepository
import com.mymonstervr.kawabi.domain.repository.EpisodeRepository

/**
 * Moves a library anime onto another source (the app-side equivalent of manga's
 * per-manga preferred source, PLAN-preferred-source.md): the row keeps its id, tracker
 * links and history, its episodes are re-listed from the new key, and watch state carries
 * over by episode number.
 *
 * The old key's server entry is deleted, otherwise the next `/anime/entries` pull would
 * re-create it as a second library row on this and every other device.
 */
class SwitchAnimeSource(
    private val animeApi: AnimeApi,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val refreshAnimeEpisodes: RefreshAnimeEpisodes,
    private val animeTrackRepository: AnimeTrackRepository,
    private val animeSyncClient: AnimeSyncClient,
) {
    suspend fun switch(animeId: Long, newKey: String, cardCover: String? = null): Result<Anime> {
        val current = animeRepository.getById(animeId)
            ?: return Result.failure(IllegalStateException("anime no longer in library"))
        if (current.key == newKey) return Result.success(current)
        val response = animeApi.getAnime(newKey).getOrElse { return Result.failure(it) }

        val carried = episodeRepository.getForAnime(animeId).toMutableList()
        // The details screen stores a (non-favorite) row for every key it opens, so the
        // key being switched to usually already has one -- it has to go before the rewrite,
        // since keys are unique, and its watch state is worth keeping.
        animeRepository.getByKey(newKey)?.takeIf { it.id != animeId }?.let { stale ->
            carried += episodeRepository.getForAnime(stale.id)
            deleteAnimeRow(animeRepository, episodeRepository, animeTrackRepository, stale.id)
        }
        animeRepository.switchSource(
            animeId = animeId,
            newKey = newKey,
            newSource = response.source,
            newUrl = response.url,
            newTitle = response.title,
            cover = response.cover_url?.takeIf { it.isNotBlank() } ?: cardCover,
        )
        val switched = animeRepository.getById(animeId)
            ?: return Result.failure(IllegalStateException("anime no longer in library"))
        refreshAnimeEpisodes.applyResponse(switched, response).getOrElse { return Result.failure(it) }
        carryWatchedByNumber(episodeRepository, carried, animeId)

        animeApi.deleteEntry(current.key)
        animeSyncClient.sync()
        return Result.success(switched)
    }
}
