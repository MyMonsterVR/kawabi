package com.mymonstervr.kawabi.data.track.anilist

import android.net.Uri
import com.mymonstervr.kawabi.data.BuildConfig
import com.mymonstervr.kawabi.data.network.TrackerApi
import com.mymonstervr.kawabi.data.network.TrackerTokenStore
import com.mymonstervr.kawabi.data.track.AccountTracker
import com.mymonstervr.kawabi.data.track.BrowserOAuthTracker
import com.mymonstervr.kawabi.data.track.PkceUtil
import kotlinx.coroutines.CoroutineScope

/**
 * Account-level AniList connection (Settings -> Tracking services), covering both the
 * manga and anime axes (PLAN-anime.md section 15). Plain OAuth2 authorization-code flow --
 * no PKCE, since the code-for-token exchange happens server-side with the client secret
 * the backend holds, and the app never talks to AniList directly.
 */
class AniListTracker(
    trackerApi: TrackerApi,
    tokenStore: TrackerTokenStore,
    scope: CoroutineScope,
) : AccountTracker(trackerApi, tokenStore, scope), BrowserOAuthTracker {

    override val id: String = TrackerTokenStore.TRACKER_ANILIST
    override val name: String = "AniList"
    override val redirectHost: String = REDIRECT_HOST

    // Same CSRF/login-CSRF guard as MAL's: a redirect that doesn't echo back the exact
    // state this instance minted is rejected before the code ever reaches the backend,
    // so another app registering the same kawabi://anilist-auth scheme/host can't get
    // its own authorization code bound to this device's session.
    private var state: String = ""

    override fun authUrl(): Uri {
        state = PkceUtil.generateCodeVerifier()
        return Uri.parse("$BASE_OAUTH_URL/authorize").buildUpon()
            .appendQueryParameter("client_id", BuildConfig.ANILIST_CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("state", state)
            .build()
    }

    override suspend fun exchangeCode(code: String, receivedState: String?): Result<Unit> = runCatching {
        check(state.isNotEmpty() && state == receivedState) { "AniList login rejected (state mismatch)" }
        val userName = trackerApi.connectAniList(code)
        tokenStore.saveProfile(id, userName)
    }

    private companion object {
        const val BASE_OAUTH_URL = "https://anilist.co/api/v2/oauth"
        const val REDIRECT_HOST = "anilist-auth"
        const val REDIRECT_URI = "kawabi://$REDIRECT_HOST"
    }
}
