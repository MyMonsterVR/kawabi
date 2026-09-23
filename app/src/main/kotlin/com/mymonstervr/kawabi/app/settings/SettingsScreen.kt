package com.mymonstervr.kawabi.app.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.app.update.AppUpdateDownloadWorker
import com.mymonstervr.kawabi.app.update.AppUpdateInfo
import com.mymonstervr.kawabi.data.settings.LIBRARY_GRID_COLUMNS_MAX
import com.mymonstervr.kawabi.data.settings.LIBRARY_GRID_COLUMNS_MIN
import com.mymonstervr.kawabi.data.settings.MARK_READ_THRESHOLD_MAX
import com.mymonstervr.kawabi.data.settings.MARK_READ_THRESHOLD_MIN
import com.mymonstervr.kawabi.data.settings.PageFitMode
import com.mymonstervr.kawabi.data.settings.ANIME_AUTO_MARK_WATCHED_THRESHOLD_MAX
import com.mymonstervr.kawabi.data.settings.ANIME_AUTO_MARK_WATCHED_THRESHOLD_MIN
import com.mymonstervr.kawabi.data.settings.ReadingDirection
import com.mymonstervr.kawabi.data.settings.SUBTITLE_TEXT_SIZE_MAX
import com.mymonstervr.kawabi.data.settings.SUBTITLE_TEXT_SIZE_MIN
import com.mymonstervr.kawabi.data.settings.SubtitleBackgroundStyle
import com.mymonstervr.kawabi.data.settings.ThemePalette
import org.koin.androidx.compose.koinViewModel

