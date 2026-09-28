package com.mymonstervr.kawabi.app.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.mymonstervr.kawabi.app.common.BackScaffold
import com.mymonstervr.kawabi.app.theme.NightSession
import org.koin.androidx.compose.koinViewModel

/**
 * Google Play Services' own code-scanner UI (no CAMERA permission needed) -- see
 * PairingApi/LinkTvViewModel for the state machine this drives.
 */
@Composable
fun LinkTvScreen(
    onBack: () -> Unit,
    viewModel: LinkTvViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var manualCode by remember { mutableStateOf("") }
    var scanError by remember { mutableStateOf<String?>(null) }

    val scannerOptions = remember {
        GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    }

    fun launchScan() {
        scanError = null
        GmsBarcodeScanning.getClient(context, scannerOptions).startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let(viewModel::onScanned) }
            .addOnFailureListener { e -> scanError = e.message ?: "Scan failed -- try again or enter the code manually" }
    }

    BackScaffold(title = "Link a TV", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val currentState = state) {
                LinkTvState.Idle -> {
                    Text("Scan the code shown on your TV", fontWeight = FontWeight.SemiBold, color = NightSession.Text, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Open kawabi on your TV, then scan its QR code or type the code it shows below.",
                        color = NightSession.TextDim,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { launchScan() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Text("Scan QR code") }
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = manualCode,
                        onValueChange = { manualCode = it },
                        label = { Text("Or enter the code manually") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { if (manualCode.isNotBlank()) viewModel.onManualCode(manualCode) },
                        enabled = manualCode.isNotBlank(),
                    ) { Text("Continue") }
                    scanError?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
                is LinkTvState.Confirm -> {
                    Text("Link this TV?", fontWeight = FontWeight.Bold, color = NightSession.Text, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Code: ${currentState.displayCode}", color = NightSession.TextDim, fontSize = 14.sp)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = viewModel::cancel) { Text("Cancel") }
                        Button(
                            onClick = viewModel::confirm,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) { Text("Link TV") }
                    }
                }
                LinkTvState.Approving -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Linking...", color = NightSession.TextDim)
                }
                is LinkTvState.Done -> {
                    Text("TV linked", fontWeight = FontWeight.Bold, color = NightSession.Text, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${currentState.deviceName.ifBlank { "Your TV" }} is now signed in.",
                        color = NightSession.TextDim,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Text("Done") }
                }
                is LinkTvState.Error -> {
                    Text("Couldn't link the TV", fontWeight = FontWeight.Bold, color = NightSession.Text, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(currentState.message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = viewModel::reset) { Text("Try again") }
                }
            }
        }
    }
}
