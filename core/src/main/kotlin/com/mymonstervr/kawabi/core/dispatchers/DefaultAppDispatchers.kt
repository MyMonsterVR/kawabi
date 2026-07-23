package com.mymonstervr.kawabi.core.dispatchers

import kotlinx.coroutines.Dispatchers

class DefaultAppDispatchers : AppDispatchers {
    override val io = Dispatchers.IO
    override val main = Dispatchers.Main
    override val default = Dispatchers.Default
}
