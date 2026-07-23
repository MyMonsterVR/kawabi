package com.mymonstervr.kawabi.data.network

import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class AppReleaseDto(
    val version: String,
    val info: String = "",
    @SerialName("download_url")
    val downloadUrl: String,
    @SerialName("commit_count")
    val commitCount: Int,
)

// Bare client, not the app's backend-authenticated one -- this manifest lives at
// apk.rasmushk.dk, unrelated to kawabi-server, so AuthInterceptor's session-token
// header has no business being sent here.
private val plainHttpClient = OkHttpClient()
private val releaseJson = Json { ignoreUnknownKeys = true }

/**
 * Reads the update manifest CI publishes on every push to main (see
 * `.github/workflows/build.yml`). `/v2/` -- distinct from the old fork's `/v1/`
 * path on the same domain, so neither app's release clobbers the other's.
 */
class AppReleaseApi(private val dispatchers: AppDispatchers) {
    suspend fun latest(): AppReleaseDto? = withContext(dispatchers.io) {
        runCatching {
            plainHttpClient.newCall(Request.Builder().url(MANIFEST_URL).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                releaseJson.decodeFromString(AppReleaseDto.serializer(), response.body.string())
            }
        }.getOrNull()
    }

    companion object {
        private const val MANIFEST_URL = "https://apk.rasmushk.dk/v2/manifest.json"
    }
}
