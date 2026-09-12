package com.mymonstervr.kawabi.app.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.mymonstervr.kawabi.app.common.BackScaffold
import com.mymonstervr.kawabi.data.usecase.UnmatchedItem
import com.mymonstervr.kawabi.domain.model.TrackStatus
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingServicesScreen(
    onBack: () -> Unit,
    onOpenAnimeSearch: (String) -> Unit,
    viewModel: TrackingServicesViewModel = koinViewModel(),
) {
    val rows by viewModel.rows.collectAsState()
    val animeImport by viewModel.animeImport.collectAsState()
    val kitsuLoggingIn by viewModel.kitsuLoggingIn.collectAsState()
    val kitsuLoginError by viewModel.kitsuLoginError.collectAsState()
    val context = LocalContext.current
    var showKitsuDialog by remember { mutableStateOf(false) }

    LaunchedEffect(rows) {
        val kitsuConnected = rows.any { it.id == com.mymonstervr.kawabi.data.network.TrackerTokenStore.TRACKER_KITSU && it.connected }
        if (kitsuConnected) showKitsuDialog = false
    }

    if (showKitsuDialog) {
        KitsuLoginDialog(
            isLoading = kitsuLoggingIn,
            error = kitsuLoginError,
            onDismiss = { showKitsuDialog = false; viewModel.clearKitsuError() },
            onSubmit = { email, password -> viewModel.kitsuLogin(email, password) },
        )
    }

    animeImport?.let { state ->
        AnimeImportDialog(
            state = state,
            onToggleStatus = viewModel::toggleAnimeImportStatus,
            onConfirm = viewModel::runAnimeImport,
            onDismiss = viewModel::dismissAnimeImport,
            onUnmatchedClick = { item ->
                viewModel.dismissAnimeImport()
                onOpenAnimeSearch(item.title)
            },
        )
    }

    BackScaffold(title = "Tracking services", onBack = onBack) {
        LazyColumn(modifier = Modifier.background(NightSession.Background)) {
            items(rows, key = { it.id }) { row ->
                TrackerRow(
                    row = row,
                    onConnect = {
                        // Browser-OAuth trackers (MAL, AniList) hand off to the system browser
                        // and come back through MainActivity's kawabi:// redirect handler;
                        // Kitsu is the in-app form instead. No per-tracker branch needed.
                        val authUrl = if (row.browserLogin) viewModel.authUrlFor(row.id) else null
                        if (authUrl != null) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, authUrl))
                        } else {
                            showKitsuDialog = true
                        }
                    },
                    onDisconnect = { viewModel.logout(row.id) },
                    onImportAnime = { viewModel.startAnimeImport(row.id, row.name) },
                )
                HorizontalDivider(color = NightSession.Hairline)
            }
        }
    }
}

@Composable
private fun TrackerRow(
    row: TrackerRowState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onImportAnime: () -> Unit,
) {
    val scale = LocalKawabiScale.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp * scale.spacing, vertical = 12.dp * scale.spacing)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = row.name, fontSize = 12.sp * scale.font, fontWeight = FontWeight.SemiBold, color = NightSession.Text)
                Text(
                    text = if (row.connected) row.userName ?: "Connected" else "Not connected",
                    fontSize = 10.5.sp * scale.font,
                    color = NightSession.TextDim,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (row.connected) {
                TextButton(onClick = onDisconnect) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error, fontSize = 11.sp * scale.font)
                }
            } else {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = NightSession.OnAccent,
                    ),
                ) {
                    Text("Connect", fontSize = 11.sp * scale.font, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (row.connected) {
            TextButton(onClick = onImportAnime) {
                Text("Import anime list", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp * scale.font)
            }
        }
    }
}

private val ANIME_IMPORT_STATUS_LABELS = listOf(
    TrackStatus.WATCHING to "Watching",
    TrackStatus.PLAN_TO_WATCH to "Plan to watch",
    TrackStatus.COMPLETED to "Completed",
    TrackStatus.ON_HOLD to "On hold",
    TrackStatus.DROPPED to "Dropped",
)

@Composable
private fun AnimeImportDialog(
    state: AnimeImportState,
    onToggleStatus: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onUnmatchedClick: (UnmatchedItem) -> Unit,
) {
    val scale = LocalKawabiScale.current
    val summary = state.summary

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NightSession.Chip,
        title = {
            Text(
                text = if (summary != null) "Import finished" else "Import ${state.trackerName} anime list",
                color = NightSession.Text,
                fontSize = 14.sp * scale.font,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when {
                    state.running -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Matching your list against the anime sources. This can take a couple of minutes.",
                            color = NightSession.TextDim,
                            fontSize = 11.sp * scale.font,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    summary != null -> {
                        Text(
                            text = "Imported ${summary.imported} \u00b7 Already in library ${summary.alreadyPresent} \u00b7 " +
                                "Unmatched ${summary.unmatched.size}",
                            color = NightSession.Text,
                            fontSize = 11.5.sp * scale.font,
                        )
                        if (summary.truncated) {
                            Text(
                                text = "Some items skipped (timeout) \u2014 run the import again to pick up the rest.",
                                color = NightSession.TextDim,
                                fontSize = 10.5.sp * scale.font,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        if (summary.unmatched.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No source match \u2014 tap to search:",
                                color = NightSession.TextDim,
                                fontSize = 10.5.sp * scale.font,
                            )
                            summary.unmatched.forEach { item ->
                                Text(
                                    text = item.title.ifBlank { item.remoteId },
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp * scale.font,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onUnmatchedClick(item) }
                                        .padding(vertical = 6.dp),
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = "Which statuses should be imported?",
                            color = NightSession.TextDim,
                            fontSize = 11.sp * scale.font,
                        )
                        ANIME_IMPORT_STATUS_LABELS.forEach { (status, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { onToggleStatus(status) },
                            ) {
                                Checkbox(
                                    checked = status in state.statuses,
                                    onCheckedChange = { onToggleStatus(status) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = NightSession.TextDim,
                                        checkmarkColor = NightSession.OnAccent,
                                    ),
                                )
                                Text(text = label, color = NightSession.Text, fontSize = 11.5.sp * scale.font)
                            }
                        }
                    }
                }
                if (state.error != null) {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp * scale.font,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (summary == null) {
                TextButton(onClick = onConfirm, enabled = !state.running && state.statuses.isNotEmpty()) {
                    Text("Import", color = MaterialTheme.colorScheme.primary)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Done", color = MaterialTheme.colorScheme.primary) }
            }
        },
        dismissButton = {
            if (summary == null) {
                TextButton(onClick = onDismiss, enabled = !state.running) {
                    Text("Cancel", color = NightSession.TextDim)
                }
            }
        },
    )
}

@Composable
private fun KitsuLoginDialog(
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NightSession.Chip,
        title = { Text("Connect Kitsu", color = NightSession.Text) },
        text = {
            Column {
                TextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("Email", color = NightSession.TextDim) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = NightSession.Background,
                        unfocusedContainerColor = NightSession.Background,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedTextColor = NightSession.Text,
                        unfocusedTextColor = NightSession.Text,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Password", color = NightSession.TextDim) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = NightSession.Background,
                        unfocusedContainerColor = NightSession.Background,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedTextColor = NightSession.Text,
                        unfocusedTextColor = NightSession.Text,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(email, password) },
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("Log in", color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = NightSession.TextDim) } },
    )
}
