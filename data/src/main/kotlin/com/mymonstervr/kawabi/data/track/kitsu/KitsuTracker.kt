package com.mymonstervr.kawabi.data.track.kitsu

import com.mymonstervr.kawabi.data.network.TrackerTokenStore
import com.mymonstervr.kawabi.data.track.Tracker
import com.mymonstervr.kawabi.data.track.TrackerProfile
import com.mymonstervr.kawabi.data.track.trackerJson

/** Account-level Kitsu connection (Settings -> Tracking services). Email+password form, no browser. */
class KitsuTracker(
    private val api: KitsuApi,
    private val interceptor: KitsuInterceptor,
    private val tokenStore: TrackerTokenStore,
) : Tracker {

    override val id: String = TrackerTokenStore.TRACKER_KITSU
    override val name: String = "Kitsu"

    override val userName: String?
        get() = tokenStore.getProfile(id)?.let(TrackerProfile::decode)?.userName

    // Kitsu's library-entry endpoints (findLibManga/addLibManga) are scoped by
    // user id, not username -- needed by TrackerSyncClient's bind/search flow.
    val userId: String?
        get() = tokenStore.getProfile(id)?.let(TrackerProfile::decode)?.userId

    suspend fun login(username: String, password: String): Result<Unit> = runCatching {
        val oauth = api.login(username, password)
        interceptor.setOAuth(oauth)
        try {
            val user = api.getCurrentUser()
            tokenStore.saveProfile(
                id,
                trackerJson.encodeToString(TrackerProfile.serializer(), TrackerProfile(userName = user.name, userId = user.id)),
            )
        } catch (e: Exception) {
            // Don't leave a connected-but-nameless session behind if the follow-up
            // identity lookup fails right after a successful login.
            interceptor.setOAuth(null)
            throw e
        }
    }

    override fun logout() {
        interceptor.setOAuth(null)
    }
}
