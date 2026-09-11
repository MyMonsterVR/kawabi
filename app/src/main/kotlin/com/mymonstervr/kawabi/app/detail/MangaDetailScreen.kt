package com.mymonstervr.kawabi.app.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mymonstervr.kawabi.app.common.TrackerEditDialog
import com.mymonstervr.kawabi.app.common.TrackerLinkSheetContent
import com.mymonstervr.kawabi.app.common.TrackerSearchDialog
import com.mymonstervr.kawabi.app.common.TrackerSheetLink
import com.mymonstervr.kawabi.app.common.TrackerSheetRow
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.data.network.dto.ChapterDto
import com.mymonstervr.kawabi.data.network.dto.MangaResponse
import com.mymonstervr.kawabi.data.network.resolveCoverUrl
import com.mymonstervr.kawabi.data.settings.ReadingDirection
import com.mymonstervr.kawabi.domain.model.Chapter
import com.mymonstervr.kawabi.domain.model.MediaType
import com.mymonstervr.kawabi.domain.model.formatChapterNumber
import com.mymonstervr.kawabi.domain.model.formatRelativeTime
import com.mymonstervr.kawabi.domain.model.normalizedScanlator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private val DETAIL_HERO_HEIGHT = 190.dp
// A fixed, predictable offset rather than measuring header content -- description
// length varies per manga, and clamping against a measured height felt like it started
// in an arbitrary place. This just clears the top app bar area.
private val FAST_SCROLL_TOP_OFFSET = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDetailScreen(
    url: String,
    onBack: () -> Unit,
    onChapterClick: (Long) -> Unit,
    onOpenTrackingSettings: () -> Unit,
    viewModel: MangaDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(url) { viewModel.load(url) }

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
    val localChaptersByUrl by viewModel.localChaptersByUrl.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val sourcePicker by viewModel.sourcePicker.collectAsState()
    val siteNames by viewModel.siteNames.collectAsState()
    val lastTitle by viewModel.lastTitle.collectAsState()
    val trackerSheet by viewModel.trackerSheet.collectAsState()
    val altTitleSuggestions by viewModel.altTitleSuggestions.collectAsState()
    val preferredScanlator by viewModel.preferredScanlator.collectAsState()
    val hideReadChapters by viewModel.hideReadChapters.collectAsState()
    val chapterSortAscending by viewModel.chapterSortAscending.collectAsState()
    val readingDirectionOverride by viewModel.readingDirectionOverride.collectAsState()
    val pullState = rememberPullToRefreshState()
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var searchingTrackerId by remember { mutableStateOf<String?>(null) }
    var editingTrackerId by remember { mutableStateOf<String?>(null) }

    val trackerSheetShown = trackerSheet as? TrackerSheetState.Shown
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
            mediaType = MediaType.MANGA,
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
            mediaType = MediaType.MANGA,
            onDismiss = { editingTrackerId = null },
            onSave = { chaptersRead, status, score ->
                viewModel.updateTrackDetails(editingTrack, chaptersRead, status, score) { editingTrackerId = null }
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
            text = { Text("Reading progress stays saved even after removal.") },
            confirmButton = {
                TextButton(onClick = { showRemoveConfirm = false; viewModel.toggleFavorite(url) }) {
                    Text("Remove")
                }
            },
            dismissButton = { TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") } },
            containerColor = NightSession.Chip,
        )
    }

    Scaffold(containerColor = NightSession.Background) { padding ->
        com.mymonstervr.kawabi.app.common.ResponsiveContainer(modifier = Modifier.padding(padding)) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (val current = state) {
                is MangaDetailState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                is MangaDetailState.Error -> MangaDetailErrorContent(
                    message = current.message,
                    onBack = onBack,
                    onRetry = { viewModel.load(url) },
                    isLoggedIn = isLoggedIn,
                    sourcePicker = sourcePicker,
                    onOpenSourcePicker = { viewModel.loadSourceOptions(url, lastTitle) },
                    onSelectSource = { siteKey -> viewModel.selectSource(url, siteKey, lastTitle) },
                )
                is MangaDetailState.Success -> PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh(url) },
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    MangaDetailContent(
                        manga = current.manga,
                        isFavorite = isFavorite,
                        onBack = onBack,
                        onToggleFavorite = {
                            if (isFavorite) showRemoveConfirm = true else viewModel.toggleFavorite(url)
                        },
                        localChaptersByUrl = localChaptersByUrl,
                        preferredScanlator = preferredScanlator,
                        onSetPreferredScanlator = viewModel::setPreferredScanlator,
                        hideReadChapters = hideReadChapters,
                        onSetHideReadChapters = viewModel::setHideReadChapters,
                        chapterSortAscending = chapterSortAscending,
                        onSetChapterSortAscending = viewModel::setChapterSortAscending,
                        readingDirectionOverride = readingDirectionOverride,
                        onSetReadingDirectionOverride = viewModel::setReadingDirectionOverride,
                        onChapterClick = onChapterClick,
                        onSetChapterRead = viewModel::setChapterRead,
                        onMarkPreviousAsRead = viewModel::markPreviousAsRead,
                        isLoggedIn = isLoggedIn,
                        sourcePicker = sourcePicker,
                        siteNames = siteNames,
                        onOpenSourcePicker = { viewModel.loadSourceOptions(url, current.manga.title) },
                        onSelectSource = { siteKey -> viewModel.selectSource(url, siteKey, current.manga.title) },
                        onOpenTrackerSheet = viewModel::openTrackerSheet,
                    )
                }
            }
        }
        }
    }
}

