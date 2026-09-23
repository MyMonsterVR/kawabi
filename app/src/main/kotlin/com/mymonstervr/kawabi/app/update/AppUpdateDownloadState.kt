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

sealed interface AppUpdateDownloadState {
    data object Idle : AppUpdateDownloadState
    data class Downloading(val percent: Int) : AppUpdateDownloadState
    data class ReadyToInstall(val apkPath: String) : AppUpdateDownloadState
    data object Failed : AppUpdateDownloadState
}

private fun WorkInfo?.toDownloadState(): AppUpdateDownloadState = when (this?.state) {
    WorkInfo.State.RUNNING -> AppUpdateDownloadState.Downloading(progress.getInt(PROGRESS_KEY, -1))
    WorkInfo.State.SUCCEEDED -> outputData.getString(APK_PATH_KEY)
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
