package com.mymonstervr.kawabi.app.update

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
import java.io.File

private const val CHANNEL_ID = "app_updates"
private const val NOTIFICATION_ID = 1001

/**
 * Update-flow notifications only -- this app has no other notification channel yet.
 * Every post is guarded by a POST_NOTIFICATIONS permission check (requested once from
 * MainActivity); on Android 13+ a denied/never-granted permission means these silently
 * no-op rather than crash -- the download/install still completes, just without a
 * progress/tap-to-install prompt to surface it.
 */
class AppUpdateNotifier(private val context: Context) {

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

    fun downloading() = notify {
        setContentTitle("Downloading update")
        setOngoing(true)
        setProgress(0, 0, true)
    }

    fun downloadFailed() = notify {
        setContentTitle("Update download failed")
        setContentText("Tap Settings to retry")
        setOngoing(false)
        setProgress(0, 0, false)
    }

    fun promptInstall(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        notify {
            setContentTitle("Update ready to install")
            setContentText("Tap to install")
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
