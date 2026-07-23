package com.mymonstervr.kawabi.core.dispatchers

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Named coroutine dispatchers, injected instead of referencing
 * `Dispatchers.IO`/`Dispatchers.Main` directly so tests can swap in a
 * deterministic test dispatcher without touching call sites.
 */
interface AppDispatchers {
    val io: CoroutineDispatcher
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
}
