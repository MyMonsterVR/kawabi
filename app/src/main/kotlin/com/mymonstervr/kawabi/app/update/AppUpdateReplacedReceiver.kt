package com.mymonstervr.kawabi.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mymonstervr.kawabi.app.MainActivity
import java.io.File

/**
 * Fires when this app is reinstalled/updated in place (MY_PACKAGE_REPLACED), including a
 * normal sideloaded APK install via the system package installer -- launches straight back
 * into the app so the user isn't left tapping "Open" from the installer UI themselves.
 * Android still requires the user to tap "Install" in that UI first; there's no way around
 * that for a non-system, non-device-owner app.
 *
 * Also deletes the cached update APK: this is the only signal that an install actually went
 * through (fires regardless of install source, not just this app's own installer intent), and
 * AppUpdateDownloadState.toDownloadState() treats the file's existence as "still pending" --
 * without deleting it here, "Update ready to install" would never go away once shown once,
 * since WorkManager's own SUCCEEDED result for that download never expires on its own.
 */
class AppUpdateReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        File(context.externalCacheDir, UPDATE_APK_FILENAME).delete()
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(launchIntent)
    }
}
