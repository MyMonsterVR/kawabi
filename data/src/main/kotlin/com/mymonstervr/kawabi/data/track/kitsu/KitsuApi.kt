package com.mymonstervr.kawabi.data.track.kitsu

import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.BuildConfig
import com.mymonstervr.kawabi.data.track.dto.KitsuAddMangaResult
import com.mymonstervr.kawabi.data.track.dto.KitsuCurrentUserResult
import com.mymonstervr.kawabi.data.track.dto.KitsuLibraryEntrySearchResult
import com.mymonstervr.kawabi.data.track.dto.KitsuMangaSearchResult
import com.mymonstervr.kawabi.data.track.dto.KitsuOAuth
import com.mymonstervr.kawabi.data.track.dto.TrackSearchResult
import com.mymonstervr.kawabi.data.track.trackerHttpClient
import com.mymonstervr.kawabi.data.track.trackerJson
import com.mymonstervr.kawabi.domain.model.TrackStatus
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl

data class KitsuUserInfo(val id: String, val name: String)

/** Existence + progress for an already-tracked manga, see [KitsuApi.findLibManga]. */
data class KitsuLinkedEntry(
    val libraryId: String,
    val chaptersRead: Double,
    val totalChapters: Double,
    val status: String,
    val score: Double,
)

// Kitsu's own status strings <-> kawabi's tracker-agnostic TrackStatus.
private fun kitsuStatus(canonical: String): String = when (canonical) {
    TrackStatus.COMPLETED -> "completed"
    TrackStatus.ON_HOLD -> "on_hold"
    TrackStatus.DROPPED -> "dropped"
    TrackStatus.PLAN_TO_READ -> "planned"
    else -> "current"
}

private fun canonicalStatus(kitsu: String): String = when (kitsu) {
    "completed" -> TrackStatus.COMPLETED
    "on_hold" -> TrackStatus.ON_HOLD
    "dropped" -> TrackStatus.DROPPED
    "planned" -> TrackStatus.PLAN_TO_READ
    else -> TrackStatus.READING
}

private val VND_JSON_MEDIA_TYPE = "application/vnd.api+json".toMediaType()
private const val VND_API_JSON = "application/vnd.api+json"

/**
 * Kitsu OAuth (password grant, no browser needed) + identity -- account-level
 * auth. Library search/sync is deferred to the per-manga tracker-linking
 * sheet (Phase B). `KITSU_CLIENT_ID`/`KITSU_CLIENT_SECRET` are
 * BuildConfig-backed (see data/build.gradle.kts) -- Kitsu has never
 * implemented per-app registration, so this is the single shared client
 * credential its own API docs publish for all third-party apps to use, not a
 * private secret of anyone's.
 */
