package com.mymonstervr.kawabi.app.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.data.track.dto.TrackSearchResult
import com.mymonstervr.kawabi.domain.model.MediaType
import com.mymonstervr.kawabi.domain.model.TrackStatus
import com.mymonstervr.kawabi.domain.model.formatChapterNumber

/**
 * Tracker linking/editing UI, shared by the manga and anime detail screens. The only
 * difference between the two axes is wording ("chapters read" vs "episodes watched") and
 * which canonical statuses are offered, both driven by [MediaType] -- the flows themselves
 * (search, bind, edit, unlink) are identical.
 */

/** One tracker's current link state, media-agnostic -- Track / AnimeTrack both map onto it. */
data class TrackerSheetLink(
    val progress: Double,
    val total: Double,
    val score: Double,
    val status: String,
)

data class TrackerSheetRow(
    val trackerId: String,
    val trackerName: String,
    val linked: TrackerSheetLink?,
)

private fun statusLabels(mediaType: MediaType): List<Pair<String, String>> =
    if (mediaType == MediaType.ANIME) {
        listOf(
            TrackStatus.WATCHING to "Watching",
            TrackStatus.COMPLETED to "Completed",
            TrackStatus.ON_HOLD to "On hold",
            TrackStatus.DROPPED to "Dropped",
            TrackStatus.PLAN_TO_WATCH to "Plan to watch",
        )
    } else {
        listOf(
            TrackStatus.READING to "Reading",
            TrackStatus.COMPLETED to "Completed",
            TrackStatus.ON_HOLD to "On hold",
            TrackStatus.DROPPED to "Dropped",
            TrackStatus.PLAN_TO_READ to "Plan to read",
        )
    }

private fun unitPlural(mediaType: MediaType) = if (mediaType == MediaType.ANIME) "episodes" else "chapters"

private fun progressLabel(mediaType: MediaType) =
    if (mediaType == MediaType.ANIME) "Episodes watched" else "Chapters read"

@Composable
fun TrackerLinkSheetContent(
    rows: List<TrackerSheetRow>,
    onOpenSearch: (trackerId: String) -> Unit,
    onOpenEdit: (trackerId: String) -> Unit,
    onGoToSettings: () -> Unit,
) {
    val scale = LocalKawabiScale.current
    Column(modifier = Modifier.padding(horizontal = 16.dp * scale.spacing).padding(bottom = 24.dp * scale.spacing)) {
        Text("Tracker links", fontSize = 14.sp * scale.font, fontWeight = FontWeight.Bold, color = NightSession.Text, modifier = Modifier.padding(bottom = 8.dp * scale.spacing))
        if (rows.isEmpty()) {
            Text("Not connected to any tracker yet.", fontSize = 11.5.sp * scale.font, color = NightSession.TextDim)
            Spacer(Modifier.height(8.dp * scale.spacing))
            TextButton(onClick = onGoToSettings) {
                Text("Go to Settings -> Tracking services", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp * scale.font)
            }
        } else {
            rows.forEachIndexed { index, row ->
                TrackerLinkRowContent(
                    row = row,
                    onClick = { if (row.linked != null) onOpenEdit(row.trackerId) else onOpenSearch(row.trackerId) },
                )
                if (index != rows.lastIndex) {
                    HorizontalDivider(color = NightSession.Hairline, modifier = Modifier.padding(vertical = 8.dp * scale.spacing))
                }
            }
        }
    }
}

@Composable
private fun TrackerLinkRowContent(row: TrackerSheetRow, onClick: () -> Unit) {
    val scale = LocalKawabiScale.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp * scale.spacing),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.trackerName, fontSize = 12.sp * scale.font, fontWeight = FontWeight.SemiBold, color = NightSession.Text)
            val linked = row.linked
            Text(
                text = when {
                    linked == null -> "Not linked"
                    else -> buildString {
                        append(formatChapterNumber(linked.progress))
                        if (linked.total > 0) append(" / ${formatChapterNumber(linked.total)}")
                        if (linked.score > 0) append(" ★ ${formatChapterNumber(linked.score)}")
                    }
                },
                fontSize = 10.5.sp * scale.font,
                color = NightSession.TextDim,
            )
        }
        if (row.linked != null) {
            Icon(Icons.Outlined.Edit, contentDescription = "Edit ${row.trackerName} link", tint = NightSession.TextDim, modifier = Modifier.size(16.dp * scale.spacing))
        } else {
            Icon(Icons.Filled.Add, contentDescription = "Link on ${row.trackerName}", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp * scale.spacing))
        }
    }
}

