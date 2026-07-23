package com.mymonstervr.kawabi.app.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingServicesScreen(onBack: () -> Unit, viewModel: TrackingServicesViewModel = koinViewModel()) {
    val rows by viewModel.rows.collectAsState()
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

    Scaffold(
        containerColor = NightSession.Background,
        topBar = {
            TopAppBar(
                title = { Text("Tracking services", fontWeight = FontWeight.Bold, color = NightSession.Text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NightSession.Background),
            )
        },
    ) { padding ->
        com.mymonstervr.kawabi.app.common.ResponsiveContainer(modifier = Modifier.padding(padding)) {
            LazyColumn(modifier = Modifier.background(NightSession.Background)) {
                items(rows, key = { it.id }) { row ->
                    TrackerRow(
                        row = row,
                        onConnect = {
                            when (row.id) {
                                com.mymonstervr.kawabi.data.network.TrackerTokenStore.TRACKER_MAL -> {
                                    val intent = Intent(Intent.ACTION_VIEW, viewModel.malAuthUrl())
                                    context.startActivity(intent)
                                }
                                com.mymonstervr.kawabi.data.network.TrackerTokenStore.TRACKER_KITSU -> showKitsuDialog = true
                            }
                        },
                        onDisconnect = { viewModel.logout(row.id) },
                    )
                    HorizontalDivider(color = NightSession.Hairline)
                }
            }
        }
    }
}

@Composable
private fun TrackerRow(row: TrackerRowState, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val scale = LocalKawabiScale.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp * scale.spacing, vertical = 12.dp * scale.spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = NightSession.OnAccent),
            ) {
                Text("Connect", fontSize = 11.sp * scale.font, fontWeight = FontWeight.Bold)
            }
        }
    }
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
