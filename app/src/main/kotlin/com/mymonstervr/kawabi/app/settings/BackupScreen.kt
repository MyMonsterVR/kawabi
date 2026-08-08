package com.mymonstervr.kawabi.app.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.common.BackScaffold
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = koinViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            viewModel.export { json ->
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (json != null) viewModel.import(json)
        }
    }

    LaunchedEffect(state) {
        if (state is BackupOpState.Success || state is BackupOpState.Error) {
            kotlinx.coroutines.delay(4000)
            viewModel.clearState()
        }
    }

    BackScaffold(title = "Backup & Restore", onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "Exports your library, categories, and read/progress state as a JSON file. Doesn't include account/tracker login.",
                fontSize = 11.5.sp * LocalKawabiScale.current.font,
                color = NightSession.TextDim,
            )
            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { exportLauncher.launch(defaultBackupFileName()) },
                enabled = state !is BackupOpState.Running,
                shape = RoundedCornerShape(NightSession.RadiusMd),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = NightSession.OnAccent),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export backup", fontWeight = FontWeight.Bold) }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                enabled = state !is BackupOpState.Running,
                shape = RoundedCornerShape(NightSession.RadiusMd),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NightSession.Text),
                border = androidx.compose.foundation.BorderStroke(1.dp, NightSession.Hairline),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import backup") }

            Spacer(modifier = Modifier.height(16.dp))

            when (val current = state) {
                is BackupOpState.Running -> Text("Working…", color = NightSession.TextDim)
                is BackupOpState.Success -> Text(current.message, color = NightSession.Read)
                is BackupOpState.Error -> Text(current.message, color = MaterialTheme.colorScheme.error)
                BackupOpState.Idle -> {}
            }
        }
    }
}

private fun defaultBackupFileName(): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
    return "kawabi-backup-$stamp.json"
}
