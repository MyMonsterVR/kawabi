package com.mymonstervr.kawabi.data.track

import com.mymonstervr.kawabi.data.network.TrackerApi
import com.mymonstervr.kawabi.data.network.TrackerTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Registry of the app's external trackers. [loggedInTrackerIds] is the
 * reactive "which trackers is this account connected to" signal that both
 * Settings -> Tracking services and the per-title tracker-linking sheet's
 * [loggedInTrackers] filter depend on.
 *
 * [trackers] is injected as a plain list (see PLAN-tracker-abstraction.md) so
 * adding tracker N+1 is one Koin line rather than a constructor-signature
 * change rippling through every call site; nothing here knows MAL/Kitsu/AniList
 * by name, lookups go through [byId] / [oAuthTrackerFor].
 */
class TrackerManager(
    val trackers: List<Tracker>,
    private val trackerApi: TrackerApi,
    private val tokenStore: TrackerTokenStore,
    scope: CoroutineScope,
) {
    val loggedInTrackerIds: StateFlow<Set<String>> = tokenStore.loggedInTrackerIds

    fun loggedInTrackers(): List<Tracker> = trackers.filter { it.id in loggedInTrackerIds.value }

    fun byId(trackerId: String): Tracker? = trackers.firstOrNull { it.id == trackerId }

    /** The browser-OAuth tracker whose `kawabi://` redirect host is [redirectHost], if any. */
    fun oAuthTrackerFor(redirectHost: String): BrowserOAuthTracker? =
        trackers.filterIsInstance<BrowserOAuthTracker>().firstOrNull { it.redirectHost == redirectHost }

    init {
        scope.launch { refresh() }
    }

    suspend fun refresh() {
        runCatching { trackerApi.status() }.onSuccess(tokenStore::replaceAll)
    }
}