// Full-screen so a cover-thumbnail result list has room to actually be useful --
// a same-title-different-language/region title is otherwise indistinguishable
// from text alone (owner feedback after testing the inline version).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerSearchDialog(
    trackerName: String,
    initialQuery: String,
    searching: Boolean,
    results: List<TrackSearchResult>?,
    error: String?,
    altTitleSuggestions: List<String>,
    mediaType: MediaType,
    onSearch: (String) -> Unit,
    onSelect: (TrackSearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    val scale = LocalKawabiScale.current
    var query by remember { mutableStateOf(initialQuery) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp * scale.spacing)) {
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
                        Text("Search", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp * scale.font)
                    }
                }

                // Alt names from MangaUpdates -- a title can be listed differently on
                // MAL/Kitsu than on its source site, so a plain title search alone can
                // come up empty.
                if (altTitleSuggestions.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp * scale.spacing),
                        horizontalArrangement = Arrangement.spacedBy(6.dp * scale.spacing),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp * scale.spacing),
                    ) {
                        items(altTitleSuggestions) { suggestion ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100))
                                    .background(NightSession.Chip)
                                    .border(1.dp, NightSession.Hairline, RoundedCornerShape(100))
                                    .clickable { query = suggestion; onSearch(suggestion) }
                                    .padding(horizontal = 10.dp * scale.spacing, vertical = 5.dp * scale.spacing),
                            ) {
                                Text(suggestion, fontSize = 10.5.sp * scale.font, color = NightSession.TextDim)
                            }
                        }
                    }
                }

                when {
                    searching -> Box(Modifier.fillMaxWidth().padding(top = 24.dp * scale.spacing), Alignment.TopCenter) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    error != null -> Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp * scale.font, modifier = Modifier.padding(16.dp * scale.spacing))
                    results != null && results.isEmpty() -> Text("No results.", color = NightSession.TextDim, fontSize = 12.sp * scale.font, modifier = Modifier.padding(16.dp * scale.spacing))
                    results != null -> LazyColumn {
                        items(results) { result ->
                            TrackSearchResultRow(result = result, unitPlural = unitPlural(mediaType), onClick = { onSelect(result) })
                            HorizontalDivider(color = NightSession.Hairline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackSearchResultRow(result: TrackSearchResult, unitPlural: String, onClick: () -> Unit) {
    val scale = LocalKawabiScale.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp * scale.spacing, vertical = 8.dp * scale.spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = result.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(44.dp * scale.spacing)
                .height(62.dp * scale.spacing)
                .clip(RoundedCornerShape(NightSession.RadiusSm))
                .background(NightSession.Cover),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp * scale.spacing)) {
            Text(result.title, color = NightSession.Text, fontSize = 12.5.sp * scale.font, fontWeight = FontWeight.SemiBold)
            if (result.totalChapters > 0) {
                Text("${formatChapterNumber(result.totalChapters)} $unitPlural", color = NightSession.TextDim, fontSize = 10.5.sp * scale.font, modifier = Modifier.padding(top = 2.dp * scale.spacing))
            }
        }
    }
}

/**
 * Standard tracker edit dialog: status, progress, and a 0-10 score all editable in one
 * place, distinct from the lightweight linking flow above. Explicitly allowed to lower
 * the progress count -- that's the documented exception to the monotonic-max rule.
 */
@Composable
fun TrackerEditDialog(
    trackerName: String,
    link: TrackerSheetLink,
    mediaType: MediaType,
    onDismiss: () -> Unit,
    onSave: (progress: Double, status: String, score: Double) -> Unit,
    onUnlink: () -> Unit,
) {
    val scale = LocalKawabiScale.current
    val labels = statusLabels(mediaType)
    var progressText by remember(link) { mutableStateOf(formatChapterNumber(link.progress)) }
    var status by remember(link) { mutableStateOf(link.status) }
    var score by remember(link) { mutableStateOf(link.score.toInt()) }
    var statusMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NightSession.Chip,
        title = { Text("$trackerName link", color = NightSession.Text) },
        text = {
            Column {
                Text("Status", fontSize = 10.5.sp * scale.font, color = NightSession.TextDim)
                Box(modifier = Modifier.padding(top = 4.dp * scale.spacing, bottom = 12.dp * scale.spacing)) {
                    TextButton(onClick = { statusMenuExpanded = true }) {
                        Text(
                            labels.firstOrNull { it.first == status }?.second ?: status,
                            color = NightSession.Text,
                            fontSize = 12.sp * scale.font,
                        )
                    }
                    DropdownMenu(expanded = statusMenuExpanded, onDismissRequest = { statusMenuExpanded = false }) {
                        labels.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { status = value; statusMenuExpanded = false },
                            )
                        }
                    }
                }

                Text(
                    text = progressLabel(mediaType) + if (link.total > 0) " (of ${formatChapterNumber(link.total)})" else "",
                    fontSize = 10.5.sp * scale.font,
                    color = NightSession.TextDim,
                )
                TextField(
                    value = progressText,
                    onValueChange = { progressText = it },
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
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp * scale.spacing, bottom = 12.dp * scale.spacing),
                )

                Text("Score", fontSize = 10.5.sp * scale.font, color = NightSession.TextDim)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp * scale.spacing)) {
                    IconButton(onClick = { if (score > 0) score-- }) {
                        Text("-", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp * scale.font, fontWeight = FontWeight.Bold)
                    }
                    Text(if (score == 0) "None" else score.toString(), color = NightSession.Text, fontSize = 13.sp * scale.font, modifier = Modifier.padding(horizontal = 8.dp * scale.spacing))
                    IconButton(onClick = { if (score < 10) score++ }) {
                        Text("+", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp * scale.font, fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(onClick = onUnlink, modifier = Modifier.padding(top = 12.dp * scale.spacing)) {
                    Text("Unlink", color = MaterialTheme.colorScheme.error, fontSize = 11.sp * scale.font)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(progressText.toDoubleOrNull() ?: link.progress, status, score.toDouble()) }) {
                Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = NightSession.TextDim) } },
    )
}
