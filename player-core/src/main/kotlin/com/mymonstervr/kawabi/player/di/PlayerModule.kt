package com.mymonstervr.kawabi.player.di

import com.mymonstervr.kawabi.player.PlayerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val playerModule = module {
    viewModel { PlayerViewModel(androidContext(), get(), get(), get(), get(), get(), get(), get()) }
}
