package com.mymonstervr.kawabi.app.update

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

private const val WORK_NAME = "app_update_download"
private const val EXTRA_DOWNLOAD_URL = "download_url"
private const val NOTIFICATION_ID = 1001
const val PROGRESS_KEY = "progress_percent"
const val APK_PATH_KEY = "apk_path"

class AppUpdateDownloadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params),
    KoinComponent {

    private val notifier: AppUpdateNotifier by inject()
    private val client = OkHttpClient()

    override suspend fun doWork(): Result {
        val url = inputData.getString(EXTRA_DOWNLOAD_URL) ?: return Result.failure()

        return withContext(Dispatchers.IO) {
            setForeground(foregroundInfo(notifier.buildForegroundNotification()))
            runCatching {
                val apkFile = File(applicationContext.externalCacheDir, "kawabi-update.apk")
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val body = response.body
                    val total = body.contentLength()
                    var written = 0L
                    var lastPercent = -1
                    body.byteStream().use { input ->
                        apkFile.outputStream().use { out ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                out.write(buffer, 0, read)
                                written += read
                                if (total > 0) {
                                    val percent = ((written * 100) / total).toInt()
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        setProgress(workDataOf(PROGRESS_KEY to percent))
                                        notifier.downloading(percent)
                                    }
                                }
                            }
                        }
                    }
                }
                notifier.promptInstall(apkFile)
                apkFile
            }.fold(
                onSuccess = { apkFile ->
                    Result.success(workDataOf(APK_PATH_KEY to apkFile.absolutePath))
                },
                onFailure = {
                    notifier.downloadFailed()
                    Result.failure()
                },
            )
        }
    }

    private fun foregroundInfo(notification: Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }

    companion object {
        fun start(context: Context, downloadUrl: String) {
            val request = OneTimeWorkRequestBuilder<AppUpdateDownloadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(EXTRA_DOWNLOAD_URL to downloadUrl))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun workInfoFlow(context: Context) =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_NAME)
    }
}