private val PREFERRED_QUALITIES = listOf("", "1080p", "720p", "480p")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onAccountClick: () -> Unit,
    onSourcesClick: () -> Unit,
    onAnimeSourcesClick: () -> Unit,
    onBackupClick: () -> Unit,
    onTrackingClick: () -> Unit,
    onChangelogClick: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val readingDirection by viewModel.readingDirection.collectAsState()
    val markReadOnScroll by viewModel.markReadOnScroll.collectAsState()
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
    val animeAutoSkipIntro by viewModel.animeAutoSkipIntro.collectAsState()
    val subtitleTextSize by viewModel.subtitleTextSize.collectAsState()
    val subtitleBackgroundStyle by viewModel.subtitleBackgroundStyle.collectAsState()
    val expiredTrackerCount by viewModel.expiredTrackerCount.collectAsState()
    val context = LocalContext.current
    val updateNotifier = org.koin.compose.koinInject<com.mymonstervr.kawabi.app.update.AppUpdateNotifier>()

    Scaffold(
        containerColor = NightSession.Background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = NightSession.Text) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NightSession.Background),
            )
        },
    ) { padding ->
        com.mymonstervr.kawabi.app.common.ResponsiveContainer(modifier = Modifier.padding(padding)) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.background(NightSession.Background),
        ) {
            item {
                SettingsGroup("Account") {
                    SettingsRow(
                        title = "Account",
                        subtitle = if (isLoggedIn) "Synced" else "Not logged in / Local library only",
                        onClick = onAccountClick,
                    )
                    HorizontalDivider(color = NightSession.Hairline)
                    SettingsRow(title = "Sources", subtitle = "Enable or disable catalog sources", onClick = onSourcesClick)
                    HorizontalDivider(color = NightSession.Hairline)
                    SettingsRow(title = "Anime sources", subtitle = "Enable or disable anime sources", onClick = onAnimeSourcesClick)
                    HorizontalDivider(color = NightSession.Hairline)
                    SettingsRow(title = "Backup & Restore", subtitle = "Export or import your library as JSON", onClick = onBackupClick)
                    HorizontalDivider(color = NightSession.Hairline)
                    SettingsRow(
                        title = "Tracking services",
                        subtitle = if (expiredTrackerCount > 0) {
                            if (expiredTrackerCount == 1) "1 tracker needs reconnect" else "$expiredTrackerCount trackers need reconnect"
                        } else {
                            "Connect MyAnimeList or Kitsu"
                        },
                        subtitleColor = if (expiredTrackerCount > 0) MaterialTheme.colorScheme.error else null,
                        onClick = onTrackingClick,
                    )
                }
            }
            item {
                SettingsGroup("Appearance") {
                    AccentRow(selectedIndex = accentIndex, onSelect = viewModel::setAccentIndex)
                    HorizontalDivider(color = NightSession.Hairline)
                    ThemePalette.entries.forEach { palette ->
                        SettingsRadioRow(
                            label = palette.label(),
                            selected = palette == themePalette,
                            onClick = { viewModel.setThemePalette(palette) },
                        )
                    }
                    HorizontalDivider(color = NightSession.Hairline)
                    SettingsSwitchRow(
                        title = "AMOLED true black",
                        subtitle = "Force pure black background, any palette",
                        checked = amoledBlack,
                        onCheckedChange = viewModel::setAmoledBlack,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        HorizontalDivider(color = NightSession.Hairline)
                        SettingsSwitchRow(
                            title = "Material You dynamic color",
                            subtitle = "Use your device wallpaper's colors instead of the palette above",
                            checked = dynamicColor,
                            onCheckedChange = viewModel::setDynamicColor,
                        )
                    }
                }
            }
            item {
                SettingsGroup("Library") {
                    SettingsSliderRow(
                        title = "Grid columns",
                        subtitle = "$libraryGridColumns per row -- applies to Library and Search",
                        value = libraryGridColumns,
                        range = LIBRARY_GRID_COLUMNS_MIN..LIBRARY_GRID_COLUMNS_MAX,
                        onValueChange = viewModel::setLibraryGridColumns,
                    )
                }
            }
            item {
                SettingsGroup("Reading direction") {
                    ReadingDirection.entries.forEachIndexed { index, direction ->
                        if (index > 0) HorizontalDivider(color = NightSession.Hairline)
                        SettingsRadioRow(
                            label = direction.label(),
                            selected = direction == readingDirection,
                            onClick = { viewModel.setReadingDirection(direction) },
                        )
                    }
                }
            }
            item {
                SettingsGroup("Page fit") {
                    PageFitMode.entries.forEachIndexed { index, fitMode ->
                        if (index > 0) HorizontalDivider(color = NightSession.Hairline)
                        SettingsRadioRow(
                            label = fitMode.label(),
                            selected = fitMode == pageFitMode,
                            onClick = { viewModel.setPageFitMode(fitMode) },
                        )
                    }
                }
            }
            item {
                SettingsGroup("Behavior") {
                    SettingsSwitchRow(
                        title = "Mark read on scroll",
                        subtitle = "Auto-mark a chapter read on reaching its last page",
                        checked = markReadOnScroll,
                        onCheckedChange = viewModel::setMarkReadOnScroll,
                    )
                    HorizontalDivider(color = NightSession.Hairline)
                    SettingsSliderRow(
                        title = "Mark read at",
                        subtitle = "$markReadThreshold% scrolled -- lower marks a chapter read sooner",
                        value = markReadThreshold,
                        range = MARK_READ_THRESHOLD_MIN..MARK_READ_THRESHOLD_MAX,
                        onValueChange = viewModel::setMarkReadThreshold,
                    )
                    HorizontalDivider(color = NightSession.Hairline)
                    SettingsSwitchRow(
                        title = "Keep screen awake while reading",
                        subtitle = null,
                        checked = keepScreenAwake,
                        onCheckedChange = viewModel::setKeepScreenAwake,
                    )
                }
            }
            item {
                SettingsGroup("Playback") {
                    SettingsSliderRow(
                        title = "Mark watched at",
                        subtitle = "${(animeAutoMarkWatchedThreshold * 100).toInt()}% of an episode -- lower marks it watched sooner",
                        value = (animeAutoMarkWatchedThreshold * 100).toInt(),
                        range = (ANIME_AUTO_MARK_WATCHED_THRESHOLD_MIN * 100).toInt()..(ANIME_AUTO_MARK_WATCHED_THRESHOLD_MAX * 100).toInt(),
                        onValueChange = { percent -> viewModel.setAnimeAutoMarkWatchedThreshold(percent / 100f) },
                    )
                    HorizontalDivider(color = NightSession.Hairline)
                    SettingsSwitchRow(
                        title = "Skip intros and endings",
                        subtitle = "Auto-skip when the source marks them -- otherwise a Skip button appears",
                        checked = animeAutoSkipIntro,
                        onCheckedChange = viewModel::setAnimeAutoSkipIntro,
                    )
                }
            }
            item {
                SettingsGroup("Subtitles") {
                    SubtitlePreview(textSizePercent = subtitleTextSize, background = subtitleBackgroundStyle)
                    HorizontalDivider(color = NightSession.Hairline)
                    SettingsSliderRow(
                        title = "Text size",
                        subtitle = "$subtitleTextSize% of default",
                        value = subtitleTextSize,
                        range = SUBTITLE_TEXT_SIZE_MIN..SUBTITLE_TEXT_SIZE_MAX,
                        onValueChange = viewModel::setSubtitleTextSize,
                    )
                    HorizontalDivider(color = NightSession.Hairline)
                    SettingsRadioRow(
                        label = "Outline (no background)",
                        selected = subtitleBackgroundStyle == SubtitleBackgroundStyle.OUTLINE,
                        onClick = { viewModel.setSubtitleBackgroundStyle(SubtitleBackgroundStyle.OUTLINE) },
                    )
                    HorizontalDivider(color = NightSession.Hairline)
                    SettingsRadioRow(
                        label = "Solid background box",
                        selected = subtitleBackgroundStyle == SubtitleBackgroundStyle.BOX,
                        onClick = { viewModel.setSubtitleBackgroundStyle(SubtitleBackgroundStyle.BOX) },
                    )
                }
            }
            item {
                SettingsGroup("Preferred quality") {
                    // Matched against each stream's title rather than a numeric field:
                    // extensions label variants ("1080p") far more reliably than they fill
                    // in a resolution, and "Auto" (empty) just takes the highest available.
                    PREFERRED_QUALITIES.forEachIndexed { index, quality ->
                        if (index > 0) HorizontalDivider(color = NightSession.Hairline)
                        SettingsRadioRow(
                            label = quality.ifBlank { "Auto (best available)" },
                            selected = quality == animePreferredQuality,
                            onClick = { viewModel.setAnimePreferredQuality(quality) },
                        )
                    }
                }
            }
            item {
                SettingsGroup("About") {
                    UpdateRow(
                        currentVersion = viewModel.currentVersion,
                        state = updateCheckState,
                        downloadState = downloadState,
                        onCheckClick = viewModel::checkForUpdate,
                        onInstallClick = { info -> AppUpdateDownloadWorker.start(context, info.downloadUrl) },
                        onInstallReadyClick = { apkPath ->
                            context.startActivity(updateNotifier.buildInstallIntent(java.io.File(apkPath)))
                        },
                    )
                    HorizontalDivider(color = NightSession.Hairline)
                    SettingsRow(title = "Changelog", subtitle = "What's changed in each version", onClick = onChangelogClick)
                }
            }
        }
        }
    }
}

