package com.mymonstervr.kawabi.core.di

import com.mymonstervr.kawabi.core.dispatchers.AppDispatchers
import com.mymonstervr.kawabi.core.dispatchers.DefaultAppDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

val coreModule = module {
    single<AppDispatchers> { DefaultAppDispatchers() }

    // Lives for the process, not any single screen -- for fire-and-forget work (like the
    // post-login sync trigger) that must survive the triggering ViewModel's own scope
    // being cancelled by nav pop. A plain viewModelScope.launch { syncClient.sync() } from
    // LoginViewModel was silently getting killed mid-flight the moment the user tapped
    // "Done" and left the login screen -- same class of bug as the reader's progress-flush
    // issue (a scope tied to a screen that navigates away before async work finishes).
    single { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}
