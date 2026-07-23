package com.mymonstervr.kawabi.data.track.myanimelist

import android.net.Uri
import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.data.BuildConfig
import com.mymonstervr.kawabi.data.track.PkceUtil
import com.mymonstervr.kawabi.data.track.dto.MALOAuth
import com.mymonstervr.kawabi.data.track.dto.MALUser
import com.mymonstervr.kawabi.data.track.dto.MalListItem
import com.mymonstervr.kawabi.data.track.dto.MalSearchResult
import com.mymonstervr.kawabi.data.track.dto.TrackSearchResult
import com.mymonstervr.kawabi.data.track.trackerHttpClient
import com.mymonstervr.kawabi.data.track.trackerJson
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** Existence + progress for an already-tracked manga, see [MyAnimeListApi.findListItem]. */
data class MalListStatus(val chaptersRead: Double, val totalChapters: Double, val status: String, val score: Double)

/**
 * MyAnimeList OAuth (browser PKCE) + identity -- account-level auth for
 * Settings -> Tracking services. Search/list sync is deferred to the
 * per-manga tracker-linking sheet (Phase B). `MAL_CLIENT_ID` is
 * BuildConfig-backed (see data/build.gradle.kts) -- kawabi's own registered
 * MAL app, configured via local.properties/CI secret, never committed.
 */
class MyAnimeListApi(
    private val interceptor: MyAnimeListInterceptor,
    private val dispatchers: AppDispatchers,
) {
    private val client = trackerHttpClient
    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    // MAL's PKCE only supports the "plain" code_challenge_method (challenge ==
    // verifier), not S256 -- not an oversight. Stashed here since
    // getAccessToken needs the same verifier the user's browser session was
    // started with.
    private var codeVerifier: String = ""

    // CSRF/login-CSRF guard: a redirect that doesn't carry back the exact state
    // this instance minted is rejected before ever calling getAccessToken (flagged
    // by security review) -- otherwise a malicious app registering the same
    // kawabi://myanimelist-auth scheme/host could hand MainActivity an attacker's
    // own authorization code and get it silently bound to this device's session.
    private var state: String = ""

    fun authUrl(): Uri {
        codeVerifier = PkceUtil.generateCodeVerifier()
        state = PkceUtil.generateCodeVerifier()
        return Uri.parse("$BASE_OAUTH_URL/authorize").buildUpon()
            .appendQueryParameter("client_id", BuildConfig.MAL_CLIENT_ID)
            .appendQueryParameter("code_challenge", codeVerifier)
            .appendQueryParameter("code_challenge_method", "plain")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("state", state)
            .build()
    }

    fun matchesState(receivedState: String?): Boolean = state.isNotEmpty() && state == receivedState

    suspend fun getAccessToken(authCode: String): MALOAuth = withContext(dispatchers.io) {
        val body = FormBody.Builder()
            .add("client_id", BuildConfig.MAL_CLIENT_ID)
            .add("code", authCode)
            .add("code_verifier", codeVerifier)
            .add("grant_type", "authorization_code")
            .build()
        val request = Request.Builder().url("$BASE_OAUTH_URL/token").post(body).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "MAL token exchange failed (HTTP ${response.code})" }
            trackerJson.decodeFromString(MALOAuth.serializer(), response.body.string())
        }
    }

    suspend fun getCurrentUser(): String = withContext(dispatchers.io) {
        val request = Request.Builder().url("$BASE_API_URL/users/@me").get().build()
        authClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "MAL user lookup failed (HTTP ${response.code})" }
            trackerJson.decodeFromString(MALUser.serializer(), response.body.string()).name
        }
    }

    suspend fun search(query: String): List<TrackSearchResult> = withContext(dispatchers.io) {
        val url = "$BASE_API_URL/manga".toHttpUrl().newBuilder()
            // MAL API 400s on queries over 64 characters.
            .addQueryParameter("q", query.take(64))
            .addQueryParameter("fields", "id,title,num_chapters,main_picture,media_type")
            .build()
        val request = Request.Builder().url(url).get().build()
        authClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "MAL search failed (HTTP ${response.code})" }
            trackerJson.decodeFromString(MalSearchResult.serializer(), response.body.string())
                .data
                .map { it.node }
                .filter { !it.mediaType.contains("novel") }
                .map {
                    TrackSearchResult(
                        remoteId = it.id.toString(),
                        title = it.title,
                        totalChapters = it.numChapters.toDouble(),
                        coverUrl = it.mainPicture?.large?.takeIf(String::isNotBlank),
                    )
                }
        }
    }

    /**
     * Existing list entry for [remoteId], or `null` if this manga isn't on the user's
     * MAL list yet ([MalListItem.myListStatus] absent on an otherwise-successful
     * response -- that field being missing IS the "not tracked" signal). A non-2xx
     * response is a real failure (auth/network/5xx), not "not tracked" -- this must
     * throw rather than return null, or callers (TrackerSyncClient's link/sync) would
     * treat a transient error as "nothing to preserve" and push a regressed count
     * over whatever the user actually has on MAL (flagged by code review).
     */
    suspend fun findListItem(remoteId: String): MalListStatus? = withContext(dispatchers.io) {
        val url = "$BASE_API_URL/manga/$remoteId".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "num_chapters,my_list_status{num_chapters_read,status,score}")
            .build()
        val request = Request.Builder().url(url).get().build()
        authClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "MAL list lookup failed (HTTP ${response.code})" }
            val item = trackerJson.decodeFromString(MalListItem.serializer(), response.body.string())
            item.myListStatus?.let { MalListStatus(it.numChaptersRead, item.numChapters.toDouble(), it.status, it.score.toDouble()) }
        }
    }

    /** Creates or updates (MAL's PUT is an upsert) the list entry for [remoteId]. */
    suspend fun upsertListItem(remoteId: String, chaptersRead: Double, status: String, score: Double): Unit = withContext(dispatchers.io) {
        val body = FormBody.Builder()
            .add("status", status)
            .add("num_chapters_read", chaptersRead.toInt().toString())
            .add("score", score.toInt().toString())
            .build()
        val request = Request.Builder().url("$BASE_API_URL/manga/$remoteId/my_list_status").put(body).build()
        authClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "MAL list update failed (HTTP ${response.code})" }
        }
    }

    companion object {
        private const val BASE_OAUTH_URL = "https://myanimelist.net/v1/oauth2"
        private const val BASE_API_URL = "https://api.myanimelist.net/v2"

        // Called by the interceptor's own refreshToken (via chain.proceed), which is
        // why the Authorization header is added manually rather than relying on the
        // interceptor -- this request IS what feeds the interceptor's auth state.
        fun refreshTokenRequest(oauth: MALOAuth): Request {
            val body = FormBody.Builder()
                .add("client_id", BuildConfig.MAL_CLIENT_ID)
                .add("refresh_token", oauth.refreshToken)
                .add("grant_type", "refresh_token")
                .build()
            val headers = Headers.Builder().add("Authorization", "Bearer ${oauth.accessToken}").build()
            return Request.Builder().url("$BASE_OAUTH_URL/token").post(body).headers(headers).build()
        }
    }
}