// A manga is "multi-version" when at least one chapter number has two or more chapters
// with differing scanlators (MangaFire's official/unofficial pairs being the confirmed
// case). Everything version-related -- badges, the version pill, preferred-version
// filtering -- only ever engages when this is true, so a single-version manga (the
// overwhelming majority of sources) renders exactly as it did before this feature existed.
private fun List<ChapterDto>.chapterVersionOptions(): List<Pair<String?, String>> {
    val duplicatedNumbers = groupBy { it.number }
        .filterValues { group -> group.map { it.scanlator.normalizedScanlator() }.distinct().size > 1 }
        .keys
    if (duplicatedNumbers.isEmpty()) return emptyList()
    return filter { it.number in duplicatedNumbers }
        .groupBy { it.scanlator.normalizedScanlator() }
        .map { (normalized, group) ->
            val label = group.firstNotNullOfOrNull { it.scanlator?.trim()?.ifBlank { null } }
                ?.replaceFirstChar { c -> c.titlecase() }
                ?: "Unknown"
            normalized to label
        }
        .sortedByDescending { (normalized, _) -> count { it.scanlator.normalizedScanlator() == normalized } }
}

private fun ChapterDto.versionBadgeLabel(): String =
    scanlator?.trim()?.ifBlank { null }?.replaceFirstChar { it.titlecase() } ?: "Unknown"

