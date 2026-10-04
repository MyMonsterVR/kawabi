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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.style.TextOverflow
import com.mymonstervr.kawabi.app.common.CoverPill
import com.mymonstervr.kawabi.app.common.PrimaryActionButton
import com.mymonstervr.kawabi.app.common.SegmentedTabs
import com.mymonstervr.kawabi.app.common.accentSoft
import com.mymonstervr.kawabi.app.common.glass
import com.mymonstervr.kawabi.app.common.roundIconButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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

private val DETAIL_BACKDROP_HEIGHT = 380.dp
private val PROGRESS_RED = Color(0xFFEF4D55)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeDetailScreen(
    animeKey: String,
    onBack: () -> Unit,
    onEpisodeClick: (String) -> Unit,
    onOpenAnimeDetail: (String) -> Unit,
    onOpenTrackingSettings: () -> Unit,
    onKeyChanged: (String) -> Unit = {},
    viewModel: AnimeDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(animeKey) { viewModel.load(animeKey) }

    // A silent identity redirect or a source switch can move the loaded show onto a
    // different key than the one this screen was opened with -- fix the back-stack entry
    // to match so rotating/returning keeps the chosen source (PLAN-anime.md section 18
    // follow-up).
    val openKey by viewModel.openKey.collectAsState()
    LaunchedEffect(openKey) {
        val key = openKey
        if (key != null && key != animeKey) onKeyChanged(key)
    }

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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            val isFailure = message.startsWith("Couldn't switch")
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (isFailure) "Retry" else null,
            )
            if (isFailure && result == SnackbarResult.ActionPerformed) viewModel.retryLastSwitch()
        }
    }

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

    Scaffold(
        containerColor = NightSession.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
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
    var newestFirst by remember { mutableStateOf(true) }
    val resume = remember(localEpisodesByKey) { resumeEpisode(localEpisodesByKey.values) }
    val hasAnyWatched = remember(localEpisodesByKey) { localEpisodesByKey.values.any { it.watched } }
    val episodes = remember(anime.episodes, newestFirst) {
        if (newestFirst) anime.episodes.sortedByDescending { it.number } else anime.episodes.sortedBy { it.number }
    }
    val gutter = 16.dp * scale.spacing

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp * scale.spacing)) {
        item {
            Column {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().height(DETAIL_BACKDROP_HEIGHT * scale.spacing)) {
                        AsyncImage(
                            model = resolveCoverUrl(anime.cover_url, anime.source),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().blur(40.dp).alpha(0.55f),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to NightSession.Background.copy(alpha = 0.25f),
                                        0.96f to NightSession.Background,
                                    ),
                                ),
                        )
                    }
                    Column(modifier = Modifier.padding(horizontal = gutter).padding(top = 14.dp * scale.spacing)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.roundIconButton(onBack), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Back",
                                    tint = NightSession.Text,
                                    modifier = Modifier.padding(horizontal = 12.dp).size(20.dp),
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp * scale.spacing),
                            horizontalArrangement = Arrangement.spacedBy(16.dp * scale.spacing),
                        ) {
                            AsyncImage(
                                model = resolveCoverUrl(anime.cover_url, anime.source),
                                contentDescription = anime.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(124.dp * scale.spacing)
                                    .height(186.dp * scale.spacing)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                                    .background(NightSession.Cover),
                            )
                            Column(modifier = Modifier.weight(1f).padding(top = 14.dp)) {
                                Text(
                                    text = anime.fullTitle ?: anime.displayTitle ?: anime.title,
                                    fontSize = 24.sp * scale.font,
                                    lineHeight = 26.sp * scale.font,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp,
                                    color = NightSession.Text,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val meta = listOfNotNull(
                                    "${episodes.size} eps".takeIf { episodes.isNotEmpty() },
                                    anime.status.takeIf { it.isNotBlank() },
                                ).joinToString(" · ")
                                if (meta.isNotBlank()) {
                                    Text(
                                        text = meta,
                                        fontSize = 13.sp * scale.font,
                                        color = NightSession.TextDim,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                                anime.nextEpisode?.let { next ->
                                    Text(
                                        text = "Episode ${next.episode} · ${formatAirsAt(next.airsAt)}",
                                        fontSize = 12.sp * scale.font,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(top = 8.dp)
                                            .clip(RoundedCornerShape(100))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                                val byline = listOfNotNull(
                                    anime.author?.takeIf { it.isNotBlank() },
                                    anime.source_name.takeIf { it.isNotBlank() },
                                ).joinToString(" · ")
                                if (byline.isNotBlank()) {
                                    Text(
                                        text = byline,
                                        fontSize = 13.sp * scale.font,
                                        color = NightSession.TextDim,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = gutter).padding(top = 18.dp * scale.spacing),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isFavorite && resume != null) {
                        PrimaryActionButton(
                            title = continueLabel(resume, hasAnyWatched),
                            subtitle = minutesLeftLabel(resume),
                            onClick = { onEpisodeClick(resume.key) },
                            modifier = Modifier.weight(1f),
                        )
                    } else if (!isFavorite && libraryMatch == null) {
                        PrimaryActionButton(
                            title = "Add to library",
                            onClick = onToggleFavorite,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f))
                    }
                    SquareGlassButton(onClick = if (isFavorite) onToggleFavorite else ({})) {
                        if (isFavorite) {
                            Icon(Icons.Filled.Favorite, contentDescription = "Remove from library", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        } else {
                            Icon(Icons.Outlined.FavoriteBorder, contentDescription = "Add to library", tint = NightSession.Text, modifier = Modifier.size(22.dp))
                        }
                    }
                    if (isFavorite) {
                        SquareGlassButton(onClick = onOpenTrackerSheet) {
                            Icon(Icons.Outlined.Flag, contentDescription = "Tracker links", tint = NightSession.Text, modifier = Modifier.size(22.dp))
                        }
                    }
                }

                if (libraryMatch != null) {
                    LibraryMatchBanner(
                        sourceName = libraryMatch.sourceName,
                        onOpenLibraryEntry = onOpenLibraryEntry,
                        onUseThisSource = onUseThisSource,
                    )
                }

                AnimeSourcePill(
                    currentName = anime.source_name.ifBlank { anime.source },
                    options = sourceOptions,
                    onOpen = onLoadSourceOptions,
                    onSelect = onSelectSource,
                    modifier = Modifier.padding(horizontal = gutter).padding(top = 14.dp * scale.spacing),
                )

                val description = anime.description
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        fontSize = 12.sp * scale.font,
                        lineHeight = 18.sp * scale.font,
                        color = NightSession.TextDim,
                        modifier = Modifier.padding(horizontal = gutter, vertical = 12.dp * scale.spacing),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = gutter)
                        .padding(top = 12.dp * scale.spacing, bottom = 12.dp * scale.spacing),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${episodes.size} episodes",
                        fontSize = 18.sp * scale.font,
                        fontWeight = FontWeight.SemiBold,
                        color = NightSession.Text,
                    )
                    SegmentedTabs(
                        options = listOf("Newest", "Oldest"),
                        selectedIndex = if (newestFirst) 0 else 1,
                        onSelect = { newestFirst = it == 0 },
                        equalWidth = false,
                    )
                }

                if (episodeError != null) {
                    Text(
                        text = episodeError,
                        fontSize = 11.sp * scale.font,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = gutter).padding(bottom = 8.dp * scale.spacing),
                    )
                } else if (!isFavorite && libraryMatch == null) {
                    Text(
                        text = "Not in your library yet — episodes still play, add it to sync progress.",
                        fontSize = 11.sp * scale.font,
                        color = NightSession.TextDim,
                        modifier = Modifier.padding(horizontal = gutter).padding(bottom = 8.dp * scale.spacing),
                    )
                }
            }
        }

        itemsIndexed(episodes, key = { _, episode -> episode.key }) { index, episode ->
            val localEpisode = localEpisodesByKey[episode.key]
            val shape = RoundedCornerShape(
                topStart = if (index == 0) 18.dp else 0.dp,
                topEnd = if (index == 0) 18.dp else 0.dp,
                bottomStart = if (index == episodes.lastIndex) 18.dp else 0.dp,
                bottomEnd = if (index == episodes.lastIndex) 18.dp else 0.dp,
            )
            EpisodeRow(
                episode = episode,
                localEpisode = localEpisode,
                isNextUp = isFavorite && resume != null && resume.key == episode.key,
                showDivider = index > 0,
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
                modifier = Modifier
                    .padding(horizontal = gutter)
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.045f)),
            )
        }
    }
}