class KitsuApi(
    private val interceptor: KitsuInterceptor,
    private val dispatchers: AppDispatchers,
) {
    private val client = trackerHttpClient
    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun login(username: String, password: String): KitsuOAuth = withContext(dispatchers.io) {
        val body = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .add("grant_type", "password")
            .add("client_id", BuildConfig.KITSU_CLIENT_ID)
            .add("client_secret", BuildConfig.KITSU_CLIENT_SECRET)
            .build()
        val request = Request.Builder().url(LOGIN_URL).post(body).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Kitsu login failed (HTTP ${response.code})" }
            trackerJson.decodeFromString(KitsuOAuth.serializer(), response.body.string())
        }
    }

    suspend fun getCurrentUser(): KitsuUserInfo = withContext(dispatchers.io) {
        val request = Request.Builder().url("${BASE_URL}users?filter[self]=true").get().build()
        authClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Kitsu user lookup failed (HTTP ${response.code})" }
            val user = trackerJson.decodeFromString(KitsuCurrentUserResult.serializer(), response.body.string()).data.first()
            KitsuUserInfo(id = user.id, name = user.attributes.name)
        }
    }

    // Deliberate simplification vs. the common approach of routing through
    // Algolia (a separate key-fetch round-trip first) -- Kitsu's own JSON:API
    // supports a plain text-filter search directly, which is all a v1
    // title-search needs.
    suspend fun search(query: String): List<TrackSearchResult> = withContext(dispatchers.io) {
        val url = "${BASE_URL}manga".toHttpUrl().newBuilder()
            .addQueryParameter("filter[text]", query)
            .addQueryParameter("page[limit]", "20")
            .build()
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Kitsu search failed (HTTP ${response.code})" }
            trackerJson.decodeFromString(KitsuMangaSearchResult.serializer(), response.body.string())
                .data
                .map {
                    TrackSearchResult(
                        remoteId = it.id,
                        title = it.attributes.canonicalTitle,
                        totalChapters = (it.attributes.chapterCount ?: 0).toDouble(),
                        coverUrl = it.attributes.posterImage?.original,
                    )
                }
        }
    }

    /**
     * Existing library entry for [remoteId] under [userId], or `null` if this manga
     * isn't on the user's Kitsu list yet (an empty `data` array on an otherwise-
     * successful response -- that IS the "not tracked" signal). A non-2xx response
     * is a real failure, not "not tracked" -- must throw rather than return null, or
     * callers (TrackerSyncClient's link/sync) would treat a transient error as
     * "nothing to preserve" and push a regressed count over the user's real Kitsu
     * progress (flagged by code review).
     */
    suspend fun findLibManga(remoteId: String, userId: String): KitsuLinkedEntry? = withContext(dispatchers.io) {
        val url = "${BASE_URL}library-entries".toHttpUrl().newBuilder()
            .addQueryParameter("filter[manga_id]", remoteId)
            .addQueryParameter("filter[user_id]", userId)
            .addQueryParameter("filter[kind]", "manga")
            .addQueryParameter("include", "manga")
            .build()
        val request = Request.Builder().url(url).get().build()
        authClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Kitsu library lookup failed (HTTP ${response.code})" }
            val result = trackerJson.decodeFromString(KitsuLibraryEntrySearchResult.serializer(), response.body.string())
            val entry = result.data.firstOrNull() ?: return@use null
            val manga = result.included.firstOrNull()
            KitsuLinkedEntry(
                libraryId = entry.id,
                chaptersRead = entry.attributes.progress.toDouble(),
                totalChapters = (manga?.attributes?.chapterCount ?: 0).toDouble(),
                status = canonicalStatus(entry.attributes.status),
                score = (entry.attributes.ratingTwenty ?: 0) / 2.0,
            )
        }
    }

    suspend fun addLibManga(remoteId: String, userId: String, chaptersRead: Double, status: String, score: Double): String =
        withContext(dispatchers.io) {
            val data = buildJsonObject {
                putJsonObject("data") {
                    put("type", "libraryEntries")
                    putJsonObject("attributes") {
                        put("status", kitsuStatus(status))
                        put("progress", chaptersRead.toInt())
                        if (score > 0) put("ratingTwenty", (score * 2).toInt())
                    }
                    putJsonObject("relationships") {
                        putJsonObject("user") { putJsonObject("data") { put("id", userId); put("type", "users") } }
                        putJsonObject("media") { putJsonObject("data") { put("id", remoteId); put("type", "manga") } }
                    }
                }
            }
            val request = Request.Builder()
                .url("${BASE_URL}library-entries")
                .headers(headersOf("Content-Type", VND_API_JSON))
                .post(data.toString().toRequestBody(VND_JSON_MEDIA_TYPE))
                .build()
            authClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Kitsu link failed (HTTP ${response.code})" }
                trackerJson.decodeFromString(KitsuAddMangaResult.serializer(), response.body.string()).data.id
            }
        }

    suspend fun updateLibManga(libraryId: String, chaptersRead: Double, status: String, score: Double): Unit =
        withContext(dispatchers.io) {
            val data = buildJsonObject {
                putJsonObject("data") {
                    put("type", "libraryEntries")
                    put("id", libraryId)
                    putJsonObject("attributes") {
                        put("status", kitsuStatus(status))
                        put("progress", chaptersRead.toInt())
                        if (score > 0) put("ratingTwenty", (score * 2).toInt())
                    }
                }
            }
            val request = Request.Builder()
                .url("${BASE_URL}library-entries/$libraryId")
                .headers(headersOf("Content-Type", VND_API_JSON))
                .patch(data.toString().toRequestBody(VND_JSON_MEDIA_TYPE))
                .build()
            authClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Kitsu update failed (HTTP ${response.code})" }
            }
        }

    companion object {
        private const val BASE_URL = "https://kitsu.app/api/edge/"
        private const val LOGIN_URL = "https://kitsu.app/api/oauth/token"

        // Called by the interceptor's own refreshToken (via chain.proceed) -- this
        // request IS what feeds the interceptor's auth state, so it can't itself
        // go through the (not-yet-refreshed) authClient.
        fun refreshTokenRequest(refreshToken: String): Request {
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", BuildConfig.KITSU_CLIENT_ID)
                .add("client_secret", BuildConfig.KITSU_CLIENT_SECRET)
                .build()
            return Request.Builder().url(LOGIN_URL).post(body).build()
        }
    }
}
