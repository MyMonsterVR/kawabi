package com.mymonstervr.kawabi.app.anime

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.data.settings.SubtitleBackgroundStyle
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private const val AUTO_NEXT_SECONDS = 5

private enum class PlayerSheet { NONE, SERVERS, SUBTITLES, SPEED }

/**
 * Landscape, immersive Media3 player (PLAN-anime.md P4). PlayerView keeps its stock
 * controller (seek bar/play/pause are exactly the standard ones users expect); everything
 * anime-specific -- server+quality, subtitle track, speed, OP/ED skip, next episode --
 * lives in the Compose overlay on top of it, shown and hidden in step with that controller.
 */
@OptIn(UnstableApi::class)
@androidx.compose.runtime.Composable
fun PlayerScreen(
    episodeKey: String,
    onBack: () -> Unit,
    onNavigateEpisode: (String) -> Unit,
    onOpenAnimeDetail: (String) -> Unit,
    viewModel: PlayerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val animeTitle by viewModel.animeTitle.collectAsState()
    val animeKey by viewModel.animeKey.collectAsState()
    val episodeTitle by viewModel.episodeTitle.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val currentVideo by viewModel.currentVideo.collectAsState()
    val selectedSubtitle by viewModel.selectedSubtitle.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val ended by viewModel.ended.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val nextEpisodeKey by viewModel.nextEpisodeKey.collectAsState()
    val previousEpisodeKey by viewModel.previousEpisodeKey.collectAsState()
    val subtitleTextSize by viewModel.subtitleTextSize.collectAsState()
    val subtitleBackgroundStyle by viewModel.subtitleBackgroundStyle.collectAsState()
    val autoSkip by viewModel.autoSkip.collectAsState()

    LaunchedEffect(episodeKey) { viewModel.load(episodeKey) }

    ImmersiveLandscape()
    PauseOnStop(onStop = viewModel::pause)

    var controlsVisible by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf(PlayerSheet.NONE) }
    val skipRange = remember(positionMs, currentVideo) { viewModel.activeSkipRange(positionMs) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.player
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == android.view.View.VISIBLE
                        },
                    )
                }
            },
            update = { view -> applySubtitleAppearance(view, subtitleTextSize, subtitleBackgroundStyle) },
        )

        if (state is PlayerUiState.Loading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        (state as? PlayerUiState.Error)?.let { error ->
            PlayerErrorOverlay(
                message = error.message,
                canPickServer = error.canPickServer || videos.size > 1,
                onPickServer = { sheet = PlayerSheet.SERVERS },
                onBack = onBack,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (controlsVisible) {
            PlayerTopBar(
                animeTitle = animeTitle,
                episodeTitle = episodeTitle,
                onBack = onBack,
                onOpenDetail = { animeKey?.let(onOpenAnimeDetail) },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            PlayerActionRow(
                serverLabel = currentVideo?.let { "${it.displayHoster} · ${it.video.title.ifBlank { "Default" }}" } ?: "Server",
                subtitleLabel = currentVideo?.video?.subtitles?.getOrNull(selectedSubtitle ?: -1)?.lang?.ifBlank { "On" } ?: "Off",
                speed = speed,
                hasPrevious = previousEpisodeKey != null,
                hasNext = nextEpisodeKey != null,
                onServers = { sheet = PlayerSheet.SERVERS },
                onSubtitles = { sheet = PlayerSheet.SUBTITLES },
                onSpeed = { sheet = PlayerSheet.SPEED },
                onPrevious = { previousEpisodeKey?.let(onNavigateEpisode) },
                onNext = { nextEpisodeKey?.let(onNavigateEpisode) },
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }

        // Deliberately not gated on controlsVisible: the skip button is only useful in the
        // seconds the opening is actually playing, and making the user tap to reveal the
        // controls first would burn most of that window.
        if (skipRange != null && !autoSkip) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(NightSession.RadiusSm),
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
            ) {
                TextButton(onClick = { viewModel.skip(skipRange) }) {
                    Text(
                        text = "Skip ${skipRange.name.ifBlank { skipRange.type }}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (ended) {
            EndOfEpisodeOverlay(
                nextEpisodeKey = nextEpisodeKey,
                onNext = onNavigateEpisode,
                onBack = onBack,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    if (sheet != PlayerSheet.NONE) {
        PlayerSheetContent(
            sheet = sheet,
            videos = videos,
            currentVideo = currentVideo,
            subtitles = currentVideo?.video?.subtitles.orEmpty().map { it.lang.ifBlank { "Subtitles" } },
            selectedSubtitle = selectedSubtitle,
            speed = speed,
            onDismiss = { sheet = PlayerSheet.NONE },
            onSelectVideo = {
                viewModel.selectVideo(it)
                sheet = PlayerSheet.NONE
            },
            onSelectSubtitle = {
                viewModel.selectSubtitle(it)
                sheet = PlayerSheet.NONE
            },
            onSelectSpeed = {
                viewModel.setSpeed(it)
                sheet = PlayerSheet.NONE
            },
        )
    }
}

/**
 * Locks the activity to landscape and hides the system bars for as long as the player is
 * on screen, restoring both on the way out -- the rest of the app is portrait-shaped, so
 * leaving either applied would follow the user back to the episode list.
 */
@Composable
private fun ImmersiveLandscape() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val insets = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

@Composable
private fun PauseOnStop(onStop: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) onStop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

// Settings screen stores size as a whole-number percent of PlayerView's own default (see
// AppPreferences.SUBTITLE_TEXT_SIZE_DEFAULT); DEFAULT_TEXT_SIZE_FRACTION is that default
// expressed the way SubtitleView actually wants it (fraction of view height).
@OptIn(UnstableApi::class)
private fun applySubtitleAppearance(view: PlayerView, textSizePercent: Int, background: SubtitleBackgroundStyle) {
    val subtitleView = view.subtitleView ?: return
    subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * textSizePercent / 100f)
    subtitleView.setStyle(
        when (background) {
            SubtitleBackgroundStyle.BOX -> CaptionStyleCompat(
                android.graphics.Color.WHITE,
                android.graphics.Color.argb(180, 0, 0, 0),
                android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_NONE,
                android.graphics.Color.TRANSPARENT,
                null,
            )
            SubtitleBackgroundStyle.OUTLINE -> CaptionStyleCompat(
                android.graphics.Color.WHITE,
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                android.graphics.Color.BLACK,
                null,
            )
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerTopBar(
    animeTitle: String,
    episodeTitle: String,
    onBack: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalKawabiScale.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp * scale.spacing, vertical = 6.dp * scale.spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        // Long-press opens the anime detail page -- the source switcher and full episode
        // list only live there, and continuing from the library's Watching row jumps
        // straight into the player, skipping detail entirely (AnimeScreen.onContinue).
        Column(
            modifier = Modifier
                .padding(start = 4.dp)
                .combinedClickable(onClick = {}, onLongClick = onOpenDetail),
        ) {
            Text(
                text = animeTitle.ifBlank { "Now playing" },
                color = Color.White,
                fontSize = 13.sp * scale.font,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (episodeTitle.isNotBlank()) {
                Text(text = episodeTitle, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp * scale.font, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PlayerActionRow(
    serverLabel: String,
    subtitleLabel: String,
    speed: Float,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onServers: () -> Unit,
    onSubtitles: () -> Unit,
    onSpeed: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalKawabiScale.current
    Row(
        // Sits above the stock controller's own bottom bar rather than on top of it.
        modifier = modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 72.dp * scale.spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayAction(text = serverLabel, onClick = onServers)
        OverlayAction(text = "CC: $subtitleLabel", onClick = onSubtitles)
        OverlayAction(text = "${formatSpeed(speed)}x", onClick = onSpeed)
        if (hasPrevious) OverlayAction(text = "Previous", onClick = onPrevious)
        if (hasNext) OverlayAction(text = "Next", onClick = onNext)
    }
}

@Composable
private fun OverlayAction(text: String, onClick: () -> Unit) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(NightSession.RadiusSm),
        color = Color.Black.copy(alpha = 0.6f),
        modifier = Modifier.padding(end = 6.dp),
    ) {
        TextButton(onClick = onClick) {
            Text(text = text, color = Color.White, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun PlayerErrorOverlay(
    message: String,
    canPickServer: Boolean,
    onPickServer: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(NightSession.RadiusMd),
        color = Color.Black.copy(alpha = 0.85f),
        modifier = modifier.padding(24.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.padding(top = 8.dp)) {
                if (canPickServer) {
                    TextButton(onClick = onPickServer) {
                        Text("Try another server", color = MaterialTheme.colorScheme.primary)
                    }
                }
                TextButton(onClick = onBack) { Text("Back", color = Color.White.copy(alpha = 0.8f)) }
            }
        }
    }
}

@Composable
private fun EndOfEpisodeOverlay(
    nextEpisodeKey: String?,
    onNext: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var remaining by remember(nextEpisodeKey) { mutableIntStateOf(AUTO_NEXT_SECONDS) }
    LaunchedEffect(nextEpisodeKey) {
        val next = nextEpisodeKey ?: return@LaunchedEffect
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
        onNext(next)
    }
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(NightSession.RadiusMd),
        color = Color.Black.copy(alpha = 0.85f),
        modifier = modifier.padding(24.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (nextEpisodeKey != null) "Next episode in $remaining…" else "That was the last episode",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                if (nextEpisodeKey != null) {
                    TextButton(onClick = { onNext(nextEpisodeKey) }) {
                        Text("Play now", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = onBack) { Text("Back to episodes", color = Color.White.copy(alpha = 0.8f)) }
            }
        }
    }
}

@androidx.compose.runtime.Composable
@kotlin.OptIn(ExperimentalMaterial3Api::class)
private fun PlayerSheetContent(
    sheet: PlayerSheet,
    videos: List<PlayerVideo>,
    currentVideo: PlayerVideo?,
    subtitles: List<String>,
    selectedSubtitle: Int?,
    speed: Float,
    onDismiss: () -> Unit,
    onSelectVideo: (PlayerVideo) -> Unit,
    onSelectSubtitle: (Int?) -> Unit,
    onSelectSpeed: (Float) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NightSession.Background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            when (sheet) {
                PlayerSheet.SERVERS -> {
                    videos.groupBy { it.displayHoster }.forEach { (hoster, hosterVideos) ->
                        SheetGroupLabel(hoster)
                        hosterVideos.forEach { option ->
                            SheetRow(
                                label = option.video.title.ifBlank { "Default" },
                                selected = option == currentVideo,
                                onClick = { onSelectVideo(option) },
                            )
                        }
                    }
                }
                PlayerSheet.SUBTITLES -> {
                    SheetGroupLabel("Subtitles")
                    SheetRow(label = "Off", selected = selectedSubtitle == null, onClick = { onSelectSubtitle(null) })
                    subtitles.forEachIndexed { index, label ->
                        SheetRow(label = label, selected = selectedSubtitle == index, onClick = { onSelectSubtitle(index) })
                    }
                }
                PlayerSheet.SPEED -> {
                    SheetGroupLabel("Playback speed")
                    SPEEDS.forEach { option ->
                        SheetRow(
                            label = "${formatSpeed(option)}x",
                            selected = option == speed,
                            onClick = { onSelectSpeed(option) },
                        )
                    }
                }
                PlayerSheet.NONE -> Unit
            }
        }
    }
}

@Composable
private fun SheetGroupLabel(text: String) {
    val scale = LocalKawabiScale.current
    Text(
        text = text.uppercase(),
        color = NightSession.TextDim,
        fontSize = 10.sp * scale.font,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SheetRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val scale = LocalKawabiScale.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp * scale.spacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = label,
                        color = if (selected) MaterialTheme.colorScheme.primary else NightSession.Text,
                        fontSize = 12.sp * scale.font,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                    if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp * scale.font)
                }
            }
        }
        HorizontalDivider(color = NightSession.Hairline)
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0').trimEnd('.')
