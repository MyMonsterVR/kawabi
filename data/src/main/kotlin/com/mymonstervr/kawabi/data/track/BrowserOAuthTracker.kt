package com.mymonstervr.kawabi.data.track

import android.net.Uri

/**
 * Tracker whose login happens in an external browser and comes back as a
 * `kawabi://<redirectHost>?code=...` deep link (MAL, AniList -- Kitsu is an
 * in-app email/password form instead). Exists so neither
 * [com.mymonstervr.kawabi.app.MainActivity] nor the Tracking settings screen
 * needs a per-tracker branch: the redirect is routed by matching [redirectHost]
 * against the registered trackers, and the settings row just asks for
 * [authUrl].
 */
interface BrowserOAuthTracker : Tracker {
    /** Host part of this tracker's `kawabi://` redirect, as registered in AndroidManifest.xml. */
    val redirectHost: String

    fun authUrl(): Uri

    /** [receivedState] is the `state` echoed back by the redirect; a mismatch must fail. */
    suspend fun exchangeCode(code: String, receivedState: String?): Result<Unit>
}
