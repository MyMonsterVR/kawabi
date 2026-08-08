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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

// Pulling per-chapter progress has no batch alternative (GET /progress?url= is one manga
// at a time) -- a library much past ~30 entries reliably drains the backend's rate-limit
// burst mid-pull on every sync, so every call here opts into BackendApiClient's retry.
class SyncApi(
    client: OkHttpClient,
    dispatchers: AppDispatchers,
) : BackendApiClient(client, dispatchers) {
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

}
