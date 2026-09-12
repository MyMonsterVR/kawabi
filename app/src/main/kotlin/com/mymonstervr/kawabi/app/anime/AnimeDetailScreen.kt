package com.mymonstervr.kawabi.app.anime

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import com.mymonstervr.kawabi.app.common.ResponsiveContainer
import com.mymonstervr.kawabi.app.common.TrackerEditDialog
import com.mymonstervr.kawabi.app.common.TrackerLinkSheetContent
import com.mymonstervr.kawabi.app.common.TrackerSearchDialog
import com.mymonstervr.kawabi.app.common.TrackerSheetLink
import com.mymonstervr.kawabi.app.common.TrackerSheetRow
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.data.network.dto.AnimeDetailResponse
import com.mymonstervr.kawabi.data.network.dto.EpisodeDto
import com.mymonstervr.kawabi.data.network.resolveCoverUrl
import com.mymonstervr.kawabi.domain.model.Episode
import com.mymonstervr.kawabi.domain.model.MediaType
import com.mymonstervr.kawabi.domain.model.formatChapterNumber
import com.mymonstervr.kawabi.domain.model.formatRelativeTime
import org.koin.androidx.compose.koinViewModel

private val DETAIL_HERO_HEIGHT = 190.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeDetailScreen(
    animeKey: String,
    onBack: () -> Unit,
    onEpisodeClick: (String) -> Unit,
    onOpenAnimeDetail: (String) -> Unit,
    onOpenTrackingSettings: () -> Unit,
    viewModel: AnimeDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(animeKey) { viewModel.load(animeKey) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val state by viewModel.state.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val localEpisodesByKey by viewModel.localEpisodesByKey.collectAsState()
    val episodeError by viewModel.episodeError.collectAsState()
    val libraryMatch by viewModel.libraryMatch.collectAsState()
    val sourceOptions by viewModel.sourceOptions.collectAsState()
    val trackerSheet by viewModel.trackerSheet.collectAsState()
    val altTitleSuggestions by viewModel.altTitleSuggestions.collectAsState()
    val lastTitle by viewModel.lastTitle.collectAsState()
    val pullState = rememberPullToRefreshState()
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var searchingTrackerId by remember { mutableStateOf<String?>(null) }
    var editingTrackerId by remember { mutableStateOf<String?>(null) }

    val trackerSheetShown = trackerSheet as? AnimeTrackerSheetState.Shown
    if (trackerSheetShown != null) {
        ModalBottomSheet(onDismissRequest = viewModel::closeTrackerSheet, containerColor = NightSession.Chip) {
            TrackerLinkSheetContent(
                rows = trackerSheetShown.rows.map { it.toSheetRow() },
                onOpenSearch = { trackerId -> searchingTrackerId = trackerId },
                onOpenEdit = { trackerId -> editingTrackerId = trackerId },
                onGoToSettings = { viewModel.closeTrackerSheet(); onOpenTrackingSettings() },
            )
        }
    }

    val searchRow = trackerSheetShown?.rows?.firstOrNull { it.trackerId == searchingTrackerId }
    LaunchedEffect(searchingTrackerId) {
        val trackerId = searchingTrackerId
        if (trackerId != null) {
            viewModel.searchTracker(trackerId, lastTitle)
            viewModel.loadAltTitleSuggestions(lastTitle)
        }
    }
    if (searchingTrackerId != null && searchRow != null) {
        TrackerSearchDialog(
            trackerName = searchRow.trackerName,
            initialQuery = lastTitle,
            searching = searchRow.searching,
            results = searchRow.searchResults,
            error = searchRow.searchError,
            altTitleSuggestions = altTitleSuggestions,
            mediaType = MediaType.ANIME,
            onSearch = { query -> viewModel.searchTracker(searchRow.trackerId, query) },
            onSelect = { result ->
                viewModel.linkTracker(searchRow.trackerId, result)
                searchingTrackerId = null
            },
            onDismiss = { searchingTrackerId = null },
        )
    }

    val editRow = trackerSheetShown?.rows?.firstOrNull { it.trackerId == editingTrackerId }
    val editingTrack = editRow?.linked
    if (editingTrack != null) {
        TrackerEditDialog(
            trackerName = editRow.trackerName,
            link = editRow.toSheetRow().linked!!,
            mediaType = MediaType.ANIME,
            onDismiss = { editingTrackerId = null },
            onSave = { episodesWatched, status, score ->
                viewModel.updateTrackDetails(editingTrack, episodesWatched, status, score) { editingTrackerId = null }
            },
            onUnlink = {
                viewModel.unlinkTracker(editRow.trackerId)
                editingTrackerId = null
            },
        )
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove from library?") },
            text = { Text("Watch progress stays saved even after removal.") },
            confirmButton = {
                TextButton(onClick = { showRemoveConfirm = false; viewModel.toggleFavorite(animeKey) }) {
                    Text("Remove")
                }
            },
            dismissButton = { TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") } },
            containerColor = NightSession.Chip,
        )
    }

    Scaffold(containerColor = NightSession.Background) { padding ->
        ResponsiveContainer(modifier = Modifier.padding(padding)) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (val current = state) {
                    is AnimeDetailState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    is AnimeDetailState.Error -> AnimeDetailErrorContent(
                        message = current.message,
                        onBack = onBack,
                        onRetry = { viewModel.load(animeKey) },
                    )
                    is AnimeDetailState.Success -> PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refresh(animeKey) },
                        state = pullState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        AnimeDetailContent(
                            anime = current.anime,
                            isFavorite = isFavorite,
                            localEpisodesByKey = localEpisodesByKey,
                            episodeError = episodeError,
                            libraryMatch = libraryMatch,
                            sourceOptions = sourceOptions,
                            onOpenLibraryEntry = { libraryMatch?.let { onOpenAnimeDetail(it.key) } },
                            onUseThisSource = viewModel::useOpenedSource,
                            onLoadSourceOptions = viewModel::loadSourceOptions,
                            onSelectSource = viewModel::selectSource,
                            onBack = onBack,
                            onToggleFavorite = {
                                if (isFavorite) showRemoveConfirm = true else viewModel.toggleFavorite(animeKey)
                            },
                            onEpisodeClick = onEpisodeClick,
                            onSetEpisodeWatched = viewModel::setEpisodeWatched,
                            onMarkPreviousAsWatched = viewModel::markPreviousAsWatched,
                            onOpenTrackerSheet = viewModel::openTrackerSheet,
                        )
                    }
                }
            }
        }
    }
}

