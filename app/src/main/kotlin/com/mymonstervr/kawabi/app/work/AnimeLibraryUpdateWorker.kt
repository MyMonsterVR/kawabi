package com.mymonstervr.kawabi.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mymonstervr.kawabi.data.usecase.AnimeLibraryUpdateManager
import java.util.concurrent.TimeUnit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val WORK_NAME = "anime_library_update"
private const val REPEAT_INTERVAL_HOURS = 6L

/** Anime counterpart of [LibraryUpdateWorker], same cadence and same never-retry policy. */
class AnimeLibraryUpdateWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params),
    KoinComponent {

    private val animeLibraryUpdateManager: AnimeLibraryUpdateManager by inject()

    override suspend fun doWork(): Result {
        return try {
            animeLibraryUpdateManager.updateDue()
            Result.success()
        } catch (e: Exception) {
            Result.success()
        }
    }

    companion object {
        fun schedule(context: Context) {
            schedulePeriodic<AnimeLibraryUpdateWorker>(context, WORK_NAME, REPEAT_INTERVAL_HOURS, TimeUnit.HOURS)
        }
    }
}
