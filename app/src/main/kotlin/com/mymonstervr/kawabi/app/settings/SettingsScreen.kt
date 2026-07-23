package com.mymonstervr.kawabi.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.app.update.AppUpdateDownloadWorker
import com.mymonstervr.kawabi.app.update.AppUpdateInfo
import com.mymonstervr.kawabi.data.settings.LibraryCardSize
import com.mymonstervr.kawabi.data.settings.ReadingDirection
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onAccountClick: () -> Unit,
    onSourcesClick: () -> Unit,
    onBackupClick: () -> Unit,
    onTrackingClick: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val readingDirection by viewModel.readingDirection.collectAsState()
    val markReadOnScroll by viewModel.markReadOnScroll.collectAsState()
    val keepScreenAwake by viewModel.keepScreenAwake.collectAsState()
    val accentIndex by viewModel.accentIndex.collectAsState()
    val libraryCardSize by viewModel.libraryCardSize.collectAsState()
    val updateCheckState by viewModel.updateCheckState.collectAsState()
    val context = LocalContext.current

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
                SettingsRow(
                    title = "Account",
                    subtitle = if (isLoggedIn) "Synced" else "Not logged in / Local library only",
                    onClick = onAccountClick,
                )
                HorizontalDivider(color = NightSession.Hairline)
                SettingsRow(title = "Sources", subtitle = "Enable or disable catalog sources", onClick = onSourcesClick)
                HorizontalDivider(color = NightSession.Hairline)
                SettingsRow(title = "Backup & Restore", subtitle = "Export or import your library as JSON", onClick = onBackupClick)
                HorizontalDivider(color = NightSession.Hairline)
                SettingsRow(title = "Tracking services", subtitle = "Connect MyAnimeList or Kitsu", onClick = onTrackingClick)
                HorizontalDivider(color = NightSession.Hairline)
            }
            item { SettingsGroupLabel("Appearance") }
            item {
                AccentRow(selectedIndex = accentIndex, onSelect = viewModel::setAccentIndex)
                HorizontalDivider(color = NightSession.Hairline, modifier = Modifier.padding(top = 8.dp))
            }
            item { SettingsGroupLabel("Library") }
            items(LibraryCardSize.entries) { size ->
                SettingsRadioRow(
                    label = size.label(),
                    selected = size == libraryCardSize,
                    onClick = { viewModel.setLibraryCardSize(size) },
                )
            }
            item { HorizontalDivider(color = NightSession.Hairline, modifier = Modifier.padding(top = 8.dp)) }
            item { SettingsGroupLabel("Reading direction") }
            items(ReadingDirection.entries) { direction ->
                SettingsRadioRow(
                    label = direction.label(),
                    selected = direction == readingDirection,
                    onClick = { viewModel.setReadingDirection(direction) },
                )
            }
            item {
                HorizontalDivider(color = NightSession.Hairline)
                SettingsSwitchRow(
                    title = "Mark read on scroll",
                    subtitle = "Auto-mark a chapter read on reaching its last page",
                    checked = markReadOnScroll,
                    onCheckedChange = viewModel::setMarkReadOnScroll,
                )
                HorizontalDivider(color = NightSession.Hairline)
                SettingsSwitchRow(
                    title = "Keep screen awake while reading",
                    subtitle = null,
                    checked = keepScreenAwake,
                    onCheckedChange = viewModel::setKeepScreenAwake,
                )
            }
            item { SettingsGroupLabel("About") }
            item {
                HorizontalDivider(color = NightSession.Hairline)
                UpdateRow(
                    currentVersion = viewModel.currentVersion,
                    state = updateCheckState,
                    onCheckClick = viewModel::checkForUpdate,
                    onInstallClick = { info -> AppUpdateDownloadWorker.start(context, info.downloadUrl) },
                )
            }
        }
        }
    }
}

@Composable
private fun UpdateRow(
    currentVersion: String,
    state: UpdateCheckState,
    onCheckClick: () -> Unit,
    onInstallClick: (AppUpdateInfo) -> Unit,
) {
    val (subtitle, onClick) = when (state) {
        UpdateCheckState.Idle -> "Version $currentVersion" to onCheckClick
        UpdateCheckState.Checking -> "Checking..." to ({})
        UpdateCheckState.UpToDate -> "Up to date ($currentVersion)" to onCheckClick
        is UpdateCheckState.Available -> "Update available: ${state.info.version} -- tap to download" to { onInstallClick(state.info) }
    }
    SettingsRow(title = "Check for updates", subtitle = subtitle, onClick = onClick)
}

private fun ReadingDirection.label(): String = when (this) {
    ReadingDirection.LEFT_TO_RIGHT -> "Left-to-right"
    ReadingDirection.RIGHT_TO_LEFT -> "Right-to-left"
    ReadingDirection.VERTICAL -> "Vertical scroll"
}

private fun LibraryCardSize.label(): String = when (this) {
    LibraryCardSize.SMALL -> "Small"
    LibraryCardSize.MEDIUM -> "Medium"
    LibraryCardSize.LARGE -> "Large"
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
private fun SettingsRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val scale = LocalKawabiScale.current
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp * scale.spacing, vertical = 13.dp * scale.spacing)) {
        Text(text = title, fontSize = 12.sp * scale.font, fontWeight = FontWeight.SemiBold, color = NightSession.Text)
        if (subtitle != null) {
            Text(text = subtitle, fontSize = 10.5.sp * scale.font, color = NightSession.TextDim, modifier = Modifier.padding(top = 1.dp))
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
