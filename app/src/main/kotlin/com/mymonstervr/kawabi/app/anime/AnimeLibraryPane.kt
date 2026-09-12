package com.mymonstervr.kawabi.app.anime

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.common.MediaGridCard
import com.mymonstervr.kawabi.app.common.NightChip
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.domain.model.AnimeLibraryEntry
import com.mymonstervr.kawabi.domain.model.formatChapterNumber

@Composable
internal fun AnimeLibraryPane(
    entries: List<AnimeLibraryEntry>,
    gridColumns: Int,
    sort: AnimeLibrarySort,
    statusFilter: AnimeStatusFilter,
    query: String,
    onSortChange: (AnimeLibrarySort) -> Unit,
    onStatusFilterChange: (AnimeStatusFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onAnimeClick: (String) -> Unit,
) {
    val scale = LocalKawabiScale.current
    var sortMenuOpen by remember { mutableStateOf(false) }

    val counts = remember(entries) {
        AnimeStatusFilter.entries.associateWith { filter ->
            if (filter.status == null) entries.size else entries.count { it.status == filter.status }
        }
    }
    val shown = remember(entries, sort, statusFilter, query) {
        entries
            .filter { statusFilter.status == null || it.status == statusFilter.status }
            .filter { query.isBlank() || it.anime.title.contains(query.trim(), ignoreCase = true) }
            .let { filtered ->
                when (sort) {
                    AnimeLibrarySort.LAST_WATCHED -> filtered.sortedByDescending { it.anime.lastWatchedAt }
                    AnimeLibrarySort.TITLE -> filtered.sortedBy { it.anime.title.lowercase() }
                    AnimeLibrarySort.RECENTLY_ADDED -> filtered.sortedByDescending { it.anime.dateAdded }
                    AnimeLibrarySort.UNWATCHED_COUNT -> filtered.sortedByDescending { it.unwatchedCount }
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp * scale.spacing, vertical = 10.dp * scale.spacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp * scale.spacing),
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Filter library", color = NightSession.TextDim, fontSize = 12.5.sp * scale.font) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = NightSession.TextDim,
                        modifier = Modifier.size(16.dp * scale.spacing),
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(NightSession.RadiusMd),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = NightSession.Chip,
                    unfocusedContainerColor = NightSession.Chip,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = NightSession.Text,
                    unfocusedTextColor = NightSession.Text,
                ),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, NightSession.Hairline, RoundedCornerShape(NightSession.RadiusMd)),
            )
            Box {
                IconButton(onClick = { sortMenuOpen = true }) {
                    Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort", tint = NightSession.TextDim)
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    AnimeLibrarySort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { onSortChange(option); sortMenuOpen = false },
                        )
                    }
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp * scale.spacing),
            horizontalArrangement = Arrangement.spacedBy(6.dp * scale.spacing),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp * scale.spacing),
        ) {
            items(AnimeStatusFilter.entries, key = { it.name }) { filter ->
                NightChip(
                    label = "${filter.label} ${counts[filter] ?: 0}",
                    selected = statusFilter == filter,
                    onClick = { onStatusFilterChange(filter) },
                )
            }
        }

        if (shown.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyAnimeMessage(
                    title = if (entries.isEmpty()) "Your anime library is empty" else "Nothing matches",
                    hint = if (entries.isEmpty()) {
                        "Search for a show and add it to your library."
                    } else {
                        "Try another status or clear the filter."
                    },
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(16.dp * scale.spacing),
                horizontalArrangement = Arrangement.spacedBy(10.dp * scale.spacing),
                verticalArrangement = Arrangement.spacedBy(14.dp * scale.spacing),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.anime.id }) { entry ->
                    MediaGridCard(
                        title = entry.anime.title,
                        coverUrl = entry.anime.thumbnailUrl,
                        subtitle = entry.lastWatchedEpisodeNumber?.let { "Ep. ${formatChapterNumber(it)}" },
                        onClick = { onAnimeClick(entry.anime.key) },
                        source = entry.anime.source,
                        // Local unwatched rows only exist once the episode list has been
                        // fetched; before that there are no rows at all, so no pill rather
                        // than a wrong one.
                        badge = entry.unwatchedCount.takeIf { it > 0 }?.let { "$it new" },
                    )
                }
            }
        }
    }
}