@Composable
private fun UpdateRow(
    currentVersion: String,
    state: UpdateCheckState,
    downloadState: com.mymonstervr.kawabi.app.update.AppUpdateDownloadState,
    onCheckClick: () -> Unit,
    onInstallClick: (AppUpdateInfo) -> Unit,
    onInstallReadyClick: (String) -> Unit,
) {
    val (subtitle, onClick, progressPercent) = when (downloadState) {
        is com.mymonstervr.kawabi.app.update.AppUpdateDownloadState.Downloading ->
            Triple(
                if (downloadState.percent >= 0) "Downloading update -- ${downloadState.percent}%" else "Downloading update...",
                {},
                downloadState.percent,
            )
        is com.mymonstervr.kawabi.app.update.AppUpdateDownloadState.ReadyToInstall ->
            Triple(
                "Update downloaded -- tap to install",
                { onInstallReadyClick(downloadState.apkPath) },
                -1,
            )
        com.mymonstervr.kawabi.app.update.AppUpdateDownloadState.Failed ->
            Triple("Update download failed -- tap to retry", onCheckClick, -1)
        com.mymonstervr.kawabi.app.update.AppUpdateDownloadState.Idle -> when (state) {
            UpdateCheckState.Idle -> Triple("Version $currentVersion", onCheckClick, -1)
            UpdateCheckState.Checking -> Triple("Checking...", {}, -1)
            UpdateCheckState.UpToDate -> Triple("Up to date ($currentVersion)", onCheckClick, -1)
            is UpdateCheckState.Available -> Triple(
                "Update available: ${state.info.version} -- tap to download",
                { onInstallClick(state.info) },
                -1,
            )
        }
    }
    val scale = LocalKawabiScale.current
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp * scale.spacing, vertical = 13.dp * scale.spacing)) {
        Text(text = "Check for updates", fontSize = 12.sp * scale.font, fontWeight = FontWeight.SemiBold, color = NightSession.Text)
        Text(text = subtitle, fontSize = 10.5.sp * scale.font, color = NightSession.TextDim, modifier = Modifier.padding(top = 1.dp))
        if (progressPercent >= 0) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progressPercent / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = NightSession.Chip,
            )
        }
    }
}

