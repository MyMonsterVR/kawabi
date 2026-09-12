package com.mymonstervr.kawabi.app.di

import com.mymonstervr.kawabi.app.anime.AnimeBrowseViewModel
import com.mymonstervr.kawabi.app.anime.AnimeDetailViewModel
import com.mymonstervr.kawabi.app.anime.AnimeLibraryViewModel
import com.mymonstervr.kawabi.app.anime.AnimeSearchViewModel
import com.mymonstervr.kawabi.app.anime.PlayerViewModel
import com.mymonstervr.kawabi.app.auth.LoginViewModel
import com.mymonstervr.kawabi.app.browse.BrowseViewModel
import com.mymonstervr.kawabi.app.detail.MangaDetailViewModel
import com.mymonstervr.kawabi.app.library.LibraryViewModel
import com.mymonstervr.kawabi.app.reader.ReaderViewModel
import com.mymonstervr.kawabi.app.search.SearchViewModel
import com.mymonstervr.kawabi.app.settings.AnimeSourcesViewModel
import com.mymonstervr.kawabi.app.settings.BackupViewModel
import com.mymonstervr.kawabi.app.settings.SettingsViewModel
import com.mymonstervr.kawabi.app.settings.SourcesViewModel
import com.mymonstervr.kawabi.app.settings.TrackingServicesViewModel
import com.mymonstervr.kawabi.app.update.AppUpdateChecker
import com.mymonstervr.kawabi.app.update.AppUpdateNotifier
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { AppUpdateChecker(get(), get()) }
    single { AppUpdateNotifier(get()) }
    viewModel { LibraryViewModel(get(), get(), get()) }
    viewModel { LoginViewModel(get(), get(), get(), get()) }
    viewModel { SearchViewModel(get(), get()) }
    viewModel { BrowseViewModel(get(), get()) }
    viewModel { MangaDetailViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ReaderViewModel(get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
    viewModel { SourcesViewModel(get()) }
    viewModel { BackupViewModel(get()) }
    viewModel { TrackingServicesViewModel(get(), get(), get(), get()) }
    viewModel { AnimeLibraryViewModel(get(), get(), get(), get()) }
    viewModel { AnimeSearchViewModel(get(), get()) }
    viewModel { AnimeBrowseViewModel(get(), get()) }
    viewModel { AnimeDetailViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { PlayerViewModel(androidContext(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { AnimeSourcesViewModel(get()) }
}
