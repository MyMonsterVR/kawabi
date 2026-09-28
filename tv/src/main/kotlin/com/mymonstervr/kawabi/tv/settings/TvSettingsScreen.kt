package com.mymonstervr.kawabi.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mymonstervr.kawabi.tv.theme.TvColors
import com.mymonstervr.kawabi.tv.theme.TvDimens
import com.mymonstervr.kawabi.tv.update.TvUpdateDownloadState
import com.mymonstervr.kawabi.tv.update.TvUpdateDownloadWorker
import com.mymonstervr.kawabi.tv.update.TvUpdateNotifier
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File

@Composable
fun TvSettingsScreen(
    onBack: () -> Unit,
    viewModel: TvSettingsViewModel = koinViewModel(),
) {
    val email by viewModel.email.collectAsState()
    val updateCheckState by viewModel.updateCheckState.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val updateNotifier: TvUpdateNotifier = koinInject()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(horizontal = TvDimens.OverscanHorizontal, vertical = TvDimens.OverscanVertical),
    ) {
        Text("Settings", color = TvColors.Text, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        Text("Signed in as", color = TvColors.TextDim, style = MaterialTheme.typography.bodyMedium)
        Text(email ?: "...", color = TvColors.Text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = viewModel::signOut,
            modifier = Modifier.focusRequester(focusRequester),
            colors = ButtonDefaults.colors(
                containerColor = TvColors.SurfaceOverlay,
                contentColor = TvColors.Text,
                focusedContainerColor = TvColors.Accent,
                focusedContentColor = TvColors.OnAccent,
            ),
        ) { Text("Sign out of this TV") }
        Spacer(Modifier.height(16.dp))
        UpdateRow(
            currentVersion = viewModel.currentVersion,
            checkState = updateCheckState,
            downloadState = downloadState,
            onCheckClick = { viewModel.checkForUpdate(force = true) },
            onInstallClick = { url -> TvUpdateDownloadWorker.start(context, url) },
            onInstallReadyClick = { apkPath -> context.startActivity(updateNotifier.buildInstallIntent(File(apkPath))) },
        )
    }
}

@Composable
private fun UpdateRow(
    currentVersion: String,
    checkState: TvUpdateCheckState,
    downloadState: TvUpdateDownloadState,
    onCheckClick: () -> Unit,
    onInstallClick: (String) -> Unit,
    onInstallReadyClick: (String) -> Unit,
) {
    val (label, onClick) = when (downloadState) {
        is TvUpdateDownloadState.Downloading ->
            (if (downloadState.percent >= 0) "Downloading update -- ${downloadState.percent}%" else "Downloading update...") to {}
        is TvUpdateDownloadState.ReadyToInstall ->
            "Update downloaded -- select to install" to { onInstallReadyClick(downloadState.apkPath) }
        TvUpdateDownloadState.Failed ->
            "Update download failed -- select to retry" to onCheckClick
        TvUpdateDownloadState.Idle -> when (checkState) {
            TvUpdateCheckState.Idle -> "Version $currentVersion" to onCheckClick
            TvUpdateCheckState.Checking -> "Checking for updates..." to {}
            TvUpdateCheckState.UpToDate -> "Up to date ($currentVersion)" to onCheckClick
            is TvUpdateCheckState.Available ->
                "Update available: ${checkState.info.version} -- select to download" to { onInstallClick(checkState.info.downloadUrl) }
        }
    }
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = TvColors.SurfaceOverlay,
            contentColor = TvColors.TextSecondary,
            focusedContainerColor = TvColors.Accent,
            focusedContentColor = TvColors.OnAccent,
        ),
    ) { Text(label) }
}
