package com.mymonstervr.kawabi.tv.di

import com.mymonstervr.kawabi.tv.detail.TvDetailViewModel
import com.mymonstervr.kawabi.tv.home.TvHomeViewModel
import com.mymonstervr.kawabi.tv.pairing.PairingViewModel
import com.mymonstervr.kawabi.tv.search.TvSearchViewModel
import com.mymonstervr.kawabi.tv.settings.TvSettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val tvModule = module {
    viewModel { PairingViewModel(get()) }
    viewModel { TvHomeViewModel(get(), get(), get()) }
    viewModel { TvSearchViewModel(get()) }
    viewModel { TvDetailViewModel(get(), get(), get(), get()) }
    viewModel { TvSettingsViewModel(get(), get()) }
}
