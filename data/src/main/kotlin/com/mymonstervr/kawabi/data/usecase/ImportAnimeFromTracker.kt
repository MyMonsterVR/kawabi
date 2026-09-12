package com.mymonstervr.kawabi.data.usecase

import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.network.AnimeApi
import com.mymonstervr.kawabi.data.network.TrackerTokenStore
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
    private val identityMatcher: AnimeIdentityMatcher,
    private val animeSyncClient: AnimeSyncClient,
    private val dispatchers: AppDispatchers,
) {
    suspend fun import(
        trackerId: String,
        statuses: List<String>,
        onMatched: (suspend (processed: Int, total: Int) -> Unit)? = null,
        onFetchingDetails: (suspend (count: Int) -> Unit)? = null,
    ): Result<ImportSummary> = withContext(dispatchers.io) {
        val results = mutableListOf<AnimeImportResultDto>()
        var truncated = false
        var cursor = 0
        var processed = 0
        while (true) {
            val response = animeApi.importFromTracker(trackerId, statuses, cursor)
                .getOrElse { return@withContext Result.failure(it) }
            results += response.results
            truncated = truncated || response.truncated
            processed += response.results.size
            onMatched?.invoke(processed, response.total)
            val next = response.next_cursor ?: break
            cursor = next
        }

        var imported = 0
        var alreadyPresent = 0
        val unmatched = mutableListOf<UnmatchedItem>()

        // Split into "already present" (no network needed) and "needs detail" up front, so the
        // batch fetch below only ever asks for keys we're actually about to add/refresh.
        data class Pending(val result: AnimeImportResultDto, val wasInLibrary: Boolean)
        val pending = mutableListOf<Pending>()

        for (result in results) {
            val match = result.match
            if (match == null) {
                unmatched.add(UnmatchedItem(result.title, result.remote_id))
                continue
            }
            // The matched key may be a different source's copy of a show already in the
            // library (a re-import after a source switch, or a tracker whose match landed
            // elsewhere) -- identity, not the key, decides whether this is a new row.
            val existing = animeRepository.getByKey(match.key)
                ?: identityMatcher.findFavorite(
                    title = result.title.ifBlank { match.title },
                    malId = malIdOf(trackerId, result),
                )
            if (existing != null) {
                // Repair pass for rows imported before the cover/last-watched fixes: both are
                // local no-ops when already populated, so this costs nothing on a healthy row.
                match.cover_url?.let { animeRepository.fillMissingThumbnail(existing.id, it) }
                if (result.tracker_updated_at > 0) {
                    animeRepository.touchLastWatched(existing.id, result.tracker_updated_at)
                }
                if (animeTrackRepository.getByAnimeAndTracker(existing.id, trackerId) != null) {
                    alreadyPresent++
                    continue
                }
            }
            pending.add(Pending(result, existing?.favorite == true))
        }

        onFetchingDetails?.invoke(pending.size)

        // One `POST /anime/batch` call (chunked at 100 keys server-side cap) instead of one
        // `GET /anime` per anime, which used to walk straight into the backend's 1-req/2s
        // sustained limiter for any list bigger than the initial burst.
        val batch = if (pending.isNotEmpty()) {
            animeApi.getAnimeBatch(pending.map { it.result.match!!.key }).getOrNull()
        } else {
            null
        }
        val detailsByKey = batch?.animes?.associateBy { it.key } ?: emptyMap()
        val batchErrors = batch?.errors ?: emptyMap()

        for (item in pending) {
            val result = item.result
            val match = result.match!!

            // A source that can't serve its own details page right now is reported as unmatched
            // rather than swallowed: the row then shows up in the result sheet's list, where a
            // tap opens search prefilled with the title, which is the useful recovery either way.
            val anime = run {
                val detail = detailsByKey[match.key]
                if (detail != null) {
                    addAnimeToLibrary.addWithDetail(detail, match.cover_url, malIdOf(trackerId, result))
                } else if (match.key in batchErrors || batch == null) {
                    // Fell out of the batch (either reported as an individual error, or the whole
                    // batch call failed) -- fall back to the per-anime path for just this one.
                    addAnimeToLibrary.add(match.key, match.cover_url, malIdOf(trackerId, result))
                } else {
                    Result.failure(IllegalStateException("missing from batch response"))
                }
            }.getOrElse {
                unmatched.add(UnmatchedItem(result.title, result.remote_id))
                continue
            }

            if (result.tracker_updated_at > 0) {
                animeRepository.touchLastWatched(anime.id, result.tracker_updated_at)
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
                    updatedAt = result.tracker_updated_at,
                ),
            )
            if (item.wasInLibrary) alreadyPresent++ else imported++
        }

        animeSyncClient.sync()

        Result.success(
            ImportSummary(
                imported = imported,
                alreadyPresent = alreadyPresent,
                unmatched = unmatched,
                truncated = truncated,
            ),
        )
    }

    private fun malIdOf(trackerId: String, result: AnimeImportResultDto): String? =
        result.remote_id.takeIf { trackerId == TrackerTokenStore.TRACKER_MAL && it.isNotBlank() }

    // The backend already answers the contract's canonical anime statuses, so this is a guard
    // against an unexpected value reaching the DB, not a mapping layer.
    private fun canonicalStatus(result: AnimeImportResultDto): String =
        if (result.status in TrackStatus.ALL_ANIME) result.status else TrackStatus.WATCHING
}
