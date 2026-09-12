package com.mymonstervr.kawabi.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.core.content.ContextCompat
import com.mymonstervr.kawabi.app.theme.KawabiTheme
import com.mymonstervr.kawabi.data.track.TrackerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {

    // Android 13+ requires this to be requested at runtime -- without it, the update
    // flow's download/install-ready notifications silently no-op (AppUpdateNotifier
    // checks the permission before every post rather than crashing).
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Unconditional during active development so the screen doesn't lock mid-test-session --
        // becomes a real per-user Settings toggle in plan step 8, not app-wide by default.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestNotificationPermission()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            KawabiTheme(windowSizeClass) {
                KawabiApp()
            }
        }
        handleOAuthRedirect(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthRedirect(intent)
    }

    // A tracker's browser OAuth redirect (kawabi://<host>?code=...) lands here because
    // MainActivity is singleTask + BROWSABLE for those scheme/hosts (manifest). The host
    // picks the tracker, so adding one needs no branch here -- only a manifest entry and
    // a matching BrowserOAuthTracker.redirectHost. The Settings -> Tracking services
    // screen doesn't need to be open or even exist yet: TrackerManager's
    // loggedInTrackerIds is a StateFlow, so the screen picks up the new connection
    // reactively whenever it's next shown.
    private fun handleOAuthRedirect(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != "kawabi") return
        val tracker = get<TrackerManager>().oAuthTrackerFor(data.host.orEmpty()) ?: return
        val code = data.getQueryParameter("code") ?: return
        val state = data.getQueryParameter("state")

        // OAuth codes are single-use -- without this, a config change or
        // process-death recreation replays the same redirect intent through
        // onCreate again, re-exchanging (and failing) a code that already
        // succeeded, which can tear a good session back down (flagged by
        // security review).
        intent.data = null
        setIntent(intent)

        get<CoroutineScope>().launch {
            // exchangeCode's failure branch called from a plain Result.onFailure
            // lambda previously ran the Toast on this scope's Dispatchers.IO
            // thread, which has no Looper -- Toast.show() there crashes the app
            // (flagged by security review). Main-dispatch it explicitly instead.
            val result = tracker.exchangeCode(code, state)
            result.exceptionOrNull()?.let { error ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, error.message ?: "${tracker.name} login failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
