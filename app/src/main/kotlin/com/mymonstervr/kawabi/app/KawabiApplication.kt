package com.mymonstervr.kawabi.app

import android.app.Application
import com.mymonstervr.kawabi.app.di.appModule
import com.mymonstervr.kawabi.app.update.AppUpdateChecker
import com.mymonstervr.kawabi.app.update.AppUpdateNotifier
import com.mymonstervr.kawabi.app.work.LibraryUpdateWorker
import com.mymonstervr.kawabi.core.di.coreModule
import com.mymonstervr.kawabi.data.di.dataModule
import com.mymonstervr.kawabi.domain.di.domainModule
import com.mymonstervr.kawabi.data.usecase.SyncClient
import com.mymonstervr.kawabi.domain.repository.CategoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class KawabiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@KawabiApplication)
            modules(coreModule, domainModule, dataModule, appModule)
        }
        get<CoroutineScope>().launch {
            get<CategoryRepository>().ensureDefault()
            // Opportunistic sync point alongside LoginViewModel firing one right after a
            // successful login.
            get<SyncClient>().sync()
        }
        LibraryUpdateWorker.schedule(this)

        // Silent, throttled (AppPreferences.isUpdateCheckDue) -- Settings also exposes a
        // manual "Check for updates" button for an explicit forceCheck.
        get<CoroutineScope>().launch {
            get<AppUpdateChecker>().check()?.let { AppUpdateNotifier(this@KawabiApplication).updateAvailable(it) }
        }
    }
}
