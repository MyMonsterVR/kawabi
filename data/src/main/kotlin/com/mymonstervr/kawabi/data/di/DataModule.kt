package com.mymonstervr.kawabi.data.di

import com.mymonstervr.kawabi.data.backup.BackupManager
import com.mymonstervr.kawabi.data.db.DatabaseDriverFactory
import com.mymonstervr.kawabi.data.db.createDatabase
import com.mymonstervr.kawabi.data.network.AppReleaseApi
import com.mymonstervr.kawabi.data.network.AuthApi
import com.mymonstervr.kawabi.data.network.AuthInterceptor
import com.mymonstervr.kawabi.data.network.SessionExpiryNotifier
import com.mymonstervr.kawabi.data.network.SourceApi
import com.mymonstervr.kawabi.data.network.SyncApi
import com.mymonstervr.kawabi.data.network.TokenStore
import com.mymonstervr.kawabi.data.network.TrackerApi
import com.mymonstervr.kawabi.data.network.TrackerTokenStore
import com.mymonstervr.kawabi.data.network.createOkHttpClient
import com.mymonstervr.kawabi.data.repository.SqlDelightCategoryRepository
import com.mymonstervr.kawabi.data.repository.SqlDelightChapterRepository
import com.mymonstervr.kawabi.data.repository.SqlDelightHistoryRepository
import com.mymonstervr.kawabi.data.repository.SqlDelightMangaRepository
import com.mymonstervr.kawabi.data.repository.SqlDelightTrackRepository
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.track.TrackerManager
import com.mymonstervr.kawabi.data.track.kitsu.KitsuTracker
import com.mymonstervr.kawabi.data.track.myanimelist.MyAnimeListTracker
import com.mymonstervr.kawabi.data.usecase.AddMangaToLibrary
import com.mymonstervr.kawabi.data.usecase.LibraryUpdateManager
import com.mymonstervr.kawabi.data.usecase.RefreshMangaChapters
import com.mymonstervr.kawabi.data.usecase.SyncClient
import com.mymonstervr.kawabi.data.usecase.TrackerSyncClient
import com.mymonstervr.kawabi.domain.repository.CategoryRepository
import com.mymonstervr.kawabi.domain.repository.ChapterRepository
import com.mymonstervr.kawabi.domain.repository.HistoryRepository
import com.mymonstervr.kawabi.domain.repository.MangaRepository
import com.mymonstervr.kawabi.domain.repository.TrackRepository
import org.koin.dsl.module

val dataModule = module {
    single { DatabaseDriverFactory(get()).create() }
    single { createDatabase(get()) }

    single<MangaRepository> { SqlDelightMangaRepository(get(), get()) }
    single<ChapterRepository> { SqlDelightChapterRepository(get(), get()) }
    single<CategoryRepository> { SqlDelightCategoryRepository(get(), get()) }
    single<HistoryRepository> { SqlDelightHistoryRepository(get(), get()) }
    single<TrackRepository> { SqlDelightTrackRepository(get(), get()) }

    single { TokenStore(get()) }
    single { TrackerTokenStore(get()) }
    single { AppPreferences(get()) }
    single { SessionExpiryNotifier() }
    single { AuthInterceptor(get(), get()) }
    single { createOkHttpClient(get()) }
    single { AuthApi(get(), get(), get()) }
    single { AppReleaseApi(get()) }
    single { SourceApi(get(), get()) }
    single { SyncApi(get(), get()) }
    single { RefreshMangaChapters(get(), get(), get()) }
    single { AddMangaToLibrary(get(), get(), get()) }
    single { SyncClient(get(), get(), get(), get(), get()) }
    single { LibraryUpdateManager(get(), get()) }
    single { BackupManager(get(), get(), get(), get()) }

    single { TrackerApi(get(), get()) }
    single { MyAnimeListTracker(get(), get(), get()) }
    single { KitsuTracker(get(), get(), get()) }
    single { TrackerManager(get(), get(), get(), get(), get()) }
    single { TrackerSyncClient(get(), get(), get()) }
}
