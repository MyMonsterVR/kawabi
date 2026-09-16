package com.mymonstervr.kawabi.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mymonstervr.kawabi.app.MainActivity

/**
 * Fires when this app is reinstalled/updated in place (MY_PACKAGE_REPLACED), including a
 * normal sideloaded APK install via the system package installer -- launches straight back
 * into the app so the user isn't left tapping "Open" from the installer UI themselves.
 * Android still requires the user to tap "Install" in that UI first; there's no way around
 * that for a non-system, non-device-owner app.
 */
class AppUpdateReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(launchIntent)
    }
}
