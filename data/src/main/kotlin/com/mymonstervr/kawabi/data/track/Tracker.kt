package com.mymonstervr.kawabi.data.track

/**
 * Account-level external tracker (MAL, Kitsu, AniList). Login is tracker-specific, so it's
 * not part of this interface: the browser-redirect ones implement [BrowserOAuthTracker],
 * Kitsu has its own in-app email/password
 * [login][com.mymonstervr.kawabi.data.track.kitsu.KitsuTracker.login].
 * [TrackerManager.loggedInTrackerIds] is the reactive "is this tracker connected" signal.
 */
interface Tracker {
    val id: String
    val name: String
    val userName: String?
    fun logout()
}
