package com.mymonstervr.kawabi.tv.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mymonstervr.kawabi.player.PlayerUiState
import com.mymonstervr.kawabi.player.PlayerViewModel
import com.mymonstervr.kawabi.tv.theme.TvColors
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

private const val SEEK_STEP_MS = 10_000L
private const val OVERLAY_AUTO_HIDE_MS = 5_000L

/**
 * D-pad key table (PLAN Phase 7): Center/Enter toggles play/pause and flashes the overlay;
 * Left/Right seek +-10s while the overlay is hidden; Up/Down reveal it; media keys map to the
 * usual transport actions; Back hides the overlay if it's showing, else exits the player.
 * No on-screen touch controller (`useController = false`) -- every interaction here is D-pad
 * driven, never a tap target.
 */
@Composable
fun TvPlayerScreen(
    episodeKey: String,
    onBack: () -> Unit,
    onEpisodeChanged: (String) -> Unit,
    viewModel: PlayerViewModel = koinViewModel(),
) {
    LaunchedEffect(episodeKey) { viewModel.load(episodeKey) }

    val state by viewModel.state.collectAsState()
    val animeTitle by viewModel.animeTitle.collectAsState()
    val episodeTitle by viewModel.episodeTitle.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val ended by viewModel.ended.collectAsState()
    val currentVideo by viewModel.currentVideo.collectAsState()
    val nextEpisodeKey by viewModel.nextEpisodeKey.collectAsState()
    val autoSkip by viewModel.autoSkip.collectAsState()
    val skipRange = remember(positionMs, currentVideo) { viewModel.activeSkipRange(positionMs) }

    var overlayVisible by remember { mutableStateOf(true) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-hide after 5s idle, same as the phone player's controller timeout.
    LaunchedEffect(overlayVisible, positionMs) {
        if (overlayVisible) {
            delay(OVERLAY_AUTO_HIDE_MS)
            overlayVisible = false
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != AndroidKeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_BACK -> {
                        if (overlayVisible) overlayVisible = false else onBack()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (viewModel.player.isPlaying) viewModel.pause() else viewModel.player.play()
                        overlayVisible = true
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                        if (!overlayVisible) {
                            viewModel.player.seekTo((viewModel.player.currentPosition - SEEK_STEP_MS).coerceAtLeast(0))
                            true
                        } else {
                            false
                        }
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        if (!overlayVisible) {
                            viewModel.player.seekTo(viewModel.player.currentPosition + SEEK_STEP_MS)
                            true
                        } else {
                            false
                        }
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> {
                        nextEpisodeKey?.let(onEpisodeChanged)
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (!overlayVisible) {
                            overlayVisible = true
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.player
                    useController = false
                    keepScreenOn = true
                }
            },
        )

        if (state is PlayerUiState.Loading) {
            Text("Loading...", color = TvColors.TextDim, modifier = Modifier.align(Alignment.Center))
        }

        (state as? PlayerUiState.Error)?.let { error ->
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error.message, color = TvColors.Text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                RetryFocusedButton(onClick = onBack)
            }
        }

        if (overlayVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(32.dp),
            ) {
                Text(animeTitle, color = TvColors.Text, style = MaterialTheme.typography.titleLarge)
                Text(episodeTitle, color = TvColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Skip button auto-focuses whenever a skippable range is active -- deliberately not
        // gated on overlayVisible, same reasoning as the phone player: the window it's useful
        // in is short, and making the user reveal the overlay first would burn most of it.
        if (skipRange != null && !autoSkip) {
            SkipChip(
                label = "Skip ${skipRange.name.ifBlank { skipRange.type }}",
                onClick = { viewModel.skip(skipRange) },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp),
            )
        }

        if (ended) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Episode finished", color = TvColors.Text, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(20.dp))
                Row {
                    if (nextEpisodeKey != null) {
                        NextEpisodeButton(onClick = { nextEpisodeKey?.let(onEpisodeChanged) })
                    } else {
                        RetryFocusedButton(label = "Back to episodes", onClick = onBack)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkipChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Button(
        onClick = onClick,
        modifier = modifier.focusRequester(focusRequester),
        colors = ButtonDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.7f),
            contentColor = TvColors.Text,
            focusedContainerColor = TvColors.Accent,
            focusedContentColor = TvColors.OnAccent,
        ),
    ) { Text(label) }
}

@Composable
private fun NextEpisodeButton(onClick: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Button(
        onClick = onClick,
        modifier = Modifier.focusRequester(focusRequester),
        colors = ButtonDefaults.colors(
            containerColor = TvColors.Accent,
            contentColor = TvColors.OnAccent,
            focusedContainerColor = TvColors.Accent,
            focusedContentColor = TvColors.OnAccent,
        ),
    ) { Text("Play next episode") }
}

@Composable
private fun RetryFocusedButton(onClick: () -> Unit, label: String = "Back") {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Button(
        onClick = onClick,
        modifier = Modifier.focusRequester(focusRequester),
        colors = ButtonDefaults.colors(
            containerColor = TvColors.SurfaceOverlay,
            contentColor = TvColors.Text,
            focusedContainerColor = TvColors.Accent,
            focusedContentColor = TvColors.OnAccent,
        ),
    ) { Text(label) }
}
