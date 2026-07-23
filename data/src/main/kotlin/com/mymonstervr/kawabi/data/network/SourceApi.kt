package com.mymonstervr.kawabi.data.network

import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.network.dto.AltTitlesResponse
import com.mymonstervr.kawabi.data.network.dto.MangaResponse
import com.mymonstervr.kawabi.data.network.dto.MangaSourcesResponse
import com.mymonstervr.kawabi.data.network.dto.PageDto
import com.mymonstervr.kawabi.data.network.dto.SearchResponse
import com.mymonstervr.kawabi.data.network.dto.SetMangaSourceRequest
import com.mymonstervr.kawabi.data.network.dto.SetSourceToggleRequest
import com.mymonstervr.kawabi.data.network.dto.SourceTogglesResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

// Same reasoning as SyncApi.kt's RATE_LIMIT_RETRY_DELAY_MS -- the backend's JSON-endpoint
// limiter (internal/middleware/ratelimit.go) is a token bucket, 1 req/2s sustained refill
// after a 30-request burst. A library refresh (one GET /manga per favorite) drains the
// burst almost immediately once past ~30 manga, and without a retry those requests just
// silently fail (Result.failure, chapter list never updates for that manga) rather than
// visibly erroring -- worse than being slow. Retrying means every manga eventually
// succeeds, but a full refresh of a large library is still bounded by the sustained rate
// (~30/min): that's a deliberate server-side cost-control measure (Suwayomi/WARP egress
// isn't free), not something client-side concurrency can work around.
private const val RATE_LIMIT_RETRY_DELAY_MS = 2_100L
private const val RATE_LIMIT_MAX_RETRIES = 5

private class SourceRateLimitedException : Exception()

class SourceApi(
    private val client: OkHttpClient,
    private val dispatchers: AppDispatchers,
) {
    suspend fun getManga(url: String): Result<MangaResponse> = withContext(dispatchers.io) {
        runCatching {
            val request = requestFor("manga") { addQueryParameter("url", url) }
            executeWithRetry(request, MangaResponse.serializer())
        }
    }

    suspend fun getPages(source: String, chapterId: String): Result<List<PageDto>> = withContext(dispatchers.io) {
        runCatching {
            val request = requestFor("pages") {
                addQueryParameter("source", source)
                addQueryParameter("chapter_id", chapterId)
            }
            executeWithRetry(request, ListSerializer(PageDto.serializer()))
        }
    }

    suspend fun search(query: String): Result<SearchResponse> = withContext(dispatchers.io) {
        runCatching {
            val request = requestFor("search") { addQueryParameter("q", query) }
            executeWithRetry(request, SearchResponse.serializer())
        }
    }

    suspend fun getSources(): Result<SourceTogglesResponse> = withContext(dispatchers.io) {
        runCatching { executeWithRetry(requestFor("sources") {}, SourceTogglesResponse.serializer()) }
    }

    // Backs the tracker-linking search dialog's alt-name suggestions -- a manga
    // named differently on MAL/Kitsu than on its source site is otherwise
    // unfindable by title search alone.
    suspend fun getAltTitles(title: String): Result<List<String>> = withContext(dispatchers.io) {
        runCatching {
            val request = requestFor("alt-titles") { addQueryParameter("title", title) }
            executeWithRetry(request, AltTitlesResponse.serializer()).titles
        }
    }

    suspend fun setSourceEnabled(key: String, enabled: Boolean): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val body = networkJson.encodeToString(SetSourceToggleRequest.serializer(), SetSourceToggleRequest(key, enabled))
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder().url("$BASE_URL/sources").put(body).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error(errorMessageFor(response))
            }
        }
    }

    // Server-side probes every enabled site concurrently (siteProbeTimeout = 15s in
    // internal/handler/mangasource.go) -- OkHttp's default 10s read timeout would cut
    // that off before the server ever responds, so this uses a longer-lived client just
    // for this one call.
    suspend fun getMangaSources(url: String, title: String): Result<MangaSourcesResponse> = withContext(dispatchers.io) {
        runCatching {
            val request = requestFor("manga/sources") {
                addQueryParameter("url", url)
                if (title.isNotBlank()) addQueryParameter("title", title)
            }
            longReadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error(errorMessageFor(response))
                networkJson.decodeFromString(MangaSourcesResponse.serializer(), response.body.string())
            }
        }
    }

    suspend fun setMangaSource(url: String, siteKey: String, title: String): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val body = networkJson.encodeToString(
                SetMangaSourceRequest.serializer(),
                SetMangaSourceRequest(url, siteKey, title.takeIf { it.isNotBlank() }),
            ).toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder().url("$BASE_URL/manga/source").put(body).build()
            longReadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error(errorMessageFor(response))
            }
        }
    }

    private val longReadClient by lazy { client.newBuilder().readTimeout(20, TimeUnit.SECONDS).build() }

    private inline fun requestFor(path: String, block: okhttp3.HttpUrl.Builder.() -> Unit): Request {
        val url = "$BASE_URL/$path".toHttpUrl().newBuilder().apply(block).build()
        return Request.Builder().url(url).get().build()
    }

    private suspend fun <T> executeWithRetry(request: Request, serializer: KSerializer<T>): T {
        repeat(RATE_LIMIT_MAX_RETRIES) {
            try {
                return execute(request, serializer)
            } catch (e: SourceRateLimitedException) {
                delay(RATE_LIMIT_RETRY_DELAY_MS)
            }
        }
        return execute(request, serializer)
    }

    private fun <T> execute(request: Request, serializer: KSerializer<T>): T {
        client.newCall(request).execute().use { response ->
            if (response.code == 429) throw SourceRateLimitedException()
            if (!response.isSuccessful) {
                error(errorMessageFor(response))
            }
            return networkJson.decodeFromString(serializer, response.body.string())
        }
    }
}