private fun ReadingDirection.label(): String = when (this) {
    ReadingDirection.LEFT_TO_RIGHT -> "Left-to-right"
    ReadingDirection.RIGHT_TO_LEFT -> "Right-to-left"
    ReadingDirection.VERTICAL -> "Vertical scroll"
}

private fun PageFitMode.label(): String = when (this) {
    PageFitMode.FIT_WIDTH -> "Fit width"
    PageFitMode.FIT_HEIGHT -> "Fit height"
    PageFitMode.ORIGINAL -> "Original size"
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
            .height(90.dp * scale.spacing)
            .padding(horizontal = 16.dp * scale.spacing, vertical = 10.dp * scale.spacing)
            .clip(RoundedCornerShape(NightSession.RadiusSm))
            .background(Color(0xFF3A3A3A)),
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
                .padding(bottom = 10.dp * scale.spacing)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        } else {
            Modifier.padding(bottom = 10.dp * scale.spacing)
        }
        Text(text = "Sample subtitle text", style = textStyle, modifier = textModifier)
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    subtitle: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    val scale = LocalKawabiScale.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp * scale.spacing, vertical = 9.dp * scale.spacing)) {
        Text(text = title, fontSize = 12.sp * scale.font, fontWeight = FontWeight.SemiBold, color = NightSession.Text)
        Text(
            text = subtitle,
            fontSize = 10.5.sp * scale.font,
            color = NightSession.TextDim,
            modifier = Modifier.padding(top = 1.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = NightSession.Chip,
            ),
        )
    }
}

// Card fill is NightSession.Cover, not .Chip -- .Chip is also the Slider inactive-track /
// Switch unchecked-track color (see GridColumnsRow/SettingsSwitchRow below), so using it as
// the card background too would wash those controls out to near-invisible against their own
// container. Cover is already a distinct "raised surface" token (MaterialTheme's
// surfaceVariant), unused elsewhere in this screen.
@Composable
private fun SettingsGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    val scale = LocalKawabiScale.current
    Column(modifier = Modifier.padding(bottom = 16.dp * scale.spacing)) {
        SettingsGroupLabel(label)
        Surface(
            shape = RoundedCornerShape(NightSession.RadiusMd),
            color = NightSession.Cover,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp * scale.spacing),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsGroupLabel(text: String) {
    val scale = LocalKawabiScale.current
    Text(
        text = text.uppercase(),
        fontSize = 10.5.sp * scale.font,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = NightSession.TextDim,
        modifier = Modifier.padding(horizontal = 16.dp * scale.spacing, vertical = 8.dp * scale.spacing),
    )
}

@Composable
private fun AccentRow(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val scale = LocalKawabiScale.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp * scale.spacing),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp * scale.spacing, vertical = 6.dp * scale.spacing),
    ) {
        NightSession.Accents.forEachIndexed { index, accent ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .size(28.dp * scale.spacing)
                    .clip(CircleShape)
                    .background(accent.color)
                    .border(2.dp, if (selected) NightSession.Text else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(Icons.Filled.Check, contentDescription = accent.label, tint = NightSession.OnAccent, modifier = Modifier.size(14.dp * scale.spacing))
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String?, onClick: () -> Unit, subtitleColor: androidx.compose.ui.graphics.Color? = null) {
    val scale = LocalKawabiScale.current
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp * scale.spacing, vertical = 13.dp * scale.spacing)) {
        Text(text = title, fontSize = 12.sp * scale.font, fontWeight = FontWeight.SemiBold, color = NightSession.Text)
        if (subtitle != null) {
            Text(text = subtitle, fontSize = 10.5.sp * scale.font, color = subtitleColor ?: NightSession.TextDim, modifier = Modifier.padding(top = 1.dp))
        }
    }
}

@Composable
private fun SettingsRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val scale = LocalKawabiScale.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp * scale.spacing, vertical = 9.dp * scale.spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = NightSession.TextDim,
            ),
        )
        Text(text = label, fontSize = 12.sp * scale.font, color = NightSession.Text, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun SettingsSwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val scale = LocalKawabiScale.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp * scale.spacing, vertical = 9.dp * scale.spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 12.sp * scale.font, fontWeight = FontWeight.SemiBold, color = NightSession.Text)
            if (subtitle != null) {
                Text(text = subtitle, fontSize = 10.5.sp * scale.font, color = NightSession.TextDim, modifier = Modifier.padding(top = 1.dp))
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NightSession.OnAccent,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = NightSession.TextDim,
                uncheckedTrackColor = NightSession.Chip,
                uncheckedBorderColor = NightSession.Hairline,
            ),
        )
    }
}
