package com.mymonstervr.kawabi.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Shared enqueue shape for this app's network-gated periodic backstop workers. */
inline fun <reified W : CoroutineWorker> schedulePeriodic(
    context: Context,
    uniqueName: String,
    repeatInterval: Long,
    timeUnit: TimeUnit,
) {
    val request = PeriodicWorkRequestBuilder<W>(repeatInterval, timeUnit)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(uniqueName, ExistingPeriodicWorkPolicy.KEEP, request)
}
