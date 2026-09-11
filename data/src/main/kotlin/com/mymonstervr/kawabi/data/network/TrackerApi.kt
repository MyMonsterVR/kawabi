package com.mymonstervr.kawabi.data.network

import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.network.dto.TrackerConnectKitsuRequest
import com.mymonstervr.kawabi.data.network.dto.TrackerConnectMalRequest
import com.mymonstervr.kawabi.data.network.dto.TrackerConnectResponse
import com.mymonstervr.kawabi.data.network.dto.TrackerEntryDto
import com.mymonstervr.kawabi.data.network.dto.TrackerSearchResultDto
import com.mymonstervr.kawabi.data.network.dto.TrackerStatusDto
import com.mymonstervr.kawabi.data.network.dto.TrackerUpsertEntryRequest
import com.mymonstervr.kawabi.data.track.dto.TrackSearchResult
import com.mymonstervr.kawabi.domain.model.MediaType
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class TrackerStatus(val tracker: String, val userName: String)

data class TrackEntry(
    val remoteId: String,
    val status: String,
    val chaptersRead: Double,
    val totalChapters: Double,
    val score: Double,
)

class TrackerApi(
    client: OkHttpClient,
    dispatchers: AppDispatchers,
) : BackendApiClient(client, dispatchers) {
    suspend fun connectMal(code: String, codeVerifier: String): String = withContext(dispatchers.io) {
        val request = postRequest("tracker/mal/connect", TrackerConnectMalRequest(code, codeVerifier), TrackerConnectMalRequest.serializer())
        execute(request, TrackerConnectResponse.serializer()).userName
    }

    suspend fun connectKitsu(email: String, password: String): String = withContext(dispatchers.io) {
        val request = postRequest("tracker/kitsu/connect", TrackerConnectKitsuRequest(email, password), TrackerConnectKitsuRequest.serializer())
        execute(request, TrackerConnectResponse.serializer()).userName
    }

    suspend fun disconnect(tracker: String): Unit = withContext(dispatchers.io) {
        val request = Request.Builder()
            .url("$BASE_URL/tracker/$tracker/disconnect")
            .post("".toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(errorMessageFor(response))
        }
    }

    suspend fun status(): List<TrackerStatus> = withContext(dispatchers.io) {
        execute(getRequest("tracker/status"), ListSerializer(TrackerStatusDto.serializer()))
            .map { TrackerStatus(it.tracker, it.userName) }
    }

    suspend fun search(tracker: String, query: String, mediaType: MediaType = MediaType.MANGA): List<TrackSearchResult> =
        withContext(dispatchers.io) {
            val request = getRequest("tracker/$tracker/search") {
                addQueryParameter("q", query)
                mediaType.wireValue?.let { addQueryParameter("type", it) }
            }
            execute(request, ListSerializer(TrackerSearchResultDto.serializer()))
                .map { dto ->
                    TrackSearchResult(
                        remoteId = dto.remoteId,
                        title = dto.title,
                        totalChapters = maxOf(dto.totalEpisodes, dto.total_episodes),
                        coverUrl = dto.coverUrl,
                    )
                }
        }

    /**
     * Existing tracker entry for [remoteId], or `null` for "not tracked yet" (204).
     * A 4xx/5xx is a real failure and must throw, not return null -- otherwise a
     * transient error would look like "nothing to preserve" and let a stale local
     * count silently overwrite real remote progress.
     */
    suspend fun findEntry(tracker: String, remoteId: String, mediaType: MediaType = MediaType.MANGA): TrackEntry? =
        withContext(dispatchers.io) {
            val request = getRequest("tracker/$tracker/entry") {
                addQueryParameter("remoteId", remoteId)
                mediaType.wireValue?.let { addQueryParameter("type", it) }
            }
            client.newCall(request).execute().use { response ->
                if (response.code == 204) return@use null
                if (!response.isSuccessful) error(errorMessageFor(response))
                val dto = networkJson.decodeFromString(TrackerEntryDto.serializer(), response.body.string())
                TrackEntry(dto.remoteId, dto.status, progressOf(dto), totalOf(dto), dto.score)
            }
        }

    suspend fun upsertEntry(
        tracker: String,
        remoteId: String,
        status: String?,
        chaptersRead: Double?,
        score: Double?,
        mediaType: MediaType = MediaType.MANGA,
    ): Unit = withContext(dispatchers.io) {
        val anime = mediaType == MediaType.ANIME
        val body = TrackerUpsertEntryRequest(
            remoteId = remoteId,
            status = status,
            chaptersRead = chaptersRead,
            score = score,
            type = mediaType.wireValue,
            episodesWatched = chaptersRead.takeIf { anime },
            episodes_watched = chaptersRead.takeIf { anime },
        )
        val request = postRequest("tracker/$tracker/entry", body, TrackerUpsertEntryRequest.serializer())
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(errorMessageFor(response))
        }
    }

    // The contract names anime progress `episodes_watched`/`total_episodes` while the
    // manga wire this endpoint already speaks is camelCase `chaptersRead`/`totalChapters`.
    // Taking the max across every spelling reads whichever the backend actually emits
    // (all the others decode to their 0.0 default) instead of guessing one and silently
    // reporting no progress at all if the guess is wrong.
    private fun progressOf(dto: TrackerEntryDto): Double =
        maxOf(dto.chaptersRead, dto.episodesWatched, dto.episodes_watched)

    private fun totalOf(dto: TrackerEntryDto): Double =
        maxOf(dto.totalChapters, dto.totalEpisodes, dto.total_episodes)
}
