package com.mymonstervr.kawabi.app.di

import com.mymonstervr.kawabi.app.auth.LoginViewModel
import com.mymonstervr.kawabi.app.detail.MangaDetailViewModel
import com.mymonstervr.kawabi.app.library.LibraryViewModel
import com.mymonstervr.kawabi.app.reader.ReaderViewModel
import com.mymonstervr.kawabi.app.search.SearchViewModel
import com.mymonstervr.kawabi.app.settings.BackupViewModel
import com.mymonstervr.kawabi.app.settings.SettingsViewModel
import com.mymonstervr.kawabi.app.settings.SourcesViewModel
import com.mymonstervr.kawabi.app.settings.TrackingServicesViewModel
import com.mymonstervr.kawabi.app.update.AppUpdateChecker
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { AppUpdateChecker(get(), get()) }
    viewModel { LibraryViewModel(get(), get()) }
    viewModel { LoginViewModel(get(), get(), get(), get()) }
    viewModel { SearchViewModel(get()) }
    viewModel { MangaDetailViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ReaderViewModel(get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
    viewModel { SourcesViewModel(get()) }
    viewModel { BackupViewModel(get()) }
    viewModel { TrackingServicesViewModel(get()) }
}
