package com.mymonstervr.kawabi.data.network

import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.network.dto.EntriesRequest
import com.mymonstervr.kawabi.data.network.dto.EntriesResponse
import com.mymonstervr.kawabi.data.network.dto.EntryDto
import com.mymonstervr.kawabi.data.network.dto.LibraryAddRequest
import com.mymonstervr.kawabi.data.network.dto.LibraryAddResponse
import com.mymonstervr.kawabi.data.network.dto.ProgressDto
import com.mymonstervr.kawabi.data.network.dto.ProgressRequest
import com.mymonstervr.kawabi.data.network.dto.ProgressResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

// Backend's JSON-endpoint limiter is a token bucket refilling 1 req/2s, burst 30
// (internal/middleware/ratelimit.go). Pulling per-chapter progress has no batch
// alternative (GET /progress?url= is one manga at a time) -- a library much past ~30
// entries reliably drains the burst mid-pull on every sync. The 429 response's
// Retry-After header is hardcoded to 60s server-side (full bucket reset, not the actual
// per-token refill), which would make a normal sync feel like it hung -- retry against
// the real refill rate instead.
private const val RATE_LIMIT_RETRY_DELAY_MS = 2_100L
private const val RATE_LIMIT_MAX_RETRIES = 5

private class RateLimitedException : Exception()

class SyncApi(
    private val client: OkHttpClient,
    private val dispatchers: AppDispatchers,
) {
    suspend fun getEntries(): Result<EntriesResponse> = withContext(dispatchers.io) {
        runCatching { executeWithRetry(getRequest("entries"), EntriesResponse.serializer()) }
    }

    suspend fun postEntries(entries: List<EntryDto>): Result<EntriesResponse> = withContext(dispatchers.io) {
        runCatching {
            executeWithRetry(
                postRequest("entries", EntriesRequest(entries), EntriesRequest.serializer()),
                EntriesResponse.serializer(),
            )
        }
    }

    suspend fun addToLibrary(url: String, title: String, coverUrl: String?): Result<LibraryAddResponse> =
        withContext(dispatchers.io) {
            runCatching {
                val body = LibraryAddRequest(url, title, coverUrl)
                executeWithRetry(postRequest("library", body, LibraryAddRequest.serializer()), LibraryAddResponse.serializer())
            }
        }

    suspend fun getProgress(mangaUrl: String): Result<ProgressResponse> = withContext(dispatchers.io) {
        runCatching {
            val request = getRequest("progress") { addQueryParameter("url", mangaUrl) }
            executeWithRetry(request, ProgressResponse.serializer())
        }
    }

    // Batch pull -- omitting ?url= returns every progress row for the user in one call,
    // instead of one GET /progress per favorite (see pull() in SyncClient for why that
    // used to blow through the rate limiter on any real-size library).
    suspend fun getAllProgress(): Result<ProgressResponse> = withContext(dispatchers.io) {
        runCatching { executeWithRetry(getRequest("progress"), ProgressResponse.serializer()) }
    }

    suspend fun postProgress(entries: List<ProgressDto>): Result<ProgressResponse> = withContext(dispatchers.io) {
        runCatching {
            executeWithRetry(
                postRequest("progress", ProgressRequest(entries), ProgressRequest.serializer()),
                ProgressResponse.serializer(),
            )
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

    private suspend fun <T> executeWithRetry(request: Request, serializer: KSerializer<T>): T {
        repeat(RATE_LIMIT_MAX_RETRIES) {
            try {
                return execute(request, serializer)
            } catch (e: RateLimitedException) {
                delay(RATE_LIMIT_RETRY_DELAY_MS)
            }
        }
        return execute(request, serializer)
    }

    private fun <T> execute(request: Request, serializer: KSerializer<T>): T {
        client.newCall(request).execute().use { response ->
            if (response.code == 429) throw RateLimitedException()
            if (!response.isSuccessful) {
                error(errorMessageFor(response))
            }
            return networkJson.decodeFromString(serializer, response.body.string())
        }
    }
}
