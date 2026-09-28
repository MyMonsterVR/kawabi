package com.mymonstervr.kawabi.tv.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import com.mymonstervr.kawabi.data.settings.ANIME_AUTO_MARK_WATCHED_THRESHOLD_MAX
import com.mymonstervr.kawabi.data.settings.ANIME_AUTO_MARK_WATCHED_THRESHOLD_MIN
import com.mymonstervr.kawabi.data.settings.SUBTITLE_TEXT_SIZE_MAX
import com.mymonstervr.kawabi.data.settings.SUBTITLE_TEXT_SIZE_MIN
import com.mymonstervr.kawabi.data.settings.SubtitleBackgroundStyle
import com.mymonstervr.kawabi.tv.theme.TvColors
import com.mymonstervr.kawabi.tv.theme.TvDimens
import com.mymonstervr.kawabi.tv.update.TvUpdateDownloadState
import com.mymonstervr.kawabi.tv.update.TvUpdateDownloadWorker
import com.mymonstervr.kawabi.tv.update.TvUpdateNotifier
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File
import kotlin.math.roundToInt

private val PREFERRED_AUDIO_OPTIONS = listOf("" to "Any", "sub" to "Sub", "dub" to "Dub")
private val PREFERRED_QUALITIES = listOf("" to "Auto", "1080p" to "1080p", "720p" to "720p", "480p" to "480p")
private const val MARK_WATCHED_STEP = 0.05f
private const val SUBTITLE_SIZE_STEP = 10

