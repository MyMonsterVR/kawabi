package com.mymonstervr.kawabi.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.mymonstervr.kawabi.data.usecase.LibraryUpdateManager
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val WORK_NAME = "library_update"
private const val REPEAT_INTERVAL_HOURS = 6L

class LibraryUpdateWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params),
    KoinComponent {

    private val libraryUpdateManager: LibraryUpdateManager by inject()

    override suspend fun doWork(): Result {
        return try {
            libraryUpdateManager.updateDue()
            Result.success()
        } catch (e: Exception) {
            // Never retry aggressively -- this runs again on its own schedule anyway, and
            // a hard failure/retry loop is exactly the kind of unbounded backend hammering
            // this job exists to avoid.
            Result.success()
        }
    }

    companion object {
        // Every 6h, not e.g. hourly -- the smart-interval skip logic inside
        // LibraryUpdateManager means most runs are near-instant no-ops (nothing due yet),
        // but WorkManager itself still has to wake the process each time, and this is a
        // deliberate Suwayomi/WARP-egress cost-control measure (PLAN.md step 9), not
        // something to run needlessly often "just in case."
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LibraryUpdateWorker>(REPEAT_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            androidx.work.WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
