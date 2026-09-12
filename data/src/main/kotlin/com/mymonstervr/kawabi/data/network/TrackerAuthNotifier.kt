package com.mymonstervr.kawabi.data.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Lets [TrackerApi] surface a tracker-specific 401 ("tracker auth expired") the moment it
 * happens, so [com.mymonstervr.kawabi.data.track.TrackerManager] can flip that tracker to
 * expired locally without waiting for the next status verify. Distinct from
 * [SessionExpiryNotifier] -- this is the tracker's own auth dying, not this app's backend
 * session, and must never clear [TokenStore] or log the user out of the backend.
 */
class TrackerAuthNotifier {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun notifyExpired(trackerId: String) {
        _events.tryEmit(trackerId)
    }
}
