package com.mymonstervr.kawabi.data.track.kitsu

import com.mymonstervr.kawabi.data.network.TrackerApi
import com.mymonstervr.kawabi.data.network.TrackerTokenStore
import com.mymonstervr.kawabi.data.track.Tracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Account-level Kitsu connection (Settings -> Tracking services). Email+password form, no browser. */
class KitsuTracker(
    private val trackerApi: TrackerApi,
    private val tokenStore: TrackerTokenStore,
    private val scope: CoroutineScope,
) : Tracker {

    override val id: String = TrackerTokenStore.TRACKER_KITSU
    override val name: String = "Kitsu"

    override val userName: String?
        get() = tokenStore.getProfile(id)

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        val userName = trackerApi.connectKitsu(email, password)
        tokenStore.saveProfile(id, userName)
    }

    override fun logout() {
        tokenStore.clearProfile(id)
        scope.launch { runCatching { trackerApi.disconnect(id) } }
    }
}
