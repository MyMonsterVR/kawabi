package com.mymonstervr.kawabi.tv.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mymonstervr.kawabi.tv.theme.TvColors
import com.mymonstervr.kawabi.tv.theme.TvDimens
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun PairingScreen(viewModel: PairingViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Stop polling while backgrounded (app switch, screensaver) -- resumes with whatever
    // code/countdown was already showing, no wasted request.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.pause()
                Lifecycle.Event.ON_START -> viewModel.resume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(horizontal = TvDimens.OverscanHorizontal, vertical = TvDimens.OverscanVertical),
        contentAlignment = Alignment.Center,
    ) {
        when (val currentState = state) {
            PairingUiState.Requesting -> RequestingContent()
            is PairingUiState.Showing -> ShowingContent(currentState, onGetNewCode = viewModel::retry)
            PairingUiState.Approved -> ApprovedContent()
            PairingUiState.IdleTimeout -> IdleTimeoutContent(onGetNewCode = viewModel::retry)
            is PairingUiState.Error -> ErrorContent(currentState.message, onRetry = viewModel::retry)
        }
    }
}

@Composable
private fun RequestingContent() {
    Text("Getting a code...", color = TvColors.TextDim, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun ApprovedContent() {
    Text("Linked -- signing in...", color = TvColors.Text, style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun IdleTimeoutContent(onGetNewCode: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Still there?",
            color = TvColors.Text,
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "This code timed out after a few tries. Press OK for a new one.",
            color = TvColors.TextDim,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(28.dp))
        FocusedGetNewCodeButton(onClick = onGetNewCode)
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Couldn't connect", color = TvColors.Text, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(message, color = TvColors.TextDim, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(28.dp))
        FocusedGetNewCodeButton(onClick = onRetry, label = "Try again")
    }
}

// D-pad must never land on nothing -- every state here has exactly one focusable element,
// so it claims initial focus as soon as it's composed.
@Composable
private fun FocusedGetNewCodeButton(onClick: () -> Unit, label: String = "Get a new code") {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    GetNewCodeButton(onClick = onClick, label = label, modifier = Modifier.focusRequester(focusRequester))
}

@Composable
private fun ShowingContent(showing: PairingUiState.Showing, onGetNewCode: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // QR side
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val qrBitmap = rememberQrBitmap(showing.qrPayload)
            Box(
                modifier = Modifier
                    .size(340.dp)
                    .clip(RoundedCornerShape(TvDimens.RadiusLg))
                    .background(TvColors.QrLight),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = "Pairing QR code",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(272.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                showing.displayCode,
                color = TvColors.Text,
                style = MaterialTheme.typography.displayLarge.copy(letterSpacing = 4.sp),
            )
        }

        Spacer(Modifier.width(120.dp))

        // Instructions side
        Column(modifier = Modifier.width(700.dp)) {
            Text("KAWABI", color = TvColors.Accent, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text("Link this TV to your account", color = TvColors.Text, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Scan the code with your phone to sign in -- no remote typing.",
                color = TvColors.TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(36.dp))

            PairingStep(1, "Open Kawabi on your phone")
            Spacer(Modifier.height(20.dp))
            PairingStep(2, "Go to Settings -> Link a TV")
            Spacer(Modifier.height(20.dp))
            PairingStep(3, "Scan the code shown here and confirm")

            Spacer(Modifier.height(32.dp))
            CountdownRow(expiresAt = showing.expiresAt)
            Spacer(Modifier.height(20.dp))
            FocusedGetNewCodeButton(onClick = onGetNewCode)
        }
    }
}

@Composable
private fun PairingStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(TvColors.ChipHover),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), color = TvColors.Accent, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(18.dp))
        Text(text, color = TvColors.Text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CountdownRow(expiresAt: Long) {
    var remainingMs by remember(expiresAt) { mutableLongStateOf(expiresAt - System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) {
        while (remainingMs > 0) {
            delay(1_000)
            remainingMs = expiresAt - System.currentTimeMillis()
        }
    }
    val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    Text(
        "New code in %d:%02d".format(minutes, seconds),
        color = TvColors.TextDim,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun GetNewCodeButton(onClick: () -> Unit, label: String = "Get a new code", modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = TvColors.SurfaceOverlay,
            contentColor = TvColors.Text,
            focusedContainerColor = TvColors.Accent,
            focusedContentColor = TvColors.OnAccent,
        ),
    ) {
        Text(label)
    }
}