private fun AnimeTrackerLinkRow.toSheetRow(): TrackerSheetRow = TrackerSheetRow(
    trackerId = trackerId,
    trackerName = trackerName,
    linked = linked?.let {
        TrackerSheetLink(progress = it.lastEpisodeWatched, total = it.totalEpisodes, score = it.score, status = it.status)
    },
)

@Composable
private fun AnimeDetailContent(
    anime: AnimeDetailResponse,
    isFavorite: Boolean,
    localEpisodesByKey: Map<String, Episode>,
    episodeError: String?,
    libraryMatch: AnimeLibraryMatch?,
    sourceOptions: AnimeSourceOptionsState,
    onOpenLibraryEntry: () -> Unit,
    onUseThisSource: () -> Unit,
    onLoadSourceOptions: () -> Unit,
    onSelectSource: (String) -> Unit,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEpisodeClick: (String) -> Unit,
    onSetEpisodeWatched: (Long, Boolean) -> Unit,
    onMarkPreviousAsWatched: (Episode) -> Unit,
    onOpenTrackerSheet: () -> Unit,
) {
    val scale = LocalKawabiScale.current
    var expandedEpisodeKey by remember { mutableStateOf<String?>(null) }
    val resume = remember(localEpisodesByKey) { resumeEpisode(localEpisodesByKey.values) }
    val hasAnyWatched = remember(localEpisodesByKey) { localEpisodesByKey.values.any { it.watched } }
    val episodes = remember(anime.episodes) { anime.episodes.sortedByDescending { it.number } }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column {
                Box(modifier = Modifier.fillMaxWidth().height(DETAIL_HERO_HEIGHT * scale.spacing)) {
                    AsyncImage(
                        model = resolveCoverUrl(anime.cover_url, anime.source),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().background(NightSession.Cover),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.25f to Color.Transparent,
                                    1f to NightSession.Background,
                                ),
                            ),
                    )
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp * scale.spacing)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    AsyncImage(
                        model = resolveCoverUrl(anime.cover_url, anime.source),
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp * scale.spacing, bottom = 14.dp * scale.spacing)
                            .width(80.dp * scale.spacing)
                            .height(120.dp * scale.spacing)
                            .clip(RoundedCornerShape(NightSession.RadiusMd))
                            .border(1.dp, NightSession.Hairline, RoundedCornerShape(NightSession.RadiusMd))
                            .background(NightSession.Cover),
                    )
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp * scale.spacing, vertical = 8.dp * scale.spacing)) {
                    Text(text = anime.title, fontSize = 16.5.sp * scale.font, fontWeight = FontWeight.Bold, color = NightSession.Text)
                    val byline = listOfNotNull(
                        anime.author?.takeIf { it.isNotBlank() },
                        anime.status.takeIf { it.isNotBlank() },
                        anime.source_name.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    if (byline.isNotBlank()) {
                        Text(
                            text = byline,
                            fontSize = 11.sp * scale.font,
                            color = NightSession.TextDim,
                            modifier = Modifier.padding(top = 3.dp * scale.spacing),
                        )
                    }
                    if (anime.genres.isNotEmpty()) {
                        Text(
                            text = anime.genres.joinToString(", "),
                            fontSize = 10.5.sp * scale.font,
                            color = NightSession.TextDim,
                            modifier = Modifier.padding(top = 3.dp * scale.spacing),
                        )
                    }
                }

                if (libraryMatch != null) {
                    LibraryMatchBanner(
                        sourceName = libraryMatch.sourceName,
                        onOpenLibraryEntry = onOpenLibraryEntry,
                        onUseThisSource = onUseThisSource,
                    )
                }

                run {
                    AnimeSourcePill(
                        currentName = anime.source_name.ifBlank { anime.source },
                        options = sourceOptions,
                        onOpen = onLoadSourceOptions,
                        onSelect = onSelectSource,
                        modifier = Modifier.padding(
                            start = 16.dp * scale.spacing,
                            end = 16.dp * scale.spacing,
                            top = 2.dp * scale.spacing,
                        ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp * scale.spacing, vertical = 4.dp * scale.spacing),
                    horizontalArrangement = Arrangement.spacedBy(8.dp * scale.spacing),
                ) {
                    if (isFavorite && resume != null) {
                        Button(
                            onClick = { onEpisodeClick(resume.key) },
                            shape = RoundedCornerShape(NightSession.RadiusMd),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = NightSession.OnAccent),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = continueLabel(resume, hasAnyWatched),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp * scale.font,
                            )
                        }
                    } else if (!isFavorite && libraryMatch == null) {
                        Button(
                            onClick = onToggleFavorite,
                            shape = RoundedCornerShape(NightSession.RadiusMd),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = NightSession.OnAccent),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Add to library", fontWeight = FontWeight.Bold, fontSize = 12.sp * scale.font)
                        }
                    }
                    IconButton(
                        onClick = if (isFavorite) onToggleFavorite else ({}),
                        modifier = Modifier
                            .size(40.dp * scale.spacing)
                            .clip(RoundedCornerShape(NightSession.RadiusMd))
                            .background(NightSession.Chip)
                            .border(1.dp, if (isFavorite) MaterialTheme.colorScheme.primary else NightSession.Hairline, RoundedCornerShape(NightSession.RadiusMd)),
                    ) {
                        if (isFavorite) {
                            Icon(Icons.Filled.Favorite, contentDescription = "Remove from library", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp * scale.spacing))
                        } else {
                            Icon(Icons.Outlined.FavoriteBorder, contentDescription = "Add to library", tint = NightSession.Text, modifier = Modifier.size(16.dp * scale.spacing))
                        }
                    }
                    // Tracker linking needs a local row to attach to -- same gating as the
                    // manga screen's heart.
                    if (isFavorite) {
                        IconButton(
                            onClick = onOpenTrackerSheet,
                            modifier = Modifier
                                .size(40.dp * scale.spacing)
                                .clip(RoundedCornerShape(NightSession.RadiusMd))
                                .background(NightSession.Chip)
                                .border(1.dp, NightSession.Hairline, RoundedCornerShape(NightSession.RadiusMd)),
                        ) {
                            Icon(Icons.Outlined.Flag, contentDescription = "Tracker links", tint = NightSession.Text, modifier = Modifier.size(16.dp * scale.spacing))
                        }
                    }
                }

                val description = anime.description
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        fontSize = 11.5.sp * scale.font,
                        lineHeight = 17.sp * scale.font,
                        color = NightSession.TextDim,
                        modifier = Modifier.padding(horizontal = 16.dp * scale.spacing, vertical = 8.dp * scale.spacing),
                    )
                }

                Text(
                    text = "${episodes.size} episodes",
                    fontSize = 11.sp * scale.font,
                    fontWeight = FontWeight.SemiBold,
                    color = NightSession.Text,
                    modifier = Modifier.padding(horizontal = 16.dp * scale.spacing, vertical = 6.dp * scale.spacing),
                )

                if (episodeError != null) {
                    Text(
                        text = episodeError,
                        fontSize = 10.5.sp * scale.font,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(
                            start = 16.dp * scale.spacing,
                            end = 16.dp * scale.spacing,
                            bottom = 6.dp * scale.spacing,
                        ),
                    )
                } else if (!isFavorite && libraryMatch == null) {
                    Text(
                        text = "Not in your library yet — episodes still play, add it to sync progress.",
                        fontSize = 10.5.sp * scale.font,
                        color = NightSession.TextDim,
                        modifier = Modifier.padding(
                            start = 16.dp * scale.spacing,
                            end = 16.dp * scale.spacing,
                            bottom = 6.dp * scale.spacing,
                        ),
                    )
                }
                HorizontalDivider(color = NightSession.Hairline)
            }
        }

        items(episodes, key = { it.key }) { episode ->
            val localEpisode = localEpisodesByKey[episode.key]
            EpisodeRow(
                episode = episode,
                localEpisode = localEpisode,
                expanded = expandedEpisodeKey == episode.key,
                onClick = { if (localEpisode != null) onEpisodeClick(episode.key) },
                onLongClick = { if (localEpisode != null) expandedEpisodeKey = episode.key },
                onMarkWatched = {
                    localEpisode?.let { onSetEpisodeWatched(it.id, !it.watched) }
                    expandedEpisodeKey = null
                },
                onMarkPreviousWatched = {
                    localEpisode?.let(onMarkPreviousAsWatched)
                    expandedEpisodeKey = null
                },
            )
        }
    }
}