@Composable
private fun SquareGlassButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .glass(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
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
            .glass(RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp * scale.spacing, vertical = 8.dp * scale.spacing),
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(NightSession.Read))
        Text(text = "Watching on", fontSize = 13.sp * scale.font, color = NightSession.TextDim)
        Text(
            text = currentName.ifBlank { "unknown" },
            fontSize = 13.sp * scale.font,
            fontWeight = FontWeight.SemiBold,
            color = NightSession.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    .clickable {
                        if (options is AnimeSourceOptionsState.Loaded) {
                            expanded = true
                        } else {
                            pendingOpen = true
                            onOpen()
                        }
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (options is AnimeSourceOptionsState.Loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Text(
                        text = "Switch",
                        fontSize = 12.sp * scale.font,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
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
}

private fun continueLabel(target: Episode, hasAnyWatched: Boolean): String {
    val number = formatChapterNumber(target.episodeNumber)
    return when {
        !hasAnyWatched -> "Start watching"
        target.positionMs > 0 -> "Continue episode $number"
        else -> "Watch episode $number"
    }
}

private fun minutesLeftLabel(target: Episode): String? {
    if (target.watched || target.positionMs <= 0 || target.durationMs <= target.positionMs) return null
    val minutes = ((target.durationMs - target.positionMs) + 59_999L) / 60_000L
    return "$minutes min left"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    episode: EpisodeDto,
    localEpisode: Episode?,
    isNextUp: Boolean,
    showDivider: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMarkWatched: () -> Unit,
    onMarkPreviousWatched: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalKawabiScale.current
    val enabled = localEpisode != null
    val isWatched = localEpisode?.watched == true
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier = modifier) {
        if (showDivider) HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isNextUp) accentSoft(0.1f) else Color.Transparent)
                .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
                .defaultMinSize(minHeight = 64.dp * scale.spacing)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            isNextUp -> accent
                            isWatched -> NightSession.Read.copy(alpha = 0.14f)
                            else -> Color.White.copy(alpha = 0.07f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isWatched && !isNextUp) {
                    Icon(Icons.Filled.Check, contentDescription = "Watched", tint = NightSession.Read, modifier = Modifier.size(18.dp))
                } else {
                    Text(
                        text = if (episode.number >= 0) formatChapterNumber(episode.number) else "-",
                        fontSize = 13.sp * scale.font,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isNextUp) NightSession.OnAccent else NightSession.Text,
                        maxLines = 1,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episodeLabel(episode),
                    fontSize = 15.sp * scale.font,
                    color = if (isWatched) NightSession.TextDim else NightSession.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val relativeTime = remember(episode.date_upload) {
                    episode.date_upload?.let { formatRelativeTime(it) }
                }
                if (relativeTime != null) {
                    Text(text = relativeTime, fontSize = 11.sp * scale.font, color = NightSession.TextDim)
                }
                val fraction = localEpisode
                    ?.takeIf { !it.watched && it.positionMs > 0 && it.durationMs > 0 }
                    ?.let { (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
                if (fraction != null) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .width(120.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.1f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .background(PROGRESS_RED),
                        )
                    }
                }
            }
            if (isNextUp) CoverPill(text = "CONTINUE", accent = true)
        }
        if (expanded && localEpisode != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onMarkWatched) {
                    Text(
                        text = if (localEpisode.watched) "Mark unwatched" else "Mark watched",
                        color = accent,
                        fontSize = 11.sp * scale.font,
                    )
                }
                TextButton(onClick = onMarkPreviousWatched) {
                    Text("Mark previous watched", color = accent, fontSize = 11.sp * scale.font)
                }
            }
        }
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

private fun formatAirsAt(airsAtSeconds: Long): String {
    val airs = java.time.Instant.ofEpochSecond(airsAtSeconds)
    val zone = java.time.ZoneId.systemDefault()
    val whenText = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", java.util.Locale.getDefault())
        .format(airs.atZone(zone))
    val minutes = java.time.Duration.between(java.time.Instant.now(), airs).toMinutes()
    val remaining = when {
        minutes <= 0 -> "any moment now"
        minutes >= 1440 -> "in ${minutes / 1440}d ${(minutes % 1440) / 60}h"
        minutes >= 60 -> "in ${minutes / 60}h ${minutes % 60}m"
        else -> "in ${minutes}m"
    }
    return "$whenText ($remaining)"
}