@Composable
private fun MangaDetailContent(
    manga: MangaResponse,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    localChaptersByUrl: Map<String, Chapter>,
    preferredScanlator: String?,
    onSetPreferredScanlator: (String?) -> Unit,
    hideReadChapters: Boolean,
    onSetHideReadChapters: (Boolean) -> Unit,
    chapterSortAscending: Boolean,
    onSetChapterSortAscending: (Boolean) -> Unit,
    readingDirectionOverride: ReadingDirection?,
    onSetReadingDirectionOverride: (ReadingDirection?) -> Unit,
    onChapterClick: (Long) -> Unit,
    onSetChapterRead: (Long, Boolean) -> Unit,
    onMarkPreviousAsRead: (Chapter) -> Unit,
    isLoggedIn: Boolean,
    sourcePicker: SourcePickerState,
    siteNames: Map<String, String>,
    onOpenSourcePicker: () -> Unit,
    onSelectSource: (String) -> Unit,
    onOpenTrackerSheet: () -> Unit,
) {
    val scale = LocalKawabiScale.current
    var expandedChapterUrl by remember { mutableStateOf<String?>(null) }
    var showJumpDialog by remember { mutableStateOf(false) }
    val target = remember(localChaptersByUrl, preferredScanlator) { resumeTarget(localChaptersByUrl.values, preferredScanlator) }
    val hasAnyRead = remember(localChaptersByUrl) { localChaptersByUrl.values.any { it.read } }

    val versionOptions = remember(manga.chapters) { manga.chapters.chapterVersionOptions() }
    val isMultiVersion = versionOptions.size > 1

    val displayedChapters = remember(manga.chapters, localChaptersByUrl, preferredScanlator, hideReadChapters, chapterSortAscending, isMultiVersion) {
        var list = manga.chapters
        if (isMultiVersion && preferredScanlator != null) {
            val byNumber = list.groupBy { it.number }
            list = byNumber.flatMap { (_, group) ->
                group.filter { it.scanlator.normalizedScanlator() == preferredScanlator } .ifEmpty { group }
            }
        }
        if (hideReadChapters) {
            list = list.filter { localChaptersByUrl[it.id]?.read != true }
        }
        list.sortedWith(compareBy<ChapterDto> { it.number }.let { if (chapterSortAscending) it else it.reversed() })
    }

    // The chapter list shares this LazyColumn with all the header content above it --
    // header items are conditional (login state, description presence), so collapsing
    // them into one item{} gives the chapter list a constant flat offset of 1, valid
    // regardless of which optional header pieces render for this manga.
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val headerOffset = 1

    if (showJumpDialog) {
        JumpToChapterDialog(
            onDismiss = { showJumpDialog = false },
            onJump = { number ->
                val index = displayedChapters.indexOfFirst { it.number == number }
                    .let { if (it >= 0) it else displayedChapters.indexOfFirst { c -> c.number >= number } }
                if (index >= 0) {
                    scope.launch { listState.animateScrollToItem(headerOffset + index) }
                }
                showJumpDialog = false
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().height(DETAIL_HERO_HEIGHT * scale.spacing)) {
                        AsyncImage(
                            model = resolveCoverUrl(manga.cover_url),
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
                            model = resolveCoverUrl(manga.cover_url),
                            contentDescription = manga.title,
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
                        Text(text = manga.title, fontSize = 16.5.sp * scale.font, fontWeight = FontWeight.Bold, color = NightSession.Text)
                        val byline = listOfNotNull(manga.author?.takeIf { it.isNotBlank() }, manga.status.takeIf { it.isNotBlank() })
                            .joinToString(" · ")
                        if (byline.isNotBlank()) {
                            Text(text = byline, fontSize = 11.sp * scale.font, color = NightSession.TextDim, modifier = Modifier.padding(top = 3.dp * scale.spacing))
                        }
                    }

                    if (isLoggedIn) {
                        SourcePickerPill(
                            picker = sourcePicker,
                            servedFrom = manga.served_from,
                            isPinned = manga.preferred_source != null,
                            siteNames = siteNames,
                            onOpen = onOpenSourcePicker,
                            onSelect = onSelectSource,
                            modifier = Modifier.padding(horizontal = 16.dp * scale.spacing, vertical = 4.dp * scale.spacing),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp * scale.spacing, vertical = 4.dp * scale.spacing),
                        horizontalArrangement = Arrangement.spacedBy(8.dp * scale.spacing),
                    ) {
                        if (isFavorite && target != null) {
                            Button(
                                onClick = { onChapterClick(target.id) },
                                shape = RoundedCornerShape(NightSession.RadiusMd),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = NightSession.OnAccent),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(continueLabel(manga, target, hasAnyRead), fontWeight = FontWeight.Bold, fontSize = 12.sp * scale.font)
                            }
                        } else if (!isFavorite) {
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
                        // Tracker linking only makes sense once this manga is actually in the
                        // library -- same gating as the heart itself.
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

                    val description = manga.description
                    if (!description.isNullOrBlank()) {
                        Text(
                            text = description,
                            fontSize = 11.5.sp * scale.font,
                            lineHeight = 17.sp * scale.font,
                            color = NightSession.TextDim,
                            modifier = Modifier.padding(horizontal = 16.dp * scale.spacing, vertical = 8.dp * scale.spacing),
                        )
                    }

                    ChapterListControlsRow(
                        // Distinct chapter NUMBERS, not rows -- a multi-version manga's
                        // official/unofficial pair is one chapter, not two, even though
                        // "Show both" renders both as separate rows.
                        chapterCount = displayedChapters.map { it.number }.distinct().size,
                        isMultiVersion = isMultiVersion,
                        versionOptions = versionOptions,
                        preferredScanlator = preferredScanlator,
                        onSetPreferredScanlator = onSetPreferredScanlator,
                        hideReadChapters = hideReadChapters,
                        onSetHideReadChapters = onSetHideReadChapters,
                        sortAscending = chapterSortAscending,
                        onSetSortAscending = onSetChapterSortAscending,
                        readingDirectionOverride = readingDirectionOverride,
                        onSetReadingDirectionOverride = onSetReadingDirectionOverride,
                        onJump = { showJumpDialog = true },
                    )

                    if (!isFavorite) {
                        Text(
                            text = "Add to library to read",
                            fontSize = 10.5.sp * scale.font,
                            color = NightSession.TextDim,
                            modifier = Modifier.padding(start = 16.dp * scale.spacing, end = 16.dp * scale.spacing, top = 2.dp * scale.spacing, bottom = 6.dp * scale.spacing),
                        )
                    }
                    HorizontalDivider(color = NightSession.Hairline)
                }
            }

            items(displayedChapters, key = { it.id }) { chapter ->
                val localChapter = localChaptersByUrl[chapter.id]
                ChapterRow(
                    chapter = chapter,
                    localChapter = localChapter,
                    showVersionBadge = isMultiVersion,
                    expanded = expandedChapterUrl == chapter.id,
                    onClick = { localChapter?.let { onChapterClick(it.id) } },
                    onLongClick = { if (localChapter != null) expandedChapterUrl = chapter.id },
                    onMarkRead = {
                        localChapter?.let { onSetChapterRead(it.id, !it.read) }
                        expandedChapterUrl = null
                    },
                    onMarkPreviousRead = {
                        localChapter?.let(onMarkPreviousAsRead)
                        expandedChapterUrl = null
                    },
                )
            }
        }

        com.mymonstervr.kawabi.app.common.FastScroller(
            listState = listState,
            itemCount = displayedChapters.size,
            headerOffset = headerOffset,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = FAST_SCROLL_TOP_OFFSET * scale.spacing)
                .fillMaxHeight()
                .width(36.dp * scale.spacing),
        )
    }
}

