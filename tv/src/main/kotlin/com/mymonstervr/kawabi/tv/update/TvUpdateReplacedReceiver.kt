package com.mymonstervr.kawabi.tv.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mymonstervr.kawabi.tv.TvActivity
import java.io.File

/**
 * Fires when this app is reinstalled/updated in place (MY_PACKAGE_REPLACED) -- launches
 * straight back into the app, same as the phone app's AppUpdateReplacedReceiver, and for the
 * same reason deletes the cached update APK here rather than relying on the worker's own
 * success path (this fires regardless of install source, including a manual Downloader-app
 * sideload of a newer build over this one).
 */
class TvUpdateReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        File(context.externalCacheDir, UPDATE_APK_FILENAME).delete()
        val launchIntent = Intent(context, TvActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(launchIntent)
    }
}
