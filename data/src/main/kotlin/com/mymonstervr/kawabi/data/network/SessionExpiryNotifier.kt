package com.mymonstervr.kawabi.data.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Lets [AuthInterceptor] surface a 401 without owning any UI -- the reader/library
 * screens observe [events] and show a non-blocking "session expired" affordance
 * (locked decision: don't yank the user out of what they're doing).
 */
class SessionExpiryNotifier {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun notifyExpired() {
        _events.tryEmit(Unit)
    }
}
