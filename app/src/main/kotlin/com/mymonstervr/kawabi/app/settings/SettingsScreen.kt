package com.mymonstervr.kawabi.app.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.common.PageTitle
import com.mymonstervr.kawabi.app.common.ResponsiveContainer
import com.mymonstervr.kawabi.app.common.SegmentedTabs
import com.mymonstervr.kawabi.app.common.accentSoft
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.app.update.AppUpdateDownloadState
import com.mymonstervr.kawabi.app.update.AppUpdateDownloadWorker
import com.mymonstervr.kawabi.app.update.AppUpdateNotifier
import com.mymonstervr.kawabi.app.update.GradientActionButton
import com.mymonstervr.kawabi.app.update.UpdateSheet
import com.mymonstervr.kawabi.data.settings.ANIME_AUTO_MARK_WATCHED_THRESHOLD_MAX
import com.mymonstervr.kawabi.data.settings.ANIME_AUTO_MARK_WATCHED_THRESHOLD_MIN
import com.mymonstervr.kawabi.data.settings.LIBRARY_GRID_COLUMNS_MAX
import com.mymonstervr.kawabi.data.settings.LIBRARY_GRID_COLUMNS_MIN
import com.mymonstervr.kawabi.data.settings.MARK_READ_THRESHOLD_MAX
import com.mymonstervr.kawabi.data.settings.MARK_READ_THRESHOLD_MIN
import com.mymonstervr.kawabi.data.settings.PageFitMode
import com.mymonstervr.kawabi.data.settings.ReadingDirection
import com.mymonstervr.kawabi.data.settings.SUBTITLE_TEXT_SIZE_MAX
import com.mymonstervr.kawabi.data.settings.SUBTITLE_TEXT_SIZE_MIN
import com.mymonstervr.kawabi.data.settings.SubtitleBackgroundStyle
import com.mymonstervr.kawabi.data.settings.ThemePalette
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private val PREFERRED_QUALITIES = listOf("", "1080p", "720p", "480p")
private val PREFERRED_QUALITY_LABELS = listOf("Auto", "1080p", "720p", "480p")
private val PREFERRED_AUDIO_OPTIONS = listOf("" to "Any", "sub" to "Sub", "dub" to "Dub")

