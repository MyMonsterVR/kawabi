package com.mymonstervr.kawabi.data.track

import com.mymonstervr.kawabi.data.network.TrackerApi
import com.mymonstervr.kawabi.data.network.TrackerTokenStore
import com.mymonstervr.kawabi.data.track.kitsu.KitsuTracker
import com.mymonstervr.kawabi.data.track.myanimelist.MyAnimeListTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Registry of the app's external trackers. [loggedInTrackerIds] is the
 * reactive "which trackers is this account connected to" signal that both
 * Settings -> Tracking services and (Phase B) the per-manga tracker-linking
 * sheet's `loggedInTrackers()` filter depend on.
 */
class TrackerManager(
    val myAnimeList: MyAnimeListTracker,
    val kitsu: KitsuTracker,
    private val trackerApi: TrackerApi,
    private val tokenStore: TrackerTokenStore,
    scope: CoroutineScope,
) {
    val trackers: List<Tracker> = listOf(myAnimeList, kitsu)

    val loggedInTrackerIds: StateFlow<Set<String>> = tokenStore.loggedInTrackerIds

    fun loggedInTrackers(): List<Tracker> = trackers.filter { it.id in loggedInTrackerIds.value }

    init {
        scope.launch { refresh() }
    }

    suspend fun refresh() {
        runCatching { trackerApi.status() }.onSuccess(tokenStore::replaceAll)
    }
}
