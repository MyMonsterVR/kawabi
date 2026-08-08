package com.mymonstervr.kawabi.data.network

import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.network.dto.AltTitlesResponse
import com.mymonstervr.kawabi.data.network.dto.BrowseResponse
import com.mymonstervr.kawabi.data.network.dto.MangaBatchRequest
import com.mymonstervr.kawabi.data.network.dto.MangaBatchResponse
import com.mymonstervr.kawabi.data.network.dto.MangaBatchResult
import com.mymonstervr.kawabi.data.network.dto.MangaResponse
import com.mymonstervr.kawabi.data.network.dto.MangaSourcesResponse
import com.mymonstervr.kawabi.data.network.dto.PageDto
import com.mymonstervr.kawabi.data.network.dto.SearchResponse
import com.mymonstervr.kawabi.data.network.dto.SetMangaSourceRequest
import com.mymonstervr.kawabi.data.network.dto.SetSourceToggleRequest
import com.mymonstervr.kawabi.data.network.dto.SourceTogglesResponse
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// Library refreshes (one GET /manga per favorite, or the batch fan-out below) reliably
// drain the backend's rate-limit burst on any real-size library -- see BackendApiClient's
// executeWithRetry doc for why every call in this class opts into retrying past a 429
// rather than silently failing (Result.failure, chapter list never updates for that manga).
// Retrying means every manga eventually succeeds, but a full refresh of a large library is
// still bounded by the sustained ~30/min rate: that's a deliberate server-side cost-control
// measure (Suwayomi/WARP egress isn't free), not something client-side concurrency can work
// around.
class SourceApi(
    client: OkHttpClient,
    dispatchers: AppDispatchers,
) : BackendApiClient(client, dispatchers) {
    suspend fun getManga(url: String): Result<MangaResponse> = withContext(dispatchers.io) {
        runCatching {
            val request = getRequest("manga") { addQueryParameter("url", url) }
            executeWithRetry(request, MangaResponse.serializer())
        }
    }

    // POST /manga/batch fans out server-side (Manga.Batch, internal 20s deadline) instead
    // of the client hitting GET /manga once per favorite -- a full-library refresh
    // otherwise drains the burst above and falls into the sustained 1-req/2s trickle (see
    // the retry comment above). Uses its own client with headroom past the server's 20s
    // deadline so a slow-but-still-responding batch isn't cut off by our own read timeout
    // right as the server would've answered.
    suspend fun getMangaBatch(urls: List<String>): Result<List<MangaBatchResult>> = withContext(dispatchers.io) {
        runCatching {
            val body = networkJson.encodeToString(MangaBatchRequest.serializer(), MangaBatchRequest(urls))
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder().url("$BASE_URL/manga/batch").post(body).build()
            executeWithRetry(request, MangaBatchResponse.serializer(), batchClient).results
        }
    }

    suspend fun getPages(source: String, chapterId: String): Result<List<PageDto>> = withContext(dispatchers.io) {
        runCatching {
            val request = getRequest("pages") {
                addQueryParameter("source", source)
                addQueryParameter("chapter_id", chapterId)
            }
            executeWithRetry(request, ListSerializer(PageDto.serializer()))
        }
    }

    // Same 10s-default-vs-server-fan-out race as getMangaSources below -- search fans
    // out across every enabled source server-side and can legitimately take just over
    // 10s (confirmed live: 10.01s/10.003s responses), which OkHttp's default read
    // timeout was cutting off right as the server would've answered.
    suspend fun search(query: String): Result<SearchResponse> = withContext(dispatchers.io) {
        runCatching {
            val request = getRequest("search") { addQueryParameter("q", query) }
            executeWithRetry(request, SearchResponse.serializer(), longReadClient)
        }
    }

    // Same fan-out-vs-timeout reasoning as search() -- a single-source listing is
    // normally fast, but reuses the long-read client for consistency and headroom.
    suspend fun browse(source: String, sort: String, page: Int): Result<BrowseResponse> = withContext(dispatchers.io) {
        runCatching {
            val request = getRequest("browse") {
                addQueryParameter("source", source)
                addQueryParameter("sort", sort)
                addQueryParameter("page", page.toString())
            }
            executeWithRetry(request, BrowseResponse.serializer(), longReadClient)
        }
    }

    suspend fun getSources(): Result<SourceTogglesResponse> = withContext(dispatchers.io) {
        runCatching { executeWithRetry(getRequest("sources") {}, SourceTogglesResponse.serializer()) }
    }

    // Backs the tracker-linking search dialog's alt-name suggestions -- a manga
    // named differently on MAL/Kitsu than on its source site is otherwise
    // unfindable by title search alone.
    suspend fun getAltTitles(title: String): Result<List<String>> = withContext(dispatchers.io) {
        runCatching {
            val request = getRequest("alt-titles") { addQueryParameter("title", title) }
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
            val request = getRequest("manga/sources") {
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

    // Headroom past Manga.Batch's own worst-case ~105s (fully-cold batch, see
    // mihon-sync-server's internal/handler/manga.go) -- mihon-sync-server/main.go's
    // WriteTimeout is 120s for the same reason.
    private val batchClient by lazy { client.newBuilder().readTimeout(110, TimeUnit.SECONDS).build() }
}
