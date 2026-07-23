package com.mymonstervr.kawabi.app.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.data.network.dto.ChapterDto
import com.mymonstervr.kawabi.data.network.dto.MangaResponse
import com.mymonstervr.kawabi.data.network.resolveCoverUrl
import com.mymonstervr.kawabi.data.track.dto.TrackSearchResult
import com.mymonstervr.kawabi.domain.model.Chapter
import com.mymonstervr.kawabi.domain.model.Track
import org.koin.androidx.compose.koinViewModel

private val DETAIL_HERO_HEIGHT = 190.dp

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
    val pullState = rememberPullToRefreshState()
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var searchingTrackerId by remember { mutableStateOf<String?>(null) }
    var editingTrackerId by remember { mutableStateOf<String?>(null) }

    val trackerSheetShown = trackerSheet as? TrackerSheetState.Shown
    if (trackerSheetShown != null) {
        ModalBottomSheet(onDismissRequest = viewModel::closeTrackerSheet, containerColor = NightSession.Chip) {
            TrackerLinkSheetContent(
                rows = trackerSheetShown.rows,
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
            track = editingTrack,
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

@Composable
private fun MangaDetailContent(
    manga: MangaResponse,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    localChaptersByUrl: Map<String, Chapter>,
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
    var expandedChapterUrl by remember { mutableStateOf<String?>(null) }
    val target = remember(localChaptersByUrl) { resumeTarget(localChaptersByUrl.values) }
    val hasAnyRead = remember(localChaptersByUrl) { localChaptersByUrl.values.any { it.read } }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(DETAIL_HERO_HEIGHT)) {
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
                        .padding(12.dp)
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
                        .padding(start = 16.dp, bottom = 14.dp)
                        .width(80.dp)
                        .height(120.dp)
                        .clip(RoundedCornerShape(NightSession.RadiusMd))
                        .border(1.dp, NightSession.Hairline, RoundedCornerShape(NightSession.RadiusMd))
                        .background(NightSession.Cover),
                )
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(text = manga.title, fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = NightSession.Text)
                val byline = listOfNotNull(manga.author?.takeIf { it.isNotBlank() }, manga.status.takeIf { it.isNotBlank() })
                    .joinToString(" · ")
                if (byline.isNotBlank()) {
                    Text(text = byline, fontSize = 11.sp, color = NightSession.TextDim, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }

        if (isLoggedIn) {
            item {
                SourcePickerPill(
                    picker = sourcePicker,
                    servedFrom = manga.served_from,
                    isPinned = manga.preferred_source != null,
                    siteNames = siteNames,
                    onOpen = onOpenSourcePicker,
                    onSelect = onSelectSource,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isFavorite && target != null) {
                    Button(
                        onClick = { onChapterClick(target.id) },
                        shape = RoundedCornerShape(NightSession.RadiusMd),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = NightSession.OnAccent),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(continueLabel(manga, target, hasAnyRead), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else if (!isFavorite) {
                    Button(
                        onClick = onToggleFavorite,
                        shape = RoundedCornerShape(NightSession.RadiusMd),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = NightSession.OnAccent),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Add to library", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                IconButton(
                    onClick = if (isFavorite) onToggleFavorite else ({}),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(NightSession.RadiusMd))
                        .background(NightSession.Chip)
                        .border(1.dp, if (isFavorite) MaterialTheme.colorScheme.primary else NightSession.Hairline, RoundedCornerShape(NightSession.RadiusMd)),
                ) {
                    if (isFavorite) {
                        Icon(Icons.Filled.Favorite, contentDescription = "Remove from library", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Outlined.FavoriteBorder, contentDescription = "Add to library", tint = NightSession.Text, modifier = Modifier.size(16.dp))
                    }
                }
                // Tracker linking only makes sense once this manga is actually in the
                // library -- same gating as the heart itself.
                if (isFavorite) {
                    IconButton(
                        onClick = onOpenTrackerSheet,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(NightSession.RadiusMd))
                            .background(NightSession.Chip)
                            .border(1.dp, NightSession.Hairline, RoundedCornerShape(NightSession.RadiusMd)),
                    ) {
                        Icon(Icons.Outlined.Flag, contentDescription = "Tracker links", tint = NightSession.Text, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        val description = manga.description
        if (!description.isNullOrBlank()) {
            item {
                Text(
                    text = description,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    color = NightSession.TextDim,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(text = "${manga.chapters.size} chapters", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NightSession.Text)
                if (!isFavorite) {
                    Text(
                        text = "Add to library to read",
                        fontSize = 10.5.sp,
                        color = NightSession.TextDim,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            HorizontalDivider(color = NightSession.Hairline)
        }

        items(manga.chapters, key = { it.id }) { chapter ->
            val localChapter = localChaptersByUrl[chapter.id]
            ChapterRow(
                chapter = chapter,
                localChapter = localChapter,
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
    val reason = friendlyErrorMessage(message)
    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = NightSession.TextDim,
                modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Couldn't load this source", color = NightSession.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (reason.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = reason, color = NightSession.TextDim, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(NightSession.RadiusMd),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = NightSession.OnAccent),
            ) { Text("Retry", fontWeight = FontWeight.Bold) }

            if (isLoggedIn) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Or try a different source for this manga:",
                    color = NightSession.TextDim,
                    fontSize = 11.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
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
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Source: $label",
                fontSize = 10.5.sp,
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
                        Box(Modifier.padding(24.dp), Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                    is SourcePickerState.Error -> {
                        Text(picker.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    }
                    is SourcePickerState.Loaded -> {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Default (auto)", color = NightSession.Text, fontSize = 12.sp) },
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
                                        Text(option.name, color = if (available) NightSession.Text else NightSession.TextDim, fontSize = 12.sp)
                                        Text(
                                            text = when (option.status) {
                                                "available" -> "Available"
                                                "unavailable" -> "Not found"
                                                else -> "Checking…"
                                            },
                                            color = NightSession.TextDim,
                                            fontSize = 9.5.sp,
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
    expanded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkPreviousRead: () -> Unit,
) {
    val enabled = localChapter != null
    val label = chapterLabel(chapter)
    val isRead = localChapter?.read == true
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isRead) NightSession.TextDim else NightSession.Text,
                modifier = Modifier.weight(1f),
            )
            if (isRead) {
                Text(text = "✓", fontSize = 11.sp, color = NightSession.Read)
            }
        }
        if (expanded && localChapter != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onMarkRead) {
                    Text(if (localChapter.read) "Mark unread" else "Mark read", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                }
                TextButton(onClick = onMarkPreviousRead) {
                    Text("Mark previous read", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                }
            }
        }
        HorizontalDivider(color = NightSession.Hairline, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

private fun chapterLabel(chapter: ChapterDto): String =
    chapter.title.ifBlank { "Chapter ${formatChapterNumber(chapter.number)}" }

private fun formatChapterNumber(number: Double): String =
    if (number == number.toLong().toDouble()) number.toLong().toString() else number.toString()

@Composable
private fun TrackerLinkSheetContent(
    rows: List<TrackerLinkRow>,
    onOpenSearch: (trackerId: String) -> Unit,
    onOpenEdit: (trackerId: String) -> Unit,
    onGoToSettings: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Text("Tracker links", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NightSession.Text, modifier = Modifier.padding(bottom = 8.dp))
        if (rows.isEmpty()) {
            Text("Not connected to any tracker yet.", fontSize = 11.5.sp, color = NightSession.TextDim)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onGoToSettings) {
                Text("Go to Settings -> Tracking services", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
            }
        } else {
            rows.forEachIndexed { index, row ->
                TrackerLinkRowContent(
                    row = row,
                    onClick = { if (row.linked != null) onOpenEdit(row.trackerId) else onOpenSearch(row.trackerId) },
                )
                if (index != rows.lastIndex) {
                    HorizontalDivider(color = NightSession.Hairline, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun TrackerLinkRowContent(row: TrackerLinkRow, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.trackerName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NightSession.Text)
            val linked = row.linked
            Text(
                text = when {
                    linked == null -> "Not linked"
                    else -> buildString {
                        append(formatChapterNumber(linked.lastChapterRead))
                        if (linked.totalChapters > 0) append(" / ${formatChapterNumber(linked.totalChapters)}")
                        if (linked.score > 0) append(" ★ ${formatChapterNumber(linked.score)}")
                    }
                },
                fontSize = 10.5.sp,
                color = NightSession.TextDim,
            )
        }
        if (row.linked != null) {
            Icon(Icons.Outlined.Edit, contentDescription = "Edit ${row.trackerName} link", tint = NightSession.TextDim, modifier = Modifier.size(16.dp))
        } else {
            Icon(Icons.Filled.Add, contentDescription = "Link on ${row.trackerName}", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
    }
}

// Full-screen so a cover-thumbnail result list has room to actually be useful --
// a same-title-different-language/region manga is otherwise indistinguishable
// from text alone (owner feedback after testing the inline version).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackerSearchDialog(
    trackerName: String,
    initialQuery: String,
    searching: Boolean,
    results: List<TrackSearchResult>?,
    error: String?,
    altTitleSuggestions: List<String>,
    onSearch: (String) -> Unit,
    onSelect: (TrackSearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(initialQuery) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            containerColor = NightSession.Background,
            topBar = {
                TopAppBar(
                    title = { Text("Link on $trackerName", fontWeight = FontWeight.Bold, color = NightSession.Text) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = NightSession.Background),
                )
            },
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = NightSession.Chip,
                            unfocusedContainerColor = NightSession.Chip,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = NightSession.Text,
                            unfocusedTextColor = NightSession.Text,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onSearch(query) }) {
                        Text("Search", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }

                // Alt names from MangaUpdates -- a manga can be listed under a
                // different localized/translated title on MAL/Kitsu than on its
                // source site, so a plain title search alone can come up empty.
                if (altTitleSuggestions.isNotEmpty()) {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        items(altTitleSuggestions) { suggestion ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100))
                                    .background(NightSession.Chip)
                                    .border(1.dp, NightSession.Hairline, RoundedCornerShape(100))
                                    .clickable { query = suggestion; onSearch(suggestion) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(suggestion, fontSize = 10.5.sp, color = NightSession.TextDim)
                            }
                        }
                    }
                }

                when {
                    searching -> Box(Modifier.fillMaxWidth().padding(top = 24.dp), Alignment.TopCenter) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    error != null -> Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                    results != null && results.isEmpty() -> Text("No results.", color = NightSession.TextDim, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                    results != null -> LazyColumn {
                        items(results) { result ->
                            TrackSearchResultRow(result = result, onClick = { onSelect(result) })
                            HorizontalDivider(color = NightSession.Hairline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackSearchResultRow(result: TrackSearchResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = result.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(44.dp)
                .height(62.dp)
                .clip(RoundedCornerShape(NightSession.RadiusSm))
                .background(NightSession.Cover),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(result.title, color = NightSession.Text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            if (result.totalChapters > 0) {
                Text("${formatChapterNumber(result.totalChapters)} chapters", color = NightSession.TextDim, fontSize = 10.5.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

private val TRACK_STATUS_LABELS = listOf(
    "reading" to "Reading",
    "completed" to "Completed",
    "on_hold" to "On hold",
    "dropped" to "Dropped",
    "plan_to_read" to "Plan to read",
)

// Standard tracker edit dialog: status, chapter progress, and a 0-10 score
// all editable in one place, distinct from the lightweight linking flow above.
@Composable
private fun TrackerEditDialog(
    trackerName: String,
    track: Track,
    onDismiss: () -> Unit,
    onSave: (chaptersRead: Double, status: String, score: Double) -> Unit,
    onUnlink: () -> Unit,
) {
    var chaptersText by remember(track.id) { mutableStateOf(formatChapterNumber(track.lastChapterRead)) }
    var status by remember(track.id) { mutableStateOf(track.status) }
    var score by remember(track.id) { mutableStateOf(track.score.toInt()) }
    var statusMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NightSession.Chip,
        title = { Text("$trackerName link", color = NightSession.Text) },
        text = {
            Column {
                Text("Status", fontSize = 10.5.sp, color = NightSession.TextDim)
                Box(modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)) {
                    TextButton(onClick = { statusMenuExpanded = true }) {
                        Text(
                            TRACK_STATUS_LABELS.firstOrNull { it.first == status }?.second ?: status,
                            color = NightSession.Text,
                            fontSize = 12.sp,
                        )
                    }
                    androidx.compose.material3.DropdownMenu(expanded = statusMenuExpanded, onDismissRequest = { statusMenuExpanded = false }) {
                        TRACK_STATUS_LABELS.forEach { (value, label) ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { status = value; statusMenuExpanded = false },
                            )
                        }
                    }
                }

                Text("Chapters read" + if (track.totalChapters > 0) " (of ${formatChapterNumber(track.totalChapters)})" else "", fontSize = 10.5.sp, color = NightSession.TextDim)
                TextField(
                    value = chaptersText,
                    onValueChange = { chaptersText = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = NightSession.Background,
                        unfocusedContainerColor = NightSession.Background,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = NightSession.Text,
                        unfocusedTextColor = NightSession.Text,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                )

                Text("Score", fontSize = 10.5.sp, color = NightSession.TextDim)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    IconButton(onClick = { if (score > 0) score-- }) {
                        Text("-", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(if (score == 0) "None" else score.toString(), color = NightSession.Text, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    IconButton(onClick = { if (score < 10) score++ }) {
                        Text("+", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(onClick = onUnlink, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Unlink", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(chaptersText.toDoubleOrNull() ?: track.lastChapterRead, status, score.toDouble()) }) {
                Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = NightSession.TextDim) } },
    )
}
