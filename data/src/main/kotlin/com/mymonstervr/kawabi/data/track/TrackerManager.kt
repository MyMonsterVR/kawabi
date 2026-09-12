package com.mymonstervr.kawabi.data.track

import com.mymonstervr.kawabi.data.network.TrackerApi
import com.mymonstervr.kawabi.data.network.TrackerAuthNotifier
import com.mymonstervr.kawabi.data.network.TrackerStatus
import com.mymonstervr.kawabi.data.network.TrackerTokenStore
import com.mymonstervr.kawabi.data.settings.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One tracker's connection state, as last known -- from a status fetch or a local 401 flip. */
data class TrackerStatusState(
    val connected: Boolean,
    val expired: Boolean = false,
    val userName: String? = null,
    val error: String? = null,
)

/**
 * Registry of the app's external trackers. [loggedInTrackerIds] is the
 * reactive "which trackers is this account connected to" signal that both
 * Settings -> Tracking services and the per-title tracker-linking sheet's
 * [loggedInTrackers] filter depend on -- it excludes an [expired] tracker, so
 * sync/import quietly skip a tracker whose upstream login has died instead of
 * failing on every attempt.
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
    private val appPreferences: AppPreferences,
    trackerAuthNotifier: TrackerAuthNotifier,
    private val scope: CoroutineScope,
) {
    private val initialStatuses: Map<String, TrackerStatusState> =
        tokenStore.loggedInTrackerIds.value.associateWith { id ->
            TrackerStatusState(connected = true, userName = tokenStore.getProfile(id))
        }

    private val _statuses = MutableStateFlow(initialStatuses)
    val statuses: StateFlow<Map<String, TrackerStatusState>> = _statuses.asStateFlow()

    val loggedInTrackerIds: StateFlow<Set<String>> = statuses
        .map { map -> map.filterValues { it.connected && !it.expired }.keys }
        .stateIn(scope, SharingStarted.Eagerly, initialStatuses.filterValues { it.connected }.keys)

    fun loggedInTrackers(): List<Tracker> = trackers.filter { it.id in loggedInTrackerIds.value }

    fun byId(trackerId: String): Tracker? = trackers.firstOrNull { it.id == trackerId }

    /** The browser-OAuth tracker whose `kawabi://` redirect host is [redirectHost], if any. */
    fun oAuthTrackerFor(redirectHost: String): BrowserOAuthTracker? =
        trackers.filterIsInstance<BrowserOAuthTracker>().firstOrNull { it.redirectHost == redirectHost }

    init {
        // A tracker-scoped 401 (TrackerApi) can happen at any moment, independent of the
        // next status refresh -- flip that tracker to expired immediately so sync/import
        // stop retrying it and the UI reflects it without waiting up to 6h.
        scope.launch {
            trackerAuthNotifier.events.collect { trackerId -> markExpiredLocally(trackerId) }
        }
    }

    /** [verify] actively checks each tracker upstream instead of returning cached status. */
    suspend fun refresh(verify: Boolean) {
        runCatching { trackerApi.status(verify = verify) }.onSuccess { list ->
            applyWireStatuses(list)
            if (verify) appPreferences.markTrackerVerified()
        }
    }

    /** Verifies upstream only if the 6h throttle window has elapsed; otherwise a cheap cached refresh. */
    suspend fun refreshIfVerifyDue() {
        refresh(verify = appPreferences.isTrackerVerifyDue())
    }

    private fun applyWireStatuses(list: List<TrackerStatus>) {
        tokenStore.replaceAll(list)
        _statuses.value = list.associate { item ->
            item.tracker to TrackerStatusState(
                connected = true,
                expired = item.expired,
                userName = item.userName,
                error = item.error,
            )
        }
    }

    private fun markExpiredLocally(trackerId: String) {
        val current = _statuses.value
        val existing = current[trackerId] ?: TrackerStatusState(connected = true)
        _statuses.value = current + (trackerId to existing.copy(connected = true, expired = true))
    }
}
