package com.mymonstervr.kawabi.app.update

import android.content.Context
import androidx.work.WorkInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File

sealed interface AppUpdateDownloadState {
    data object Idle : AppUpdateDownloadState
    data class Downloading(val percent: Int) : AppUpdateDownloadState
    data class ReadyToInstall(val apkPath: String) : AppUpdateDownloadState
    data object Failed : AppUpdateDownloadState
}

// WorkManager keeps a unique work chain's last SUCCEEDED result around indefinitely -- with
// no consumption/expiry, every future app launch (this is an app-wide singleton -- see
// AppUpdateStateHolder below) would replay "ready to install" for whatever APK a past update
// downloaded, even long after it was installed. The file-still-exists check is what actually
// distinguishes "still pending" from "already actioned" -- AppUpdateReplacedReceiver deletes
// the file once MY_PACKAGE_REPLACED confirms the install went through, which is what makes
// this fall back to Idle afterward instead of looping "tap to install" forever.
private fun WorkInfo?.toDownloadState(): AppUpdateDownloadState = when (this?.state) {
    WorkInfo.State.RUNNING -> AppUpdateDownloadState.Downloading(progress.getInt(PROGRESS_KEY, -1))
    WorkInfo.State.SUCCEEDED -> outputData.getString(APK_PATH_KEY)
        ?.takeIf { File(it).exists() }
        ?.let(AppUpdateDownloadState::ReadyToInstall) ?: AppUpdateDownloadState.Idle
    WorkInfo.State.FAILED -> AppUpdateDownloadState.Failed
    else -> AppUpdateDownloadState.Idle
}

/**
 * App-wide mirror of the update-download worker's state, so a progress indicator and the
 * auto-install prompt work regardless of which screen is on top -- the download isn't tied
 * to Settings, it keeps running via the worker's foreground service after navigating away.
 */
class AppUpdateStateHolder(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val state: StateFlow<AppUpdateDownloadState> = AppUpdateDownloadWorker.workInfoFlow(context)
        .map { infos -> infos.firstOrNull().toDownloadState() }
        .stateIn(scope, SharingStarted.Eagerly, AppUpdateDownloadState.Idle)
}
