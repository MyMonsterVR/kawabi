package com.mymonstervr.kawabi.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mymonstervr.kawabi.data.usecase.SyncClient
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val WORK_NAME = "progress_sync"
private const val REPEAT_INTERVAL_MINUTES = 60L

// KawabiApplication.onCreate() and LoginViewModel each fire a one-off SyncClient.sync(), but
// that only covers process cold start / login -- a long-lived process (tablets especially can
// go days without a true cold start) never re-syncs on its own after that, so progress read
// mid-session only reaches the backend once ReaderViewModel's own per-chapter-completion push
// happens to fire. This periodic worker is the backstop for everything else that can change
// local state without a chapter completing (e.g. a manual mark-read/unread from the manga
// detail screen) and for catching up a device that's been backgrounded a while.
class SyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params),
    KoinComponent {

    private val syncClient: SyncClient by inject()

    override suspend fun doWork(): Result {
        syncClient.sync()
        // sync() already swallows its own failures (runCatching) rather than throwing --
        // never retry aggressively here, same reasoning as LibraryUpdateWorker: this runs
        // again on its own schedule regardless.
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            schedulePeriodic<SyncWorker>(context, WORK_NAME, REPEAT_INTERVAL_MINUTES, TimeUnit.MINUTES)
        }
    }
}