@Composable
fun SettingsScreen(
    onAccountClick: () -> Unit,
    onSourcesClick: () -> Unit,
    onAnimeSourcesClick: () -> Unit,
    onBackupClick: () -> Unit,
    onTrackingClick: () -> Unit,
    onChangelogClick: () -> Unit,
    onLinkTvClick: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val readingDirection by viewModel.readingDirection.collectAsState()
    val markReadOnScroll by viewModel.markReadOnScroll.collectAsState()
    val newChapterNotificationsEnabled by viewModel.newChapterNotificationsEnabled.collectAsState()
    val keepScreenAwake by viewModel.keepScreenAwake.collectAsState()
    val accentIndex by viewModel.accentIndex.collectAsState()
    val libraryGridColumns by viewModel.libraryGridColumns.collectAsState()
    val updateCheckState by viewModel.updateCheckState.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val pageFitMode by viewModel.pageFitMode.collectAsState()
    val markReadThreshold by viewModel.markReadThreshold.collectAsState()
    val themePalette by viewModel.themePalette.collectAsState()
    val amoledBlack by viewModel.amoledBlack.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val animeAutoMarkWatchedThreshold by viewModel.animeAutoMarkWatchedThreshold.collectAsState()
    val animePreferredQuality by viewModel.animePreferredQuality.collectAsState()
    val animePreferredAudio by viewModel.animePreferredAudio.collectAsState()
    val animeAutoSkipIntro by viewModel.animeAutoSkipIntro.collectAsState()
    val subtitleTextSize by viewModel.subtitleTextSize.collectAsState()
    val subtitleBackgroundStyle by viewModel.subtitleBackgroundStyle.collectAsState()
    val expiredTrackerCount by viewModel.expiredTrackerCount.collectAsState()
    val trackerSummaries by viewModel.trackerSummaries.collectAsState()
    val context = LocalContext.current
    val updateNotifier = koinInject<AppUpdateNotifier>()
    var showUpdateSheet by remember { mutableStateOf(false) }

    (updateCheckState as? UpdateCheckState.Available)?.takeIf { showUpdateSheet }?.let { available ->
        UpdateSheet(
            info = available.info,
            currentVersion = viewModel.currentVersion,
            onDismiss = { showUpdateSheet = false },
            onDownload = {
                showUpdateSheet = false
                AppUpdateDownloadWorker.start(context, available.info.downloadUrl)
            },
        )
    }

    androidx.compose.material3.Scaffold(containerColor = NightSession.Background) { padding ->
        ResponsiveContainer(modifier = Modifier.padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
                modifier = Modifier.background(NightSession.Background),
            ) {
                item { PageTitle("Settings", modifier = Modifier.padding(bottom = 16.dp)) }
                item { AccountCard(isLoggedIn = isLoggedIn, onClick = onAccountClick) }
                item {
                    SettingsSection("Content") {
                        SettingsNavRow("S", "Manga sources", onSourcesClick)
                        SettingsDivider()
                        SettingsNavRow("A", "Anime sources", onAnimeSourcesClick)
                    }
                }
                item {
                    SettingsSection("Tracking") {
                        if (trackerSummaries.isEmpty()) {
                            SettingsNavRow(
                                letter = "T",
                                label = "Tracking services",
                                onClick = onTrackingClick,
                                note = if (expiredTrackerCount > 0) "Reconnect" else null,
                                noteColor = if (expiredTrackerCount > 0) NightSession.Danger else null,
                            )
                        }
                        trackerSummaries.forEachIndexed { index, tracker ->
                            if (index > 0) SettingsDivider()
                            SettingsNavRow(
                                letter = tracker.name.take(1).uppercase(),
                                label = tracker.name,
                                onClick = onTrackingClick,
                                note = when {
                                    tracker.expired -> "Reconnect"
                                    tracker.connected -> "Connected"
                                    else -> "Not connected"
                                },
                                noteColor = when {
                                    tracker.expired -> NightSession.Danger
                                    tracker.connected -> NightSession.Read
                                    else -> null
                                },
                            )
                        }
                    }
                }
                item {
                    SettingsSection("Library") {
                        SettingsSliderRow(
                            title = "Grid columns",
                            readout = "$libraryGridColumns",
                            subtitle = "Per row, applies to Library and Search",
                            value = libraryGridColumns,
                            range = LIBRARY_GRID_COLUMNS_MIN..LIBRARY_GRID_COLUMNS_MAX,
                            onValueChange = viewModel::setLibraryGridColumns,
                        )
                    }
                }
                item {
                    SettingsSection("Reading") {
                        SettingsSegmentRow(
                            title = "Reading direction",
                            options = ReadingDirection.entries.map { it.label() },
                            selectedIndex = ReadingDirection.entries.indexOf(readingDirection),
                            onSelect = { viewModel.setReadingDirection(ReadingDirection.entries[it]) },
                        )
                        SettingsDivider()
                        SettingsSegmentRow(
                            title = "Page fit",
                            options = PageFitMode.entries.map { it.label() },
                            selectedIndex = PageFitMode.entries.indexOf(pageFitMode),
                            onSelect = { viewModel.setPageFitMode(PageFitMode.entries[it]) },
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            title = "Mark read on scroll",
                            subtitle = "Auto-mark a chapter read on reaching its last page",
                            checked = markReadOnScroll,
                            onCheckedChange = viewModel::setMarkReadOnScroll,
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            title = "New chapter notifications",
                            subtitle = "Notify when the background library update finds new chapters",
                            checked = newChapterNotificationsEnabled,
                            onCheckedChange = viewModel::setNewChapterNotificationsEnabled,
                        )
                        SettingsDivider()
                        SettingsSliderRow(
                            title = "Mark read at",
                            readout = "$markReadThreshold%",
                            subtitle = "Lower marks a chapter read sooner",
                            value = markReadThreshold,
                            range = MARK_READ_THRESHOLD_MIN..MARK_READ_THRESHOLD_MAX,
                            onValueChange = viewModel::setMarkReadThreshold,
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            title = "Keep screen awake while reading",
                            subtitle = null,
                            checked = keepScreenAwake,
                            onCheckedChange = viewModel::setKeepScreenAwake,
                        )
                    }
                }
                item {
                    SettingsSection("Playback") {
                        SettingsSliderRow(
                            title = "Mark watched at",
                            readout = "${(animeAutoMarkWatchedThreshold * 100).toInt()}%",
                            subtitle = "Of an episode, lower marks it watched sooner",
                            value = (animeAutoMarkWatchedThreshold * 100).toInt(),
                            range = (ANIME_AUTO_MARK_WATCHED_THRESHOLD_MIN * 100).toInt()..(ANIME_AUTO_MARK_WATCHED_THRESHOLD_MAX * 100).toInt(),
                            onValueChange = { percent -> viewModel.setAnimeAutoMarkWatchedThreshold(percent / 100f) },
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            title = "Skip intros and endings",
                            subtitle = "Auto-skip when the source marks them, otherwise a Skip button appears",
                            checked = animeAutoSkipIntro,
                            onCheckedChange = viewModel::setAnimeAutoSkipIntro,
                        )
                        SettingsDivider()
                        // Matched against each stream's title rather than a numeric field:
                        // extensions label variants ("1080p") far more reliably than they fill
                        // in a resolution, and "Auto" (empty) just takes the highest available.
                        SettingsSegmentRow(
                            title = "Preferred quality",
                            options = PREFERRED_QUALITY_LABELS,
                            selectedIndex = PREFERRED_QUALITIES.indexOf(animePreferredQuality).coerceAtLeast(0),
                            onSelect = { viewModel.setAnimePreferredQuality(PREFERRED_QUALITIES[it]) },
                        )
                        SettingsDivider()
                        // Same matching approach as quality: sub/dub is a plain word in the
                        // stream's title alongside the resolution (e.g. "Sub 1080p"). "Any"
                        // (empty) falls back to every stream when nothing matches.
                        SettingsSegmentRow(
                            title = "Preferred audio",
                            options = PREFERRED_AUDIO_OPTIONS.map { it.second },
                            selectedIndex = PREFERRED_AUDIO_OPTIONS.indexOfFirst { it.first == animePreferredAudio }.coerceAtLeast(0),
                            onSelect = { viewModel.setAnimePreferredAudio(PREFERRED_AUDIO_OPTIONS[it].first) },
                        )
                    }
                }
                item {
                    SettingsSection("Subtitles") {
                        SubtitlePreview(textSizePercent = subtitleTextSize, background = subtitleBackgroundStyle)
                        SettingsSliderRow(
                            title = "Text size",
                            readout = "$subtitleTextSize%",
                            value = subtitleTextSize,
                            range = SUBTITLE_TEXT_SIZE_MIN..SUBTITLE_TEXT_SIZE_MAX,
                            onValueChange = viewModel::setSubtitleTextSize,
                        )
                        SettingsSegmentRow(
                            title = "Style",
                            options = listOf("Outline", "Box"),
                            selectedIndex = if (subtitleBackgroundStyle == SubtitleBackgroundStyle.BOX) 1 else 0,
                            onSelect = { index ->
                                viewModel.setSubtitleBackgroundStyle(
                                    if (index == 1) SubtitleBackgroundStyle.BOX else SubtitleBackgroundStyle.OUTLINE,
                                )
                            },
                        )
                    }
                }
                item {
                    SettingsSection("Appearance") {
                        AccentRow(selectedIndex = accentIndex, onSelect = viewModel::setAccentIndex)
                        SettingsDivider()
                        SettingsSegmentRow(
                            title = "Theme",
                            options = ThemePalette.entries.map { it.label() },
                            selectedIndex = ThemePalette.entries.indexOf(themePalette),
                            onSelect = { viewModel.setThemePalette(ThemePalette.entries[it]) },
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            title = "AMOLED true black",
                            subtitle = "Force pure black background, any palette",
                            checked = amoledBlack,
                            onCheckedChange = viewModel::setAmoledBlack,
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            SettingsDivider()
                            SettingsToggleRow(
                                title = "Material You dynamic color",
                                subtitle = "Use your device wallpaper's colors instead of the palette above",
                                checked = dynamicColor,
                                onCheckedChange = viewModel::setDynamicColor,
                            )
                        }
                    }
                }
                item {
                    SettingsSection("App") {
                        SettingsNavRow("B", "Backup and restore", onBackupClick)
                        if (isLoggedIn) {
                            SettingsDivider()
                            SettingsNavRow("T", "Link a TV", onLinkTvClick)
                        }
                        SettingsDivider()
                        UpdateRow(
                            currentVersion = viewModel.currentVersion,
                            state = updateCheckState,
                            downloadState = downloadState,
                            onCheckClick = viewModel::checkForUpdate,
                            onReviewClick = { showUpdateSheet = true },
                            onRetryDownload = { info -> AppUpdateDownloadWorker.start(context, info.downloadUrl) },
                            onInstallReadyClick = { apkPath ->
                                context.startActivity(updateNotifier.buildInstallIntent(java.io.File(apkPath)))
                            },
                        )
                        SettingsDivider()
                        SettingsNavRow("C", "Changelog", onChangelogClick, note = "v${viewModel.currentVersion}")
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(isLoggedIn: Boolean, onClick: () -> Unit) {
    val scale = LocalKawabiScale.current
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accentSoft(0.10f))
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(14.dp * scale.spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp * scale.spacing)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), accent))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isLoggedIn) "K" else "?",
                fontSize = 16.sp * scale.font,
                fontWeight = FontWeight.SemiBold,
                color = NightSession.OnAccent,
            )
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp * scale.spacing)) {
            Text(
                text = if (isLoggedIn) "Synced account" else "Not signed in",
                fontSize = 15.sp * scale.font,
                fontWeight = FontWeight.SemiBold,
                color = NightSession.Text,
            )
            Text(
                text = if (isLoggedIn) "● Library syncing across devices" else "Local library only · tap to sign in",
                fontSize = 12.sp * scale.font,
                color = if (isLoggedIn) NightSession.Read else NightSession.TextDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Chevron()
    }
}

