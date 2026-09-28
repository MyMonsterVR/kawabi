package com.mymonstervr.kawabi.tv.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.mymonstervr.kawabi.data.update.AppUpdateInfo
import java.io.File

private const val CHANNEL_ID = "tv_updates"
private const val NOTIFICATION_ID = 1001

/**
 * TV port of the phone app's AppUpdateNotifier -- same behavior, own notification channel/
 * FileProvider authority (${applicationId}.fileprovider resolves to
 * com.mymonstervr.kawabi.tv.fileprovider here, distinct from the phone app's). The Settings
 * screen is the primary surface for this on TV (a remote-driven notification tap is clunky),
 * but posting these too costs nothing and covers whichever a given device/launcher makes
 * easier to notice.
 */
class TvUpdateNotifier(private val context: Context) {

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notify(build: NotificationCompat.Builder.() -> Unit) {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .apply(build)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun downloading(percent: Int = -1) = notify {
        setContentTitle("Downloading update")
        setOngoing(true)
        if (percent < 0) {
            setProgress(0, 0, true)
        } else {
            setContentText("$percent%")
            setProgress(100, percent, false)
        }
    }

    fun buildForegroundNotification() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("Downloading update")
        .setOngoing(true)
        .setProgress(0, 0, true)
        .build()

    fun downloadFailed() = notify {
        setContentTitle("Update download failed")
        setContentText("Open Settings to retry")
        setOngoing(false)
        setProgress(0, 0, false)
    }

    fun buildInstallIntent(apkFile: File): Intent {
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun promptInstall(apkFile: File) {
        val installIntent = buildInstallIntent(apkFile)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notify {
            setContentTitle("Update ready to install")
            setContentText("Select to install")
            setContentIntent(pendingIntent)
            setAutoCancel(true)
            setOngoing(false)
            setProgress(0, 0, false)
        }
    }

    fun updateAvailable(info: AppUpdateInfo) = notify {
        setContentTitle("Update available")
        setContentText(info.version)
        setAutoCancel(true)
    }
}
