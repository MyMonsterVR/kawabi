package com.mymonstervr.kawabi.data.track

import com.mymonstervr.kawabi.data.network.TrackerApi
import com.mymonstervr.kawabi.data.network.TrackerTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Shared [userName]/[logout] plumbing for account-level trackers (MAL, Kitsu) -- only their login mechanics differ. */
abstract class AccountTracker(
    protected val trackerApi: TrackerApi,
    protected val tokenStore: TrackerTokenStore,
    protected val scope: CoroutineScope,
) : Tracker {

    override val userName: String?
        get() = tokenStore.getProfile(id)

    override fun logout() {
        tokenStore.clearProfile(id)
        scope.launch { runCatching { trackerApi.disconnect(id) } }
    }
}
