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
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

data class TrackerStatus(val tracker: String, val userName: String)

data class TrackEntry(
    val remoteId: String,
    val status: String,
    val chaptersRead: Double,
    val totalChapters: Double,
    val score: Double,
)

class TrackerApi(
    private val client: OkHttpClient,
    private val dispatchers: AppDispatchers,
) {
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

    suspend fun search(tracker: String, query: String): List<TrackSearchResult> = withContext(dispatchers.io) {
        val request = getRequest("tracker/$tracker/search") { addQueryParameter("q", query) }
        execute(request, ListSerializer(TrackerSearchResultDto.serializer()))
            .map { TrackSearchResult(remoteId = it.remoteId, title = it.title, totalChapters = 0.0, coverUrl = it.coverUrl) }
    }

    /**
     * Existing tracker entry for [remoteId], or `null` for "not tracked yet" (204).
     * A 4xx/5xx is a real failure and must throw, not return null -- otherwise a
     * transient error would look like "nothing to preserve" and let a stale local
     * count silently overwrite real remote progress.
     */
    suspend fun findEntry(tracker: String, remoteId: String): TrackEntry? = withContext(dispatchers.io) {
        val request = getRequest("tracker/$tracker/entry") { addQueryParameter("remoteId", remoteId) }
        client.newCall(request).execute().use { response ->
            if (response.code == 204) return@use null
            if (!response.isSuccessful) error(errorMessageFor(response))
            val dto = networkJson.decodeFromString(TrackerEntryDto.serializer(), response.body.string())
            TrackEntry(dto.remoteId, dto.status, dto.chaptersRead, dto.totalChapters, dto.score)
        }
    }

    suspend fun upsertEntry(tracker: String, remoteId: String, status: String?, chaptersRead: Double?, score: Double?): Unit =
        withContext(dispatchers.io) {
            val body = TrackerUpsertEntryRequest(remoteId, status, chaptersRead, score)
            val request = postRequest("tracker/$tracker/entry", body, TrackerUpsertEntryRequest.serializer())
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error(errorMessageFor(response))
            }
        }

    private inline fun getRequest(path: String, block: okhttp3.HttpUrl.Builder.() -> Unit = {}): Request {
        val url = "$BASE_URL/$path".toHttpUrl().newBuilder().apply(block).build()
        return Request.Builder().url(url).get().build()
    }

    private fun <T> postRequest(path: String, body: T, serializer: KSerializer<T>): Request {
        val requestBody = networkJson.encodeToString(serializer, body).toRequestBody(JSON_MEDIA_TYPE)
        return Request.Builder().url("$BASE_URL/$path").post(requestBody).build()
    }

    private fun <T> execute(request: Request, serializer: KSerializer<T>): T {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(errorMessageFor(response))
            return networkJson.decodeFromString(serializer, response.body.string())
        }
    }
}