@Composable
private fun ChapterListControlsRow(
    chapterCount: Int,
    isMultiVersion: Boolean,
    versionOptions: List<Pair<String?, String>>,
    preferredScanlator: String?,
    onSetPreferredScanlator: (String?) -> Unit,
    hideReadChapters: Boolean,
    onSetHideReadChapters: (Boolean) -> Unit,
    sortAscending: Boolean,
    onSetSortAscending: (Boolean) -> Unit,
    readingDirectionOverride: ReadingDirection?,
    onSetReadingDirectionOverride: (ReadingDirection?) -> Unit,
    onJump: () -> Unit,
) {
    val scale = LocalKawabiScale.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp * scale.spacing, end = 16.dp * scale.spacing, top = 8.dp * scale.spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "$chapterCount chapters", fontSize = 12.sp * scale.font, fontWeight = FontWeight.Bold, color = NightSession.Text, modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 16.dp * scale.spacing, vertical = 6.dp * scale.spacing),
            horizontalArrangement = Arrangement.spacedBy(6.dp * scale.spacing),
        ) {
            ChipToggle(
                label = if (sortAscending) "Oldest first" else "Newest first",
                onClick = { onSetSortAscending(!sortAscending) },
            )
            ChipToggle(
                label = "Hide read",
                selected = hideReadChapters,
                onClick = { onSetHideReadChapters(!hideReadChapters) },
            )
            ChipToggle(label = "Jump", onClick = onJump)
            if (isMultiVersion) {
                VersionPickerChip(
                    options = versionOptions,
                    selected = preferredScanlator,
                    onSelect = onSetPreferredScanlator,
                )
            }
            ReadingDirectionChip(selected = readingDirectionOverride, onSelect = onSetReadingDirectionOverride)
        }
    }
}

@Composable
private fun ReadingDirectionChip(selected: ReadingDirection?, onSelect: (ReadingDirection?) -> Unit) {
    val scale = LocalKawabiScale.current
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = selected?.chipLabel() ?: "Auto"

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100))
                .background(NightSession.Chip)
                .border(1.dp, NightSession.Hairline, RoundedCornerShape(100))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp * scale.spacing, vertical = 5.dp * scale.spacing),
        ) {
            Text(text = "Direction: $selectedLabel", fontSize = 10.5.sp * scale.font, fontWeight = FontWeight.SemiBold, color = NightSession.TextDim, maxLines = 1, softWrap = false)
        }
        if (expanded) {
            androidx.compose.material3.DropdownMenu(expanded = true, onDismissRequest = { expanded = false }, containerColor = NightSession.Chip) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Auto (Settings default)", color = NightSession.Text, fontSize = 12.sp * scale.font) },
                    trailingIcon = if (selected == null) { { Text("✓", color = MaterialTheme.colorScheme.primary) } } else null,
                    onClick = { onSelect(null); expanded = false },
                )
                ReadingDirection.entries.forEach { direction ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(direction.chipLabel(), color = NightSession.Text, fontSize = 12.sp * scale.font) },
                        trailingIcon = if (direction == selected) { { Text("✓", color = MaterialTheme.colorScheme.primary) } } else null,
                        onClick = { onSelect(direction); expanded = false },
                    )
                }
            }
        }
    }
}

