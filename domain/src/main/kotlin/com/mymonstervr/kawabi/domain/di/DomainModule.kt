package com.mymonstervr.kawabi.domain.di

import com.mymonstervr.kawabi.domain.interactor.SyncChaptersWithSource
import com.mymonstervr.kawabi.domain.interactor.SyncEpisodesWithSource
import org.koin.dsl.module

val domainModule = module {
    single { SyncChaptersWithSource(get()) }
    single { SyncEpisodesWithSource(get()) }
}
