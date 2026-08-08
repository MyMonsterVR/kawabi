package com.mymonstervr.kawabi.data.network

import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers

internal val JSON_MEDIA_TYPE = "application/json".toMediaType()

// Backend's JSON-endpoint limiter is a token bucket, 1 req/2s sustained refill after a
// 30-request burst (internal/middleware/ratelimit.go) -- shared by every endpoint that
// opts into executeWithRetry below. The 429 response's Retry-After header is hardcoded
// to 60s server-side (full bucket reset, not the actual per-token refill), which would
// make retrying feel like it hung -- retry against the real refill rate instead.
internal const val RATE_LIMIT_RETRY_DELAY_MS = 2_100L
internal const val RATE_LIMIT_MAX_RETRIES = 5

/** Shared GET/POST request-building and response-decoding plumbing for this app's backend API classes. */
abstract class BackendApiClient(
    protected val client: OkHttpClient,
    protected val dispatchers: AppDispatchers,
) {
    protected inline fun getRequest(path: String, block: okhttp3.HttpUrl.Builder.() -> Unit = {}): Request {
        val url = "$BASE_URL/$path".toHttpUrl().newBuilder().apply(block).build()
        return Request.Builder().url(url).get().build()
    }

    protected fun <T> postRequest(path: String, body: T, serializer: KSerializer<T>): Request {
        val requestBody = networkJson.encodeToString(serializer, body).toRequestBody(JSON_MEDIA_TYPE)
        return Request.Builder().url("$BASE_URL/$path").post(requestBody).build()
    }

    protected fun <T> execute(request: Request, serializer: KSerializer<T>, httpClient: OkHttpClient = client): T {
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(errorMessageFor(response))
            return networkJson.decodeFromString(serializer, response.body.string())
        }
    }

    /**
     * [execute], but retries on 429 up to [RATE_LIMIT_MAX_RETRIES] times before falling
     * through to a final plain [execute] call (which throws the normal error on a 429
     * exactly like an un-retried call would). Opt-in per endpoint -- some endpoints
     * (e.g. tracker calls) deliberately don't retry and should keep calling [execute] directly.
     */
    protected suspend fun <T> executeWithRetry(request: Request, serializer: KSerializer<T>, httpClient: OkHttpClient = client): T {
        repeat(RATE_LIMIT_MAX_RETRIES) {
            val response = httpClient.newCall(request).execute()
            if (response.code == 429) {
                response.close()
                delay(RATE_LIMIT_RETRY_DELAY_MS)
                return@repeat
            }
            response.use {
                if (!it.isSuccessful) error(errorMessageFor(it))
                return networkJson.decodeFromString(serializer, it.body.string())
            }
        }
        return execute(request, serializer, httpClient)
    }
}
