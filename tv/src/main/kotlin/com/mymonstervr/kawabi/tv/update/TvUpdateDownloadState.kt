package com.mymonstervr.kawabi.tv.update

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

sealed interface TvUpdateDownloadState {
    data object Idle : TvUpdateDownloadState
    data class Downloading(val percent: Int) : TvUpdateDownloadState
    data class ReadyToInstall(val apkPath: String) : TvUpdateDownloadState
    data object Failed : TvUpdateDownloadState
}

// WorkManager keeps a unique work chain's last SUCCEEDED result around indefinitely -- the
// file-still-exists check is what distinguishes "still pending" from "already actioned"
// (TvUpdateReplacedReceiver deletes the file once MY_PACKAGE_REPLACED confirms the install
// went through), same as the phone app's AppUpdateDownloadState.
private fun WorkInfo?.toDownloadState(): TvUpdateDownloadState = when (this?.state) {
    WorkInfo.State.RUNNING -> TvUpdateDownloadState.Downloading(progress.getInt(PROGRESS_KEY, -1))
    WorkInfo.State.SUCCEEDED -> outputData.getString(APK_PATH_KEY)
        ?.takeIf { File(it).exists() }
        ?.let(TvUpdateDownloadState::ReadyToInstall) ?: TvUpdateDownloadState.Idle
    WorkInfo.State.FAILED -> TvUpdateDownloadState.Failed
    else -> TvUpdateDownloadState.Idle
}

class TvUpdateStateHolder(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val state: StateFlow<TvUpdateDownloadState> = TvUpdateDownloadWorker.workInfoFlow(context)
        .map { infos -> infos.firstOrNull().toDownloadState() }
        .stateIn(scope, SharingStarted.Eagerly, TvUpdateDownloadState.Idle)
}
