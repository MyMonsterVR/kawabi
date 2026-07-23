package com.mymonstervr.kawabi.domain.di

import com.mymonstervr.kawabi.domain.interactor.SyncChaptersWithSource
import org.koin.dsl.module

val domainModule = module {
    single { SyncChaptersWithSource(get()) }
}