@Composable
private fun UpdateRow(
    currentVersion: String,
    state: UpdateCheckState,
    downloadState: AppUpdateDownloadState,
    onCheckClick: () -> Unit,
    onReviewClick: () -> Unit,
    onRetryDownload: (com.mymonstervr.kawabi.data.update.AppUpdateInfo) -> Unit,
    onInstallReadyClick: (String) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val neutralIconBg = accentSoft(0.14f)
    val danger = NightSession.Danger
    val green = NightSession.Read
    val availableVersion = (state as? UpdateCheckState.Available)?.info?.version

    when (downloadState) {
        is AppUpdateDownloadState.Downloading -> UpdateStatusRow(
            icon = "↓",
            iconBg = accent,
            iconColor = NightSession.OnAccent,
            title = "Downloading ${availableVersion ?: "update"}",
            sub = (if (downloadState.percent >= 0) "${downloadState.percent}% · " else "") + "keeps going in the background",
            subColor = accent,
            tint = accentSoft(0.10f),
            progress = downloadState.percent.takeIf { it >= 0 },
        )
        is AppUpdateDownloadState.ReadyToInstall -> UpdateStatusRow(
            icon = "✓",
            iconBg = accent,
            iconColor = NightSession.OnAccent,
            title = "Ready to install",
            sub = availableVersion?.let { "Version $it downloaded" } ?: "Update downloaded",
            subColor = accent,
            tint = accentSoft(0.10f),
            buttonLabel = "Install",
            buttonFilled = true,
            onButtonClick = { onInstallReadyClick(downloadState.apkPath) },
            onClick = { onInstallReadyClick(downloadState.apkPath) },
        )
        AppUpdateDownloadState.Failed -> UpdateStatusRow(
            icon = "!",
            iconBg = danger.copy(alpha = 0.18f),
            iconColor = danger,
            title = "Download failed",
            sub = "Check your connection and try again",
            subColor = danger,
            tint = danger.copy(alpha = 0.08f),
            buttonLabel = "Retry",
            buttonFilled = false,
            onButtonClick = {
                (state as? UpdateCheckState.Available)?.let { onRetryDownload(it.info) } ?: onCheckClick()
            },
        )
        AppUpdateDownloadState.Idle -> when (state) {
            UpdateCheckState.Idle -> UpdateStatusRow(
                icon = "↻",
                iconBg = neutralIconBg,
                iconColor = accent,
                title = "Check for updates",
                sub = "Version $currentVersion",
                onClick = onCheckClick,
            )
            UpdateCheckState.Checking -> UpdateStatusRow(
                icon = "↻",
                iconBg = neutralIconBg,
                iconColor = accent,
                title = "Checking for updates",
                sub = "Version $currentVersion",
            )
            UpdateCheckState.UpToDate -> UpdateStatusRow(
                icon = "✓",
                iconBg = green.copy(alpha = 0.14f),
                iconColor = green,
                title = "You’re up to date",
                sub = "Version $currentVersion",
                buttonLabel = "Check again",
                buttonFilled = false,
                onButtonClick = onCheckClick,
            )
            is UpdateCheckState.Available -> UpdateStatusRow(
                icon = "↓",
                iconBg = accent,
                iconColor = NightSession.OnAccent,
                title = "Update available",
                sub = "${state.info.version} · tap to review and download",
                subColor = accent,
                tint = accentSoft(0.10f),
                onClick = onReviewClick,
                showChevron = true,
            )
        }
    }
}

