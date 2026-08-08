package com.mymonstervr.kawabi.data.track.kitsu

import com.mymonstervr.kawabi.data.network.TrackerApi
import com.mymonstervr.kawabi.data.network.TrackerTokenStore
import com.mymonstervr.kawabi.data.track.AccountTracker
import kotlinx.coroutines.CoroutineScope

/** Account-level Kitsu connection (Settings -> Tracking services). Email+password form, no browser. */
class KitsuTracker(
    trackerApi: TrackerApi,
    tokenStore: TrackerTokenStore,
    scope: CoroutineScope,
) : AccountTracker(trackerApi, tokenStore, scope) {

    override val id: String = TrackerTokenStore.TRACKER_KITSU
    override val name: String = "Kitsu"

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        val userName = trackerApi.connectKitsu(email, password)
        tokenStore.saveProfile(id, userName)
    }
}
