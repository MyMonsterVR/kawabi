package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.dto.AnimeImportResultDto
import com.mymonstervr.kawabi.data.track.trackingUrlFor
import com.mymonstervr.kawabi.domain.model.AnimeTrack
import com.mymonstervr.kawabi.domain.model.MediaType
import com.mymonstervr.kawabi.domain.model.TrackStatus
import com.mymonstervr.kawabi.domain.repository.AnimeRepository
import com.mymonstervr.kawabi.domain.repository.AnimeTrackRepository
import com.mymonstervr.kawabi.domain.repository.EpisodeRepository
import kotlinx.coroutines.withContext

data class UnmatchedItem(val title: String, val remoteId: String)

data class ImportSummary(
    val imported: Int,
    val alreadyPresent: Int,
    val unmatched: List<UnmatchedItem>,
    val truncated: Boolean,
)

/**
 * Pulls the user's MAL/Kitsu anime list through `POST /anime/import` and materialises every
 * server-matched item locally (PLAN-anime.md section 14). The backend deliberately does not
 * write `anime_entries` itself -- the app owns library creation so the local anime/episode/
 * track rows stay consistent with each other -- so everything below the request is local work.
 */
class ImportAnimeFromTracker(
    private val animeApi: AnimeApi,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val animeTrackRepository: AnimeTrackRepository,
    private val addAnimeToLibrary: AddAnimeToLibrary,
    private val animeSyncClient: AnimeSyncClient,
    private val dispatchers: AppDispatchers,
) {
    suspend fun import(trackerId: String, statuses: List<String>): Result<ImportSummary> = withContext(dispatchers.io) {
        val response = animeApi.importFromTracker(trackerId, statuses).getOrElse { return@withContext Result.failure(it) }

        var imported = 0
        var alreadyPresent = 0
        val unmatched = mutableListOf<UnmatchedItem>()

        for (result in response.results) {
            val match = result.match
            if (match == null) {
                unmatched.add(UnmatchedItem(result.title, result.remote_id))
                continue
            }
            val existing = animeRepository.getByKey(match.key)
            if (existing != null && animeTrackRepository.getByAnimeAndTracker(existing.id, trackerId) != null) {
                alreadyPresent++
                continue
            }
            val wasInLibrary = existing?.favorite == true
            // A source that can't serve its own details page right now is reported as unmatched
            // rather than swallowed: the row then shows up in the result sheet's list, where a
            // tap opens search prefilled with the title, which is the useful recovery either way.
            val anime = addAnimeToLibrary.add(match.key).getOrElse {
                unmatched.add(UnmatchedItem(result.title, result.remote_id))
                continue
            }

            if (result.episodes_watched > 0) {
                episodeRepository.markWatchedUpToNumber(anime.id, result.episodes_watched)
            }
            animeTrackRepository.link(
                AnimeTrack(
                    id = 0,
                    animeId = anime.id,
                    trackerId = trackerId,
                    remoteId = result.remote_id,
                    libraryId = null,
                    title = result.title.ifBlank { match.title },
                    trackingUrl = trackingUrlFor(trackerId, result.remote_id, MediaType.ANIME),
                    totalEpisodes = result.total_episodes,
                    lastEpisodeWatched = result.episodes_watched,
                    score = result.score,
                    status = canonicalStatus(result),
                ),
            )
            if (wasInLibrary) alreadyPresent++ else imported++
        }

        animeSyncClient.sync()

        Result.success(
            ImportSummary(
                imported = imported,
                alreadyPresent = alreadyPresent,
                unmatched = unmatched,
                truncated = response.truncated,
            ),
        )
    }

    // The backend already answers the contract's canonical anime statuses, so this is a guard
    // against an unexpected value reaching the DB, not a mapping layer.
    private fun canonicalStatus(result: AnimeImportResultDto): String =
        if (result.status in TrackStatus.ALL_ANIME) result.status else TrackStatus.WATCHING
}