@Composable
private fun UpdateStatusRow(
    icon: String,
    iconBg: Color,
    iconColor: Color,
    title: String,
    sub: String,
    modifier: Modifier = Modifier,
    subColor: Color = NightSession.TextDim,
    tint: Color = Color.Transparent,
    buttonLabel: String? = null,
    buttonFilled: Boolean = false,
    onButtonClick: () -> Unit = {},
    onClick: (() -> Unit)? = null,
    progress: Int? = null,
    showChevron: Boolean = false,
) {
    val scale = LocalKawabiScale.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(tint)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp * scale.spacing, vertical = 12.dp * scale.spacing),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp * scale.spacing).clip(RoundedCornerShape(12.dp)).background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = icon, fontSize = 16.sp * scale.font, fontWeight = FontWeight.Bold, color = iconColor)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp * scale.spacing)) {
                Text(text = title, fontSize = 15.sp * scale.font, fontWeight = FontWeight.SemiBold, color = NightSession.Text)
                Text(text = sub, fontSize = 12.sp * scale.font, color = subColor, modifier = Modifier.padding(top = 2.dp))
            }
            if (buttonLabel != null) {
                if (buttonFilled) {
                    GradientActionButton(label = buttonLabel, onClick = onButtonClick, height = 40.dp, corner = 12.dp, fontSize = 13f)
                } else {
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), RoundedCornerShape(12.dp))
                            .clickable(onClick = onButtonClick)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = buttonLabel, fontSize = 13.sp * scale.font, fontWeight = FontWeight.SemiBold, color = NightSession.Text)
                    }
                }
            }
            if (showChevron) Chevron(tint = MaterialTheme.colorScheme.primary)
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp * scale.spacing).height(6.dp).clip(RoundedCornerShape(6.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.1f),
            )
        }
    }
}