@Composable
fun TvSettingsScreen(
    onBack: () -> Unit,
    viewModel: TvSettingsViewModel = koinViewModel(),
) {
    val email by viewModel.email.collectAsState()
    val preferredAudio by viewModel.animePreferredAudio.collectAsState()
    val preferredQuality by viewModel.animePreferredQuality.collectAsState()
    val autoSkipIntro by viewModel.animeAutoSkipIntro.collectAsState()
    val markWatchedThreshold by viewModel.animeAutoMarkWatchedThreshold.collectAsState()
    val subtitleTextSize by viewModel.subtitleTextSize.collectAsState()
    val subtitleBackgroundStyle by viewModel.subtitleBackgroundStyle.collectAsState()
    val updateCheckState by viewModel.updateCheckState.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val updateNotifier: TvUpdateNotifier = koinInject()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // A LazyColumn, not a plain Column -- Home's row titles rendering entirely off-screen
    // (fixed here 2026-09-28) was exactly this same mistake: a settings screen this long
    // (7 groups) has no guarantee of fitting the 540dp canvas either, and this way it just
    // scrolls instead of silently clipping whatever doesn't fit.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(horizontal = TvDimens.OverscanHorizontal, vertical = TvDimens.OverscanVertical),
    ) {
        item {
            Text("Settings", color = TvColors.Text, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            Text("Signed in as", color = TvColors.TextDim, style = MaterialTheme.typography.bodyMedium)
            Text(email ?: "...", color = TvColors.Text, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))
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
        }

        item {
            SettingsSection("Preferred audio") {
                OptionRow(PREFERRED_AUDIO_OPTIONS, preferredAudio, viewModel::setPreferredAudio)
            }
        }

        item {
            SettingsSection("Preferred quality") {
                OptionRow(PREFERRED_QUALITIES, preferredQuality, viewModel::setPreferredQuality)
            }
        }

        item {
            SettingsSection("Skip intros and endings") {
                Button(
                    onClick = { viewModel.setAutoSkipIntro(!autoSkipIntro) },
                    colors = ButtonDefaults.colors(
                        containerColor = if (autoSkipIntro) TvColors.ChipHover else TvColors.SurfaceOverlay,
                        contentColor = if (autoSkipIntro) TvColors.Accent else TvColors.TextSecondary,
                        focusedContainerColor = TvColors.Accent,
                        focusedContentColor = TvColors.OnAccent,
                    ),
                ) { Text(if (autoSkipIntro) "Auto-skip: On" else "Auto-skip: Off (shows a Skip button instead)") }
            }
        }

        item {
            SettingsSection("Mark watched at") {
                StepperRow(
                    label = "${(markWatchedThreshold * 100).roundToInt()}% of an episode",
                    onDecrease = {
                        viewModel.setAutoMarkWatchedThreshold(
                            (markWatchedThreshold - MARK_WATCHED_STEP).coerceIn(ANIME_AUTO_MARK_WATCHED_THRESHOLD_MIN, ANIME_AUTO_MARK_WATCHED_THRESHOLD_MAX),
                        )
                    },
                    onIncrease = {
                        viewModel.setAutoMarkWatchedThreshold(
                            (markWatchedThreshold + MARK_WATCHED_STEP).coerceIn(ANIME_AUTO_MARK_WATCHED_THRESHOLD_MIN, ANIME_AUTO_MARK_WATCHED_THRESHOLD_MAX),
                        )
                    },
                )
            }
        }

        item {
            SettingsSection("Subtitle text size") {
                StepperRow(
                    label = "$subtitleTextSize% of default",
                    onDecrease = { viewModel.setSubtitleTextSize((subtitleTextSize - SUBTITLE_SIZE_STEP).coerceIn(SUBTITLE_TEXT_SIZE_MIN, SUBTITLE_TEXT_SIZE_MAX)) },
                    onIncrease = { viewModel.setSubtitleTextSize((subtitleTextSize + SUBTITLE_SIZE_STEP).coerceIn(SUBTITLE_TEXT_SIZE_MIN, SUBTITLE_TEXT_SIZE_MAX)) },
                )
            }
        }

        item {
            SettingsSection("Subtitle background") {
                OptionRow(
                    options = listOf(SubtitleBackgroundStyle.OUTLINE to "Outline", SubtitleBackgroundStyle.BOX to "Solid box"),
                    selected = subtitleBackgroundStyle,
                    onSelect = viewModel::setSubtitleBackgroundStyle,
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            UpdateRow(
                currentVersion = viewModel.currentVersion,
                checkState = updateCheckState,
                downloadState = downloadState,
                onCheckClick = { viewModel.checkForUpdate(force = true) },
                onInstallClick = { url -> TvUpdateDownloadWorker.start(context, url) },
                onInstallReadyClick = { apkPath -> context.startActivity(updateNotifier.buildInstallIntent(File(apkPath))) },
            )
            Spacer(Modifier.height(TvDimens.OverscanVertical))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Spacer(Modifier.height(24.dp))
        Text(title, color = TvColors.TextDim, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun <T> OptionRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row {
        options.forEachIndexed { index, (value, label) ->
            if (index > 0) Spacer(Modifier.width(12.dp))
            Button(
                onClick = { onSelect(value) },
                colors = ButtonDefaults.colors(
                    containerColor = if (value == selected) TvColors.ChipHover else TvColors.SurfaceOverlay,
                    contentColor = if (value == selected) TvColors.Accent else TvColors.TextSecondary,
                    focusedContainerColor = TvColors.Accent,
                    focusedContentColor = TvColors.OnAccent,
                ),
            ) { Text(label) }
        }
    }
}

/** D-pad-friendly stepper -- a drag Slider has no equivalent gesture on a remote. */
@Composable
private fun StepperRow(label: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row {
        Button(
            onClick = onDecrease,
            colors = ButtonDefaults.colors(
                containerColor = TvColors.SurfaceOverlay,
                contentColor = TvColors.TextSecondary,
                focusedContainerColor = TvColors.Accent,
                focusedContentColor = TvColors.OnAccent,
            ),
        ) { Text("-") }
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            color = TvColors.Text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(Modifier.width(16.dp))
        Button(
            onClick = onIncrease,
            colors = ButtonDefaults.colors(
                containerColor = TvColors.SurfaceOverlay,
                contentColor = TvColors.TextSecondary,
                focusedContainerColor = TvColors.Accent,
                focusedContentColor = TvColors.OnAccent,
            ),
        ) { Text("+") }
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