@Composable
private fun LibraryMatchBanner(
    sourceName: String,
    onOpenLibraryEntry: () -> Unit,
    onUseThisSource: () -> Unit,
) {
    val scale = LocalKawabiScale.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp * scale.spacing, vertical = 6.dp * scale.spacing)
            .clip(RoundedCornerShape(NightSession.RadiusMd))
            .background(NightSession.Chip)
            .border(1.dp, NightSession.Hairline, RoundedCornerShape(NightSession.RadiusMd))
            .padding(horizontal = 12.dp * scale.spacing, vertical = 8.dp * scale.spacing),
    ) {
        Text(
            text = "In your library from $sourceName",
            fontSize = 11.sp * scale.font,
            fontWeight = FontWeight.SemiBold,
            color = NightSession.Text,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp * scale.spacing)) {
            TextButton(onClick = onOpenLibraryEntry) {
                Text("Open library entry", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp * scale.font)
            }
            TextButton(onClick = onUseThisSource) {
                Text("Use this source instead", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp * scale.font)
            }
        }
    }
}

@Composable
private fun AnimeSourcePill(
    currentName: String,
    options: AnimeSourceOptionsState,
    onOpen: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalKawabiScale.current
    var expanded by remember { mutableStateOf(false) }
    var pendingOpen by remember { mutableStateOf(false) }
    LaunchedEffect(options) {
        if (pendingOpen && options !is AnimeSourceOptionsState.Loading && options !is AnimeSourceOptionsState.Idle) {
            pendingOpen = false
            expanded = true
        }
    }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100))
                .background(NightSession.Chip)
                .border(1.dp, NightSession.Hairline, RoundedCornerShape(100))
                .clickable {
                    if (options is AnimeSourceOptionsState.Loaded) {
                        expanded = true
                    } else {
                        pendingOpen = true
                        onOpen()
                    }
                }
                .padding(horizontal = 10.dp * scale.spacing, vertical = 5.dp * scale.spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Source: ${currentName.ifBlank { "unknown" }} ",
                fontSize = 10.5.sp * scale.font,
                fontWeight = FontWeight.SemiBold,
                color = NightSession.TextDim,
            )
            if (options is AnimeSourceOptionsState.Loading) {
                CircularProgressIndicator(
                    color = NightSession.TextDim,
                    strokeWidth = 1.5.dp,
                    modifier = Modifier.size(10.dp * scale.font),
                )
            } else {
                Text(
                    text = "▾",
                    fontSize = 10.5.sp * scale.font,
                    fontWeight = FontWeight.SemiBold,
                    color = NightSession.TextDim,
                )
            }
        }
        if (expanded) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { expanded = false },
                containerColor = NightSession.Chip,
            ) {
                when (options) {
                    AnimeSourceOptionsState.Idle, AnimeSourceOptionsState.Loading -> Box(
                        Modifier.padding(24.dp * scale.spacing),
                        Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp * scale.spacing),
                        )
                    }
                    is AnimeSourceOptionsState.Error -> Text(
                        text = options.message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp * scale.font,
                        modifier = Modifier.padding(16.dp * scale.spacing),
                    )
                    is AnimeSourceOptionsState.Loaded -> options.options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.sourceName,
                                    color = NightSession.Text,
                                    fontSize = 12.sp * scale.font,
                                )
                            },
                            trailingIcon = if (option.key == options.selected) {
                                { Text("✓", color = MaterialTheme.colorScheme.primary) }
                            } else {
                                null
                            },
                            onClick = {
                                expanded = false
                                if (option.key != options.selected) onSelect(option.key)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun continueLabel(target: Episode, hasAnyWatched: Boolean): String {
    val label = "Ep. ${formatChapterNumber(target.episodeNumber)}"
    return when {
        !hasAnyWatched -> "Start watching"
        target.positionMs > 0 -> "Continue $label"
        else -> label
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    episode: EpisodeDto,
    localEpisode: Episode?,
    expanded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMarkWatched: () -> Unit,
    onMarkPreviousWatched: () -> Unit,
) {
    val scale = LocalKawabiScale.current
    val enabled = localEpisode != null
    val isWatched = localEpisode?.watched == true
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp * scale.spacing, vertical = 9.dp * scale.spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episodeLabel(episode),
                    fontSize = 11.5.sp * scale.font,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isWatched) NightSession.TextDim else NightSession.Text,
                )
                val relativeTime = remember(episode.date_upload) {
                    episode.date_upload?.let { formatRelativeTime(it) }
                }
                if (relativeTime != null) {
                    Text(text = relativeTime, fontSize = 9.5.sp * scale.font, color = NightSession.TextDim)
                }
                // Only meaningful once playback has recorded a duration -- an episode with
                // a position but no known duration can't be drawn as a fraction.
                val fraction = localEpisode
                    ?.takeIf { !it.watched && it.positionMs > 0 && it.durationMs > 0 }
                    ?.let { (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = NightSession.Chip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .padding(top = 4.dp * scale.spacing),
                    )
                }
            }
            if (isWatched) {
                Text(text = "✓", fontSize = 11.sp * scale.font, color = NightSession.Read)
            }
        }
        if (expanded && localEpisode != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp * scale.spacing, vertical = 6.dp * scale.spacing),
                horizontalArrangement = Arrangement.spacedBy(8.dp * scale.spacing),
            ) {
                TextButton(onClick = onMarkWatched) {
                    Text(
                        text = if (localEpisode.watched) "Mark unwatched" else "Mark watched",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp * scale.font,
                    )
                }
                TextButton(onClick = onMarkPreviousWatched) {
                    Text("Mark previous watched", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp * scale.font)
                }
            }
        }
        HorizontalDivider(color = NightSession.Hairline, modifier = Modifier.padding(horizontal = 16.dp * scale.spacing))
    }
}

private fun episodeLabel(episode: EpisodeDto): String =
    episode.title.ifBlank { "Episode ${formatChapterNumber(episode.number)}" }

@Composable
private fun AnimeDetailErrorContent(message: String, onBack: () -> Unit, onRetry: () -> Unit) {
    val scale = LocalKawabiScale.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp * scale.spacing),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp * scale.font,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp * scale.spacing)) {
            TextButton(onClick = onRetry) { Text("Retry", color = MaterialTheme.colorScheme.primary) }
            TextButton(onClick = onBack) { Text("Back", color = NightSession.TextDim) }
        }
    }
}
