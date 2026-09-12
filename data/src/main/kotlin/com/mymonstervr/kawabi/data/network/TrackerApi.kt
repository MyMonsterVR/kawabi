package com.mymonstervr.kawabi.data.network

import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.network.dto.AnimeTrackerEntryDto
import com.mymonstervr.kawabi.data.network.dto.AnimeTrackerSearchResultDto
import com.mymonstervr.kawabi.data.network.dto.AnimeTrackerUpsertEntryRequest
import com.mymonstervr.kawabi.data.network.dto.TrackerConnectAniListRequest
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
import okhttp3.Response

data class TrackerStatus(
    val tracker: String,
    val userName: String,
    val expired: Boolean = false,
    val error: String? = null,
)

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
    private val trackerAuthNotifier: TrackerAuthNotifier,
) : BackendApiClient(client, dispatchers) {

    /** A 401 with this body means the tracker's own upstream auth died, not this app's backend session. */
    private fun notifyIfTrackerAuthExpired(response: Response, trackerId: String) {
        if (response.code != 401) return
        val body = runCatching { response.peekBody(2048).string() }.getOrNull() ?: return
        if (body.contains("tracker auth expired")) trackerAuthNotifier.notifyExpired(trackerId)
    }
    suspend fun connectMal(code: String, codeVerifier: String): String = withContext(dispatchers.io) {
        val request = postRequest("tracker/mal/connect", TrackerConnectMalRequest(code, codeVerifier), TrackerConnectMalRequest.serializer())
        execute(request, TrackerConnectResponse.serializer()).userName
    }

    suspend fun connectKitsu(email: String, password: String): String = withContext(dispatchers.io) {
        val request = postRequest("tracker/kitsu/connect", TrackerConnectKitsuRequest(email, password), TrackerConnectKitsuRequest.serializer())
        execute(request, TrackerConnectResponse.serializer()).userName
    }

    /**
     * AniList's code-for-token exchange needs the client secret, so it happens on the backend
     * (PLAN-anime.md section 15) -- the app only forwards the authorization code the browser
     * redirect handed it. No code verifier: AniList's flow is plain OAuth2, not PKCE.
     */
    suspend fun connectAniList(code: String): String = withContext(dispatchers.io) {
        val request = postRequest("tracker/anilist/connect", TrackerConnectAniListRequest(code), TrackerConnectAniListRequest.serializer())
        execute(request, TrackerConnectResponse.serializer()).userName
    }

    suspend fun disconnect(tracker: String): Unit = withContext(dispatchers.io) {
        val request = Request.Builder()
            .url("$BASE_URL/tracker/$tracker/disconnect")
            .post("".toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            notifyIfTrackerAuthExpired(response, tracker)
            if (!response.isSuccessful) error(errorMessageFor(response))
        }
    }

    /** [verify] = actively check upstream instead of returning the last-known cached status. */
    suspend fun status(verify: Boolean = false): List<TrackerStatus> = withContext(dispatchers.io) {
        val request = getRequest("tracker/status") {
            if (verify) addQueryParameter("verify", "1")
        }
        execute(request, ListSerializer(TrackerStatusDto.serializer()))
            .map { TrackerStatus(it.tracker, it.userName, it.expired, it.error) }
    }

    suspend fun search(tracker: String, query: String, mediaType: MediaType = MediaType.MANGA): List<TrackSearchResult> =
        withContext(dispatchers.io) {
            val request = getRequest("tracker/$tracker/search") {
                addQueryParameter("q", query)
                mediaType.wireValue?.let { addQueryParameter("type", it) }
            }
            client.newCall(request).execute().use { response ->
                notifyIfTrackerAuthExpired(response, tracker)
                if (!response.isSuccessful) error(errorMessageFor(response))
                val body = response.body.string()
                if (mediaType == MediaType.ANIME) {
                    networkJson.decodeFromString(ListSerializer(AnimeTrackerSearchResultDto.serializer()), body)
                        .map { dto ->
                            TrackSearchResult(
                                remoteId = dto.remote_id,
                                title = dto.title,
                                totalChapters = dto.total_episodes,
                                coverUrl = dto.cover_url,
                            )
                        }
                } else {
                    networkJson.decodeFromString(ListSerializer(TrackerSearchResultDto.serializer()), body)
                        .map { dto ->
                            TrackSearchResult(
                                remoteId = dto.remoteId,
                                title = dto.title,
                                totalChapters = dto.totalEpisodes,
                                coverUrl = dto.coverUrl,
                            )
                        }
                }
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
                notifyIfTrackerAuthExpired(response, tracker)
                if (response.code == 204) return@use null
                if (!response.isSuccessful) error(errorMessageFor(response))
                val body = response.body.string()
                if (mediaType == MediaType.ANIME) {
                    val dto = networkJson.decodeFromString(AnimeTrackerEntryDto.serializer(), body)
                    TrackEntry(dto.remote_id, dto.status, dto.episodes_watched, dto.total_episodes, dto.score)
                } else {
                    val dto = networkJson.decodeFromString(TrackerEntryDto.serializer(), body)
                    TrackEntry(dto.remoteId, dto.status, dto.chaptersRead, dto.totalChapters, dto.score)
                }
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
        val request = if (mediaType == MediaType.ANIME) {
            val body = AnimeTrackerUpsertEntryRequest(
                remote_id = remoteId,
                type = mediaType.wireValue!!,
                status = status,
                episodes_watched = chaptersRead,
                score = score,
            )
            postRequest("tracker/$tracker/entry", body, AnimeTrackerUpsertEntryRequest.serializer())
        } else {
            val body = TrackerUpsertEntryRequest(
                remoteId = remoteId,
                status = status,
                chaptersRead = chaptersRead,
                score = score,
            )
            postRequest("tracker/$tracker/entry", body, TrackerUpsertEntryRequest.serializer())
        }
        client.newCall(request).execute().use { response ->
            notifyIfTrackerAuthExpired(response, tracker)
            if (!response.isSuccessful) error(errorMessageFor(response))
        }
    }
}
