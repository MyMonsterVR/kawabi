package com.mymonstervr.kawabi.data.network

import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.network.dto.AnimeBatchRequest
import com.mymonstervr.kawabi.data.network.dto.AnimeBatchResponse
import com.mymonstervr.kawabi.data.network.dto.AnimeBrowseResponse
import com.mymonstervr.kawabi.data.network.dto.AnimeDetailResponse
import com.mymonstervr.kawabi.data.network.dto.AnimeEntriesRequest
import com.mymonstervr.kawabi.data.network.dto.AnimeEntriesResponse
import com.mymonstervr.kawabi.data.network.dto.AnimeEntryDto
import com.mymonstervr.kawabi.data.network.dto.AnimeImportRequest
import com.mymonstervr.kawabi.data.network.dto.AnimeImportResponse
import com.mymonstervr.kawabi.data.network.dto.AnimeProgressDto
import com.mymonstervr.kawabi.data.network.dto.AnimeProgressRequest
import com.mymonstervr.kawabi.data.network.dto.AnimeProgressResponse
import com.mymonstervr.kawabi.data.network.dto.AnimeSearchResponse
import com.mymonstervr.kawabi.data.network.dto.AnimeSourcesResponse
import com.mymonstervr.kawabi.data.network.dto.HosterDto
import com.mymonstervr.kawabi.data.network.dto.HosterVideosDto
import com.mymonstervr.kawabi.data.network.dto.SetAnimeSourceToggleRequest
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * The `/anime/` half of the backend (PLAN-anime.md section 10). Deliberately a separate
 * class from [SourceApi]/[SyncApi] rather than extra methods on them -- decision D8's
 * parallel stack, so nothing here can regress a manga call path.
 *
 * Timeouts mirror the manga side's reasoning: catalog calls fan out server-side and sit
 * just past OkHttp's 10s default, and `/anime/videos` is documented as 5-20s (multiple
 * hoster round trips inside the engine), so it gets its own much longer client.
 */
