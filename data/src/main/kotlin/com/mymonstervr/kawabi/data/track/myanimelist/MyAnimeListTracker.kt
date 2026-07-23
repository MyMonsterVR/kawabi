package com.mymonstervr.kawabi.data.track.myanimelist

import android.net.Uri
import com.mymonstervr.kawabi.data.network.TrackerTokenStore
import com.mymonstervr.kawabi.data.track.Tracker
import com.mymonstervr.kawabi.data.track.TrackerProfile
import com.mymonstervr.kawabi.data.track.trackerJson

/**
 * Account-level MyAnimeList connection (Settings -> Tracking services).
 * Browser PKCE OAuth: [authUrl] is opened externally (Custom Tab/browser),
 * MAL redirects back to `kawabi://myanimelist-auth?code=...`, and
 * [MainActivity][com.mymonstervr.kawabi.app.MainActivity] hands that code to
 * [exchangeCode].
 */
class MyAnimeListTracker(
    private val api: MyAnimeListApi,
    private val interceptor: MyAnimeListInterceptor,
    private val tokenStore: TrackerTokenStore,
) : Tracker {

    override val id: String = TrackerTokenStore.TRACKER_MAL
    override val name: String = "MyAnimeList"

    override val userName: String?
        get() = tokenStore.getProfile(id)?.let(TrackerProfile::decode)?.userName

    fun authUrl(): Uri = api.authUrl()

    // `state` must be the value MAL's redirect echoed back -- verified against what
    // authUrl() minted before this ever calls getAccessToken (CSRF/login-CSRF guard,
    // flagged by security review: without it, another app registering the same
    // kawabi://myanimelist-auth scheme/host could hand a forged code+no-state intent
    // to MainActivity and get it silently exchanged).
    suspend fun exchangeCode(code: String, state: String?): Result<Unit> = runCatching {
        check(api.matchesState(state)) { "MyAnimeList login rejected (state mismatch)" }
        val oauth = api.getAccessToken(code)
        interceptor.setOAuth(oauth)
        try {
            val userName = api.getCurrentUser()
            tokenStore.saveProfile(id, trackerJson.encodeToString(TrackerProfile.serializer(), TrackerProfile(userName)))
        } catch (e: Exception) {
            // Don't leave a connected-but-nameless session behind if the follow-up
            // identity lookup fails right after a successful token exchange.
            interceptor.setOAuth(null)
            throw e
        }
    }

    override fun logout() {
        interceptor.setOAuth(null)
    }
}
