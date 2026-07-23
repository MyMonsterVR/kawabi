package com.mymonstervr.kawabi.app.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private const val WORK_NAME = "app_update_download"
private const val EXTRA_DOWNLOAD_URL = "download_url"

class AppUpdateDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val notifier = AppUpdateNotifier(applicationContext)
    private val client = OkHttpClient()

    override suspend fun doWork(): Result {
        val url = inputData.getString(EXTRA_DOWNLOAD_URL) ?: return Result.failure()

        return withContext(Dispatchers.IO) {
            notifier.downloading()
            runCatching {
                val apkFile = File(applicationContext.externalCacheDir, "kawabi-update.apk")
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    apkFile.outputStream().use { out -> response.body.byteStream().copyTo(out) }
                }
                notifier.promptInstall(apkFile)
            }.fold(
                onSuccess = { Result.success() },
                onFailure = {
                    notifier.downloadFailed()
                    Result.failure()
                },
            )
        }
    }

    companion object {
        fun start(context: Context, downloadUrl: String) {
            val request = OneTimeWorkRequestBuilder<AppUpdateDownloadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(EXTRA_DOWNLOAD_URL to downloadUrl))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