private fun ReadingDirection.chipLabel(): String = when (this) {
    ReadingDirection.LEFT_TO_RIGHT -> "Left-to-right"
    ReadingDirection.RIGHT_TO_LEFT -> "Right-to-left"
    ReadingDirection.VERTICAL -> "Vertical"
}

@Composable
private fun ChipToggle(label: String, onClick: () -> Unit, selected: Boolean = false) {
    val scale = LocalKawabiScale.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else NightSession.Chip)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else NightSession.Hairline, RoundedCornerShape(100))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp * scale.spacing, vertical = 5.dp * scale.spacing),
    ) {
        Text(
            text = label,
            fontSize = 10.5.sp * scale.font,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.primary else NightSession.TextDim,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun VersionPickerChip(
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val scale = LocalKawabiScale.current
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "Show both"

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100))
                .background(NightSession.Chip)
                .border(1.dp, NightSession.Hairline, RoundedCornerShape(100))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp * scale.spacing, vertical = 5.dp * scale.spacing),
        ) {
            Text(text = "Version: $selectedLabel", fontSize = 10.5.sp * scale.font, fontWeight = FontWeight.SemiBold, color = NightSession.TextDim, maxLines = 1, softWrap = false)
        }
        if (expanded) {
            androidx.compose.material3.DropdownMenu(expanded = true, onDismissRequest = { expanded = false }, containerColor = NightSession.Chip) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Show both", color = NightSession.Text, fontSize = 12.sp * scale.font) },
                    trailingIcon = if (selected == null) { { Text("✓", color = MaterialTheme.colorScheme.primary) } } else null,
                    onClick = { onSelect(null); expanded = false },
                )
                options.forEach { (value, label) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(label, color = NightSession.Text, fontSize = 12.sp * scale.font) },
                        trailingIcon = if (value == selected) { { Text("✓", color = MaterialTheme.colorScheme.primary) } } else null,
                        onClick = { onSelect(value); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun JumpToChapterDialog(onDismiss: () -> Unit, onJump: (Double) -> Unit) {
    val scale = LocalKawabiScale.current
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(NightSession.RadiusMd), color = NightSession.Chip) {
            Column(modifier = Modifier.padding(20.dp * scale.spacing)) {
                Text("Jump to chapter", color = NightSession.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp * scale.font)
                Spacer(modifier = Modifier.height(12.dp * scale.spacing))
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Chapter number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { text.toDoubleOrNull()?.let(onJump) }),
                    colors = TextFieldDefaults.colors(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp * scale.spacing))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = NightSession.TextDim) }
                    TextButton(onClick = { text.toDoubleOrNull()?.let(onJump) }) {
                        Text("Go", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Backend error messages are internal diagnostics ("suwayomi: HTTP error 403",
// "preferred source failed: not available on comick.live") -- an internal engine name or
// raw HTTP code means nothing to someone who just picked "Comick" from a list. Map the
// handful of known shapes to plain language instead of trying to sanitize the raw string
// (a previous version of this function only stripped URLs out of the raw text, which
// still left the technical parts and could leave a dangling sentence fragment if the
// message happened to end in one).
private fun friendlyErrorMessage(raw: String): String = when {
    raw.contains("not available on") || raw.contains("not found", ignoreCase = true) ->
        "This manga isn't available on that source."
    raw.contains("403") -> "This source is blocking requests right now -- try another one."
    raw.contains("HTTP error 5") || raw.contains("HTTP 5") ->
        "This source is having trouble loading right now."
    raw.isBlank() -> "Something went wrong loading this source."
    else -> "This source couldn't be loaded right now."
}

// Previously a dead end: a failed load (e.g. the source's own upstream site blocking the
// backend, a real 502 seen in practice) showed only error text -- no back button, no
// retry, no way to reach the source picker since that pill only rendered inside the
// Success branch. Give the user a way out and a way to try a different source even when
// the default pick is completely broken.
@Composable
private fun MangaDetailErrorContent(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    isLoggedIn: Boolean,
    sourcePicker: SourcePickerState,
    onOpenSourcePicker: () -> Unit,
    onSelectSource: (String) -> Unit,
) {
    val scale = LocalKawabiScale.current
    val reason = friendlyErrorMessage(message)
    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp * scale.spacing)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp * scale.spacing).align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = NightSession.TextDim,
                modifier = Modifier.size(40.dp * scale.spacing),
            )
            Spacer(modifier = Modifier.height(12.dp * scale.spacing))
            Text(text = "Couldn't load this source", color = NightSession.Text, fontSize = 14.sp * scale.font, fontWeight = FontWeight.Bold)
            if (reason.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp * scale.spacing))
                Text(text = reason, color = NightSession.TextDim, fontSize = 11.sp * scale.font)
            }
            Spacer(modifier = Modifier.height(16.dp * scale.spacing))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(NightSession.RadiusMd),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = NightSession.OnAccent),
            ) { Text("Retry", fontWeight = FontWeight.Bold) }

            if (isLoggedIn) {
                Spacer(modifier = Modifier.height(16.dp * scale.spacing))
                Text(
                    text = "Or try a different source for this manga:",
                    color = NightSession.TextDim,
                    fontSize = 11.sp * scale.font,
                )
                Spacer(modifier = Modifier.height(8.dp * scale.spacing))
                SourcePickerPill(
                    picker = sourcePicker,
                    onOpen = onOpenSourcePicker,
                    onSelect = onSelectSource,
                )
            }
        }
    }
}