private fun ReadingDirection.label(): String = when (this) {
    ReadingDirection.LEFT_TO_RIGHT -> "Left-right"
    ReadingDirection.RIGHT_TO_LEFT -> "Right-left"
    ReadingDirection.VERTICAL -> "Vertical"
}

private fun PageFitMode.label(): String = when (this) {
    PageFitMode.FIT_WIDTH -> "Width"
    PageFitMode.FIT_HEIGHT -> "Height"
    PageFitMode.ORIGINAL -> "Original"
}

private fun ThemePalette.label(): String = when (this) {
    ThemePalette.NIGHT_SESSION -> "Night Session"
    ThemePalette.CATPPUCCIN_MOCHA -> "Catppuccin Mocha"
}

// Approximates the player's actual CaptionStyleCompat rendering (applySubtitleAppearance in
// PlayerScreen.kt) closely enough to judge size/style before opening a real episode --
// exact pixel match isn't the point, just "will this be legible over a bright scene."
@Composable
private fun SubtitlePreview(textSizePercent: Int, background: SubtitleBackgroundStyle) {
    val scale = LocalKawabiScale.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp * scale.spacing)
            .background(Brush.linearGradient(listOf(Color(0xFF0F2B3A), Color(0xFF2F7A78), Color(0xFF0A141C)))),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val textStyle = when (background) {
            SubtitleBackgroundStyle.OUTLINE -> TextStyle(
                color = Color.White,
                fontSize = (16 * textSizePercent / 100).sp,
                shadow = Shadow(color = Color.Black, blurRadius = 6f),
            )
            SubtitleBackgroundStyle.BOX -> TextStyle(color = Color.White, fontSize = (16 * textSizePercent / 100).sp)
        }
        val textModifier = if (background == SubtitleBackgroundStyle.BOX) {
            Modifier
                .padding(bottom = 14.dp * scale.spacing)
                .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        } else {
            Modifier.padding(bottom = 14.dp * scale.spacing)
        }
        Text(text = "Sample subtitle text", style = textStyle, modifier = textModifier)
    }
}

@Composable
private fun AccentRow(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val scale = LocalKawabiScale.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp * scale.spacing, vertical = 6.dp * scale.spacing),
    ) {
        NightSession.Accents.forEachIndexed { index, accent ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp * scale.spacing)
                        .clip(CircleShape)
                        .background(accent.color)
                        .border(2.dp, if (selected) NightSession.Text else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(Icons.Filled.Check, contentDescription = accent.label, tint = NightSession.OnAccent, modifier = Modifier.size(14.dp * scale.spacing))
                    }
                }
            }
        }
    }
}
