package com.mymonstervr.kawabi.tv.di

import com.mymonstervr.kawabi.data.network.AppReleaseApi
import com.mymonstervr.kawabi.data.update.AppUpdateChecker
import com.mymonstervr.kawabi.tv.BuildConfig
import com.mymonstervr.kawabi.tv.detail.TvDetailViewModel
import com.mymonstervr.kawabi.tv.home.TvHomeViewModel
import com.mymonstervr.kawabi.tv.pairing.PairingViewModel
import com.mymonstervr.kawabi.tv.search.TvSearchViewModel
import com.mymonstervr.kawabi.tv.settings.TvSettingsViewModel
import com.mymonstervr.kawabi.tv.update.TvUpdateNotifier
import com.mymonstervr.kawabi.tv.update.TvUpdateStateHolder
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val tvModule = module {
    viewModel { PairingViewModel(get()) }
    viewModel { TvHomeViewModel(get(), get(), get()) }
    viewModel { TvSearchViewModel(get()) }
    viewModel { TvDetailViewModel(get(), get(), get(), get()) }
    viewModel { TvSettingsViewModel(get(), get(), get(), get()) }

    // Own AppReleaseApi instance pointed at the TV manifest -- not the shared dataModule
    // single, which points at the phone app's /v2/ path.
    single { AppUpdateChecker(AppReleaseApi(get(), AppReleaseApi.TV_MANIFEST_URL), get(), BuildConfig.COMMIT_COUNT) }
    single { TvUpdateNotifier(get()) }
    single { TvUpdateStateHolder(androidContext()) }
}