class AnimeApi(
    client: OkHttpClient,
    dispatchers: AppDispatchers,
) : BackendApiClient(client, dispatchers) {

    suspend fun getSources(): Result<AnimeSourcesResponse> = withContext(dispatchers.io) {
        runCatching { executeWithRetry(getRequest("anime/sources") {}, AnimeSourcesResponse.serializer(), longReadClient) }
    }

    suspend fun setSourceEnabled(key: String, enabled: Boolean): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val body = networkJson.encodeToString(
                SetAnimeSourceToggleRequest.serializer(),
                SetAnimeSourceToggleRequest(key, enabled),
            ).toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder().url("$BASE_URL/anime/sources").put(body).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error(errorMessageFor(response))
            }
        }
    }

    suspend fun browse(source: String, sort: String, page: Int): Result<AnimeBrowseResponse> = withContext(dispatchers.io) {
        runCatching {
            val request = getRequest("anime/browse") {
                addQueryParameter("source", source)
                addQueryParameter("sort", sort)
                addQueryParameter("page", page.toString())
            }
            executeWithRetry(request, AnimeBrowseResponse.serializer(), longReadClient)
        }
    }

    suspend fun search(query: String): Result<AnimeSearchResponse> = withContext(dispatchers.io) {
        runCatching {
            val request = getRequest("anime/search") { addQueryParameter("q", query) }
            executeWithRetry(request, AnimeSearchResponse.serializer(), longReadClient)
        }
    }

    suspend fun getAnime(key: String): Result<AnimeDetailResponse> = withContext(dispatchers.io) {
        runCatching {
            val request = getRequest("anime") { addQueryParameter("key", key) }
            executeWithRetry(request, AnimeDetailResponse.serializer(), longReadClient)
        }
    }

    /**
     * `POST /anime/batch` fans out the per-key `GET /anime` fetches server-side (mirrors
     * [SourceApi.getMangaBatch]) instead of one call per anime hitting the sustained 1-req/2s
     * limiter -- see PLAN-anime.md section 14/import-batch. Chunked at 100 keys per the
     * backend's documented cap; errors for individual keys come back in the same response
     * rather than failing the whole batch.
     */
    suspend fun getAnimeBatch(keys: List<String>): Result<AnimeBatchResponse> = withContext(dispatchers.io) {
        runCatching {
            val animes = mutableListOf<AnimeDetailResponse>()
            val errors = mutableMapOf<String, String>()
            for (chunk in keys.chunked(100)) {
                val request = postRequest("anime/batch", AnimeBatchRequest(chunk), AnimeBatchRequest.serializer())
                val response = executeWithRetry(request, AnimeBatchResponse.serializer(), batchClient)
                animes += response.animes
                errors += response.errors
            }
            AnimeBatchResponse(animes, errors)
        }
    }

    suspend fun getHosters(episodeKey: String): Result<List<HosterDto>> = withContext(dispatchers.io) {
        runCatching {
            val request = getRequest("anime/hosters") { addQueryParameter("episode", episodeKey) }
            executeWithRetry(request, ListSerializer(HosterDto.serializer()), videoClient)
        }
    }

    suspend fun getVideos(episodeKey: String, hosterIndex: Int? = null): Result<List<HosterVideosDto>> =
        withContext(dispatchers.io) {
            runCatching {
                val request = getRequest("anime/videos") {
                    addQueryParameter("episode", episodeKey)
                    hosterIndex?.let { addQueryParameter("hoster", it.toString()) }
                }
                executeWithRetry(request, ListSerializer(HosterVideosDto.serializer()), videoClient)
            }
        }

    suspend fun getEntries(): Result<AnimeEntriesResponse> = withContext(dispatchers.io) {
        runCatching { executeWithRetry(getRequest("anime/entries"), AnimeEntriesResponse.serializer()) }
    }

    suspend fun postEntries(entries: List<AnimeEntryDto>): Result<AnimeEntriesResponse> = withContext(dispatchers.io) {
        runCatching {
            executeWithRetry(
                postRequest("anime/entries", AnimeEntriesRequest(entries), AnimeEntriesRequest.serializer()),
                AnimeEntriesResponse.serializer(),
            )
        }
    }

    suspend fun deleteEntry(key: String): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val url = "$BASE_URL/anime/entries".toHttpUrl().newBuilder()
                .addQueryParameter("key", key)
                .build()
            val request = Request.Builder().url(url).delete().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error(errorMessageFor(response))
            }
        }
    }

    /** Every progress row for the user when [key] is null, one anime's rows otherwise. */
    suspend fun getProgress(key: String? = null): Result<AnimeProgressResponse> = withContext(dispatchers.io) {
        runCatching {
            val request = getRequest("anime/progress") { key?.let { addQueryParameter("key", it) } }
            executeWithRetry(request, AnimeProgressResponse.serializer())
        }
    }

    suspend fun postProgress(entries: List<AnimeProgressDto>): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val request = postRequest("anime/progress", AnimeProgressRequest(entries), AnimeProgressRequest.serializer())
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error(errorMessageFor(response))
            }
        }
    }

    /**
     * Tracker-list import: the backend fetches the whole MAL/Kitsu list and then title-searches
     * every item across the anime sources, with a documented total cap of 120s before it gives up
     * and answers `truncated` (PLAN-anime.md section 14) -- so the client has to outwait that cap.
     */
    suspend fun importFromTracker(tracker: String, statuses: List<String>): Result<AnimeImportResponse> =
        withContext(dispatchers.io) {
            runCatching {
                val request = postRequest("anime/import", AnimeImportRequest(tracker, statuses), AnimeImportRequest.serializer())
                executeWithRetry(request, AnimeImportResponse.serializer(), importClient)
            }
        }

    private val longReadClient by lazy { client.newBuilder().readTimeout(20, TimeUnit.SECONDS).build() }

    // /anime/videos walks every hoster's extractor inside the engine (documented 5-20s,
    // and the engine's own HTTP timeout is 90s) -- anything near the 20s catalog client
    // would cut off a perfectly healthy extraction.
    private val videoClient by lazy {
        client.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(100, TimeUnit.SECONDS)
            .build()
    }

    private val importClient by lazy { client.newBuilder().readTimeout(150, TimeUnit.SECONDS).build() }

    private val batchClient by lazy { client.newBuilder().readTimeout(150, TimeUnit.SECONDS).build() }
}
