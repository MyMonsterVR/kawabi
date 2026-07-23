package com.mymonstervr.kawabi.data.track

/**
 * Account-level external tracker (MAL, Kitsu). Login is tracker-specific
 * (browser PKCE for MAL, in-app username/password for Kitsu), so it's not
 * part of this interface -- see [com.mymonstervr.kawabi.data.track.myanimelist.MyAnimeListTracker]
 * / [com.mymonstervr.kawabi.data.track.kitsu.KitsuTracker]. [TrackerManager.loggedInTrackerIds]
 * is the reactive "is this tracker connected" signal.
 */
interface Tracker {
    val id: String
    val name: String
    val userName: String?
    fun logout()
}
