package com.mymonstervr.kawabi.tv.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.mymonstervr.kawabi.player.PlayerUiState
import com.mymonstervr.kawabi.player.PlayerVideo
import com.mymonstervr.kawabi.player.PlayerViewModel
import com.mymonstervr.kawabi.tv.theme.TvColors
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import java.util.concurrent.TimeUnit

private const val SEEK_STEP_MS = 10_000L
private const val SEEK_STEP_FAST_MS = 30_000L
private const val SEEK_STEP_FASTEST_MS = 60_000L
private const val SEEK_ACCEL_AFTER_MS = 1_500L
private const val SEEK_ACCEL_AGAIN_MS = 4_000L
private const val OVERLAY_AUTO_HIDE_MS = 5_000L
private const val SEEK_READOUT_HIDE_MS = 1_200L

private enum class TvPlayerPanel { SERVERS, SUBTITLES }

private fun formatMs(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    val h = TimeUnit.SECONDS.toHours(totalSeconds)
    val m = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

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
    val videos by viewModel.videos.collectAsState()
    val currentVideo by viewModel.currentVideo.collectAsState()
    val selectedSubtitle by viewModel.selectedSubtitle.collectAsState()
    val nextEpisodeKey by viewModel.nextEpisodeKey.collectAsState()
    val autoSkip by viewModel.autoSkip.collectAsState()
    val skipRange = remember(positionMs, currentVideo) { viewModel.activeSkipRange(positionMs) }

    var overlayVisible by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf<TvPlayerPanel?>(null) }

    // Accelerating seek-on-hold: repeated same-direction presses inside the window ramp the
    // step 10s -> 30s -> 60s: a full rewatch-skip across a 20+ minute episode at a flat 10s
    // step would take dozens of presses otherwise.
    var seekStreak by remember { mutableStateOf(0) }
    var lastSeekAt by remember { mutableStateOf(0L) }
    var seekReadoutMs by remember { mutableStateOf<Long?>(null) }
    fun stepFor(streak: Int) = when {
        streak >= 2 -> SEEK_STEP_FASTEST_MS
        streak == 1 -> SEEK_STEP_FAST_MS
        else -> SEEK_STEP_MS
    }
    fun seek(deltaSign: Int) {
        val now = System.currentTimeMillis()
        val gap = now - lastSeekAt
        seekStreak = when {
            gap > SEEK_ACCEL_AGAIN_MS -> 0
            gap > SEEK_ACCEL_AFTER_MS -> seekStreak
            else -> (seekStreak + 1).coerceAtMost(2)
        }
        lastSeekAt = now
        val step = stepFor(seekStreak)
        val target = (viewModel.player.currentPosition + deltaSign * step).coerceAtLeast(0)
        viewModel.player.seekTo(target)
        seekReadoutMs = target
    }
    LaunchedEffect(seekReadoutMs) {
        if (seekReadoutMs != null) {
            delay(SEEK_READOUT_HIDE_MS)
            seekReadoutMs = null
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Auto-hide after 5s idle, same as the phone player's controller timeout. Never while
    // ended/error/a panel is up -- their own buttons live outside the overlayVisible gate and
    // must stay reachable.
    LaunchedEffect(overlayVisible, positionMs, ended, state, panel) {
        if (overlayVisible && !ended && state is PlayerUiState.Ready && panel == null) {
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
                if (panel != null) {
                    // Panel traps everything except Back (closes it) -- every row inside is a
                    // normal focusable Surface, so D-pad up/down/center already work for free.
                    if (event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                        panel = null
                        true
                    } else {
                        false
                    }
                } else {
                    // A focused overlay/ended/error button must get Center/Enter itself --
                    // this Box's own handling of those keys is only for the "nothing else is
                    // showing, D-pad drives bare playback" case, and has to yield to any real
                    // button on screen or it'd swallow every button click site-wide.
                    val bareControlsActive = !overlayVisible && !ended && state is PlayerUiState.Ready
                    when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_BACK -> {
                            if (overlayVisible) overlayVisible = false else onBack()
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (bareControlsActive) {
                                if (viewModel.player.isPlaying) viewModel.pause() else viewModel.player.play()
                                overlayVisible = true
                                true
                            } else {
                                false
                            }
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                            if (bareControlsActive) {
                                seek(-1)
                                true
                            } else {
                                false
                            }
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            if (bareControlsActive) {
                                seek(1)
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
                            if (bareControlsActive) {
                                overlayVisible = true
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
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
                Row {
                    // Focused by default on an error with other servers available -- the fix
                    // is usually "pick another server", not "give up and go back".
                    if (error.canPickServer) {
                        RetryFocusedButton(label = "Try another server", onClick = { panel = TvPlayerPanel.SERVERS })
                        Spacer(Modifier.width(12.dp))
                        SecondaryButton(label = "Back", onClick = onBack)
                    } else {
                        RetryFocusedButton(label = "Back", onClick = onBack)
                    }
                }
            }
        }

        seekReadoutMs?.let { ms ->
            Text(
                formatMs(ms),
                color = TvColors.Text,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.Center),
            )
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
                Spacer(Modifier.height(16.dp))
                Row {
                    OverlayActionButton(
                        label = currentVideo?.let { "${it.displayHoster} · ${it.video.title.ifBlank { "Default" }}" } ?: "Server",
                        onClick = { panel = TvPlayerPanel.SERVERS },
                    )
                    Spacer(Modifier.width(12.dp))
                    OverlayActionButton(
                        label = "CC: " + (currentVideo?.video?.subtitles?.getOrNull(selectedSubtitle ?: -1)?.lang?.ifBlank { "On" } ?: "Off"),
                        onClick = { panel = TvPlayerPanel.SUBTITLES },
                    )
                }
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

        if (panel != null) {
            TvPlayerSidePanel(
                panel = panel!!,
                videos = videos,
                currentVideo = currentVideo,
                selectedSubtitle = selectedSubtitle,
                onSelectVideo = { viewModel.selectVideo(it); panel = null },
                onSelectSubtitle = { viewModel.selectSubtitle(it); panel = null },
                onClose = { panel = null },
            )
        }
    }
}

/**
 * Focus-trapped: the only focusable content on screen while it's up is inside this panel (the
 * Box's onPreviewKeyEvent handler above stops forwarding Left/Right/Center-for-playback once
 * [panel] is non-null), and every row requests focus once so D-pad down from the top of the
 * list always lands somewhere real. Back closes it -- handled in that same key intercept, not
 * here, so it works no matter which row currently has focus.
 */
@Composable
private fun TvPlayerSidePanel(
    panel: TvPlayerPanel,
    videos: List<PlayerVideo>,
    currentVideo: PlayerVideo?,
    selectedSubtitle: Int?,
    onSelectVideo: (PlayerVideo) -> Unit,
    onSelectSubtitle: (Int?) -> Unit,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(420.dp)
                .background(TvColors.Background)
                .padding(24.dp),
        ) {
            Text(
                if (panel == TvPlayerPanel.SERVERS) "Server" else "Subtitles",
                color = TvColors.Text,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn {
                if (panel == TvPlayerPanel.SERVERS) {
                    items(videos) { option ->
                        PanelRow(
                            label = "${option.displayHoster} · ${option.video.title.ifBlank { "Default" }}",
                            selected = option == currentVideo,
                            initialFocus = option == currentVideo,
                            onClick = { onSelectVideo(option) },
                        )
                    }
                } else {
                    item {
                        PanelRow(
                            label = "Off",
                            selected = selectedSubtitle == null,
                            initialFocus = selectedSubtitle == null,
                            onClick = { onSelectSubtitle(null) },
                        )
                    }
                    val subtitles = currentVideo?.video?.subtitles.orEmpty()
                    itemsIndexed(subtitles) { index, track ->
                        PanelRow(
                            label = track.lang.ifBlank { "Subtitles" },
                            selected = selectedSubtitle == index,
                            initialFocus = selectedSubtitle == index,
                            onClick = { onSelectSubtitle(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelRow(label: String, selected: Boolean, initialFocus: Boolean, onClick: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    if (initialFocus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) TvColors.SurfaceOverlay else Color.Transparent,
            focusedContainerColor = TvColors.Accent,
        ),
    ) {
        Text(
            label,
            color = if (selected) TvColors.Accent else TvColors.Text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp),
        )
    }
}

@Composable
private fun OverlayActionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = TvColors.SurfaceOverlay,
            contentColor = TvColors.Text,
            focusedContainerColor = TvColors.Accent,
            focusedContentColor = TvColors.OnAccent,
        ),
    ) { Text(label) }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = TvColors.SurfaceOverlay,
            contentColor = TvColors.Text,
            focusedContainerColor = TvColors.Accent,
            focusedContentColor = TvColors.OnAccent,
        ),
    ) { Text(label) }
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
