package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.toSourceEpisodes
import com.mymonstervr.kawabi.domain.interactor.NoEpisodesException
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
 *
 * Every other local row for the same show goes away here too, not just the one sitting on
 * the key being switched to: leaving one behind is what let a stale source win the next
 * identity lookup and quietly take the switch back.
 */
class SwitchAnimeSource(
    private val animeApi: AnimeApi,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val refreshAnimeEpisodes: RefreshAnimeEpisodes,
    private val animeTrackRepository: AnimeTrackRepository,
    private val animeSyncClient: AnimeSyncClient,
    private val identityMatcher: AnimeIdentityMatcher,
) {
    suspend fun switch(animeId: Long, newKey: String, cardCover: String? = null): Result<Anime> {
        val current = animeRepository.getById(animeId)
            ?: return Result.failure(IllegalStateException("anime no longer in library"))
        if (current.key == newKey) return Result.success(current)
        val response = animeApi.getAnime(newKey).getOrElse { return Result.failure(it) }
        if (response.toSourceEpisodes().isEmpty()) return Result.failure(NoEpisodesException())

        val carried = episodeRepository.getForAnime(animeId).toMutableList()
        // The details screen stores a (non-favorite) row for every key it opens, so the
        // key being switched to usually already has one, and the show can have further
        // rows under yet other sources. All of them go before the rewrite -- keys are
        // unique, and their watch state, tracker links and library membership are worth
        // keeping.
        val malId = identityMatcher.malIdOf(animeId)
        val absorbed = buildList {
            animeRepository.getByKey(newKey)?.takeIf { it.id != animeId }?.let { add(it) }
            addAll(identityMatcher.matching(response.title, malId, excludeId = animeId))
            addAll(identityMatcher.matching(current.title, malId, excludeId = animeId))
        }.distinctBy { it.id }
        for (row in absorbed) {
            carried += episodeRepository.getForAnime(row.id)
            for (track in animeTrackRepository.getForAnime(row.id)) {
                if (animeTrackRepository.getByAnimeAndTracker(animeId, track.trackerId) != null) continue
                animeTrackRepository.link(track.copy(id = 0, animeId = animeId))
            }
            deleteAnimeRow(animeRepository, episodeRepository, animeTrackRepository, row.id)
            if (row.favorite) animeApi.deleteEntry(row.key)
        }
        animeRepository.switchSource(
            animeId = animeId,
            newKey = newKey,
            newSource = response.source,
            newUrl = response.url,
            newTitle = response.title,
            cover = response.cover_url?.takeIf { it.isNotBlank() } ?: cardCover,
            sourceChosenAt = System.currentTimeMillis(),
        )
        if (!current.favorite && absorbed.any { it.favorite }) animeRepository.setFavorite(animeId, true)
        val switched = animeRepository.getById(animeId)
            ?: return Result.failure(IllegalStateException("anime no longer in library"))
        refreshAnimeEpisodes.applyResponse(switched, response).getOrElse { return Result.failure(it) }
        carryWatchedByNumber(episodeRepository, carried, animeId)

        animeApi.deleteEntry(current.key)
        animeSyncClient.sync()
        return Result.success(switched)
    }
}
