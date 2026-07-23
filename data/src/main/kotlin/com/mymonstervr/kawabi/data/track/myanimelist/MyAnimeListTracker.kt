package com.mymonstervr.kawabi.data.track.myanimelist

import android.net.Uri
import com.mymonstervr.kawabi.data.BuildConfig
import com.mymonstervr.kawabi.data.network.TrackerApi
import com.mymonstervr.kawabi.data.network.TrackerTokenStore
import com.mymonstervr.kawabi.data.track.PkceUtil
import com.mymonstervr.kawabi.data.track.Tracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Account-level MyAnimeList connection (Settings -> Tracking services).
 * Browser PKCE OAuth: [authUrl] is opened externally (Custom Tab/browser),
 * MAL redirects back to `kawabi://myanimelist-auth?code=...`, and
 * [MainActivity][com.mymonstervr.kawabi.app.MainActivity] hands that code to
 * [exchangeCode]. Token exchange/refresh itself happens server-side --
 * this class only mints the PKCE challenge and hands the app's own backend
 * the authorization code.
 */
class MyAnimeListTracker(
    private val trackerApi: TrackerApi,
    private val tokenStore: TrackerTokenStore,
    private val scope: CoroutineScope,
) : Tracker {

    override val id: String = TrackerTokenStore.TRACKER_MAL
    override val name: String = "MyAnimeList"

    override val userName: String?
        get() = tokenStore.getProfile(id)

    // MAL's PKCE only supports the "plain" code_challenge_method (challenge ==
    // verifier), not S256 -- not an oversight. Stashed here since exchangeCode
    // needs the same verifier the user's browser session was started with.
    private var codeVerifier: String = ""

    // CSRF/login-CSRF guard: a redirect that doesn't carry back the exact state
    // this instance minted is rejected before ever calling connectMal (flagged
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

    suspend fun exchangeCode(code: String, receivedState: String?): Result<Unit> = runCatching {
        check(state.isNotEmpty() && state == receivedState) { "MyAnimeList login rejected (state mismatch)" }
        val userName = trackerApi.connectMal(code, codeVerifier)
        tokenStore.saveProfile(id, userName)
    }

    override fun logout() {
        tokenStore.clearProfile(id)
        scope.launch { runCatching { trackerApi.disconnect(id) } }
    }

    private companion object {
        const val BASE_OAUTH_URL = "https://myanimelist.net/v1/oauth2"
    }
}
