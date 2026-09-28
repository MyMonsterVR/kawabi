package com.mymonstervr.kawabi.tv

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.mymonstervr.kawabi.core.di.coreModule
import com.mymonstervr.kawabi.data.di.dataModule
import com.mymonstervr.kawabi.data.network.createImageOkHttpClient
import com.mymonstervr.kawabi.domain.di.domainModule
import com.mymonstervr.kawabi.player.di.playerModule
import com.mymonstervr.kawabi.tv.di.tvModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class KawabiTvApplication : Application(), SingletonImageLoader.Factory {
    // Same reasoning as the phone app's KawabiApplication -- Coil's default OkHttpClient's
    // stock 10s timeouts race the backend's slower /image fetches.
    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { createImageOkHttpClient() })) }
            .build()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@KawabiTvApplication)
            modules(coreModule, domainModule, dataModule, playerModule, tvModule)
        }
    }
}