@Composable
private fun SourcePickerPill(
    picker: SourcePickerState,
    onOpen: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    servedFrom: String? = null,
    // From the manga response's own preferred_source field (set by the backend whenever
    // it served this from an explicit per-manga pin) -- known immediately on page load,
    // unlike the picker's own `selected`, which only exists once the (expensive, live-
    // probing) picker has been opened. Without this, the pill guessed "(auto)" for every
    // manga until the picker was opened, then dropped it once the picker revealed the
    // pin was actually explicit -- looked like the label was randomly changing.
    isPinned: Boolean = false,
    siteNames: Map<String, String> = emptyMap(),
) {
    val scale = LocalKawabiScale.current
    var expanded by remember { mutableStateOf(false) }
    val selectedName = (picker as? SourcePickerState.Loaded)?.let { loaded ->
        loaded.options.firstOrNull { it.key == loaded.selected }?.name
    }
    // Show what's actually serving the manga right now (resolved from the manga
    // response's served_from). Prefer the picker's own loaded names (only present once
    // the live per-manga probe has run), fall back to the cheap GET /sources lookup
    // (available immediately, no live probing) so this doesn't show a raw domain while
    // waiting on the user to open the picker.
    val autoName = (picker as? SourcePickerState.Loaded)?.options?.firstOrNull { it.key == servedFrom }?.name
        ?: servedFrom?.let { siteNames[it] }
        ?: servedFrom
    val label = when {
        selectedName != null -> selectedName
        isPinned -> autoName ?: "this source"
        autoName != null -> "$autoName (auto)"
        else -> "Default (auto)"
    }

    Box(modifier = modifier) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100))
                .background(NightSession.Chip)
                .border(1.dp, NightSession.Hairline, RoundedCornerShape(100))
                .clickable {
                    expanded = true
                    if (picker is SourcePickerState.Idle || picker is SourcePickerState.Error) onOpen()
                }
                .padding(horizontal = 10.dp * scale.spacing, vertical = 5.dp * scale.spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Source: $label",
                fontSize = 10.5.sp * scale.font,
                fontWeight = FontWeight.SemiBold,
                color = NightSession.TextDim,
            )
        }

        if (expanded) {
            androidx.compose.material3.DropdownMenu(
                expanded = true,
                onDismissRequest = { expanded = false },
                containerColor = NightSession.Chip,
            ) {
                when (picker) {
                    is SourcePickerState.Loading, SourcePickerState.Idle -> {
                        Box(Modifier.padding(24.dp * scale.spacing), Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp * scale.spacing))
                        }
                    }
                    is SourcePickerState.Error -> {
                        Text(picker.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp * scale.spacing))
                    }
                    is SourcePickerState.Loaded -> {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Default (auto)", color = NightSession.Text, fontSize = 12.sp * scale.font) },
                            trailingIcon = if (picker.selected == null) {
                                { Text("✓", color = MaterialTheme.colorScheme.primary) }
                            } else null,
                            onClick = { onSelect(""); expanded = false },
                        )
                        picker.options.forEach { option ->
                            val available = option.status != "unavailable"
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(option.name, color = if (available) NightSession.Text else NightSession.TextDim, fontSize = 12.sp * scale.font)
                                        Text(
                                            text = when (option.status) {
                                                "available" -> "Available"
                                                "unavailable" -> "Not found"
                                                else -> "Checking…"
                                            },
                                            color = NightSession.TextDim,
                                            fontSize = 9.5.sp * scale.font,
                                        )
                                    }
                                },
                                trailingIcon = if (option.key == picker.selected) {
                                    { Text("✓", color = MaterialTheme.colorScheme.primary) }
                                } else null,
                                enabled = available,
                                onClick = { onSelect(option.key); expanded = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

// Mirrors kawabi-web's continue-button semantics: "Start reading" if nothing's been
// read yet, "Continue chapter N" if resuming mid-page, else plain "Chapter N".
private fun continueLabel(manga: MangaResponse, target: Chapter, hasAnyRead: Boolean): String {
    val label = manga.chapters.firstOrNull { it.id == target.url }?.let { chapterLabel(it) }
        ?: "Chapter ${formatChapterNumber(target.chapterNumber)}"
    return when {
        !hasAnyRead -> "Start reading"
        target.lastPageRead > 0 -> "Continue $label"
        else -> label
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRow(
    chapter: ChapterDto,
    localChapter: Chapter?,
    showVersionBadge: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkPreviousRead: () -> Unit,
) {
    val scale = LocalKawabiScale.current
    val enabled = localChapter != null
    val label = chapterLabel(chapter)
    val isRead = localChapter?.read == true
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
                    text = label,
                    fontSize = 11.5.sp * scale.font,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isRead) NightSession.TextDim else NightSession.Text,
                )
                val relativeTime = remember(chapter.date_upload) {
                    chapter.date_upload?.let { formatRelativeTime(it) }
                }
                if (relativeTime != null) {
                    Text(
                        text = relativeTime,
                        fontSize = 9.5.sp * scale.font,
                        color = NightSession.TextDim,
                    )
                }
            }
            if (showVersionBadge) {
                Text(
                    text = chapter.versionBadgeLabel(),
                    fontSize = 9.5.sp * scale.font,
                    fontWeight = FontWeight.SemiBold,
                    color = NightSession.TextDim,
                    modifier = Modifier
                        .padding(end = 8.dp * scale.spacing)
                        .clip(RoundedCornerShape(100))
                        .background(NightSession.Chip)
                        .border(1.dp, NightSession.Hairline, RoundedCornerShape(100))
                        .padding(horizontal = 7.dp * scale.spacing, vertical = 2.dp * scale.spacing),
                )
            }
            if (isRead) {
                Text(text = "✓", fontSize = 11.sp * scale.font, color = NightSession.Read)
            }
        }
        if (expanded && localChapter != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp * scale.spacing, vertical = 6.dp * scale.spacing),
                horizontalArrangement = Arrangement.spacedBy(8.dp * scale.spacing),
            ) {
                TextButton(onClick = onMarkRead) {
                    Text(if (localChapter.read) "Mark unread" else "Mark read", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp * scale.font)
                }
                TextButton(onClick = onMarkPreviousRead) {
                    Text("Mark previous read", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp * scale.font)
                }
            }
        }
        HorizontalDivider(color = NightSession.Hairline, modifier = Modifier.padding(horizontal = 16.dp * scale.spacing))
    }
}

private fun chapterLabel(chapter: ChapterDto): String =
    chapter.title.ifBlank { "Chapter ${formatChapterNumber(chapter.number)}" }

private fun TrackerLinkRow.toSheetRow(): TrackerSheetRow = TrackerSheetRow(
    trackerId = trackerId,
    trackerName = trackerName,
    linked = linked?.let {
        TrackerSheetLink(progress = it.lastChapterRead, total = it.totalChapters, score = it.score, status = it.status)
    },
)
