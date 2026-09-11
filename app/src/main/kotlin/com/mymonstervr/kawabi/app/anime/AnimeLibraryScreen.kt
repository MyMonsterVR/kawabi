package com.mymonstervr.kawabi.app.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.common.MediaGridCard
import com.mymonstervr.kawabi.app.common.NightChip
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.domain.model.AnimeWithUnwatchedCount
import com.mymonstervr.kawabi.domain.model.formatChapterNumber
import org.koin.androidx.compose.koinViewModel

// Same shape as the manga library's sort menu, minus the options that have no anime-side
// backing data at all (no per-episode fetch dates, no tracker score column).
private enum class AnimeLibrarySort(val label: String) {
    LAST_WATCHED("Last watched"),
    ALPHABETICAL("Alphabetical"),
    UNWATCHED_COUNT("Unwatched count"),
    TOTAL_EPISODES("Total episodes"),
    DATE_ADDED("Date added"),
    RANDOM("Random"),
}

private enum class AnimeCategoryFilter(val label: String) {
    ALL("All"), WATCHING("Watching"), COMPLETED("Completed")
}

private const val CONTINUE_RAIL_MAX = 10

private fun sortFavorites(favorites: List<AnimeWithUnwatchedCount>, sort: AnimeLibrarySort): List<AnimeWithUnwatchedCount> =
    when (sort) {
        AnimeLibrarySort.LAST_WATCHED -> favorites.sortedByDescending { it.anime.lastWatchedAt }
        AnimeLibrarySort.ALPHABETICAL -> favorites.sortedBy { it.anime.title.lowercase() }
        AnimeLibrarySort.UNWATCHED_COUNT -> favorites.sortedByDescending { it.unwatchedCount }
        AnimeLibrarySort.TOTAL_EPISODES -> favorites.sortedByDescending { it.anime.totalEpisodes }
        AnimeLibrarySort.DATE_ADDED -> favorites.sortedByDescending { it.anime.dateAdded }
        AnimeLibrarySort.RANDOM -> favorites.shuffled()
    }

private fun filterByCategory(
    favorites: List<AnimeWithUnwatchedCount>,
    filter: AnimeCategoryFilter,
): List<AnimeWithUnwatchedCount> = when (filter) {
    AnimeCategoryFilter.ALL -> favorites
    AnimeCategoryFilter.WATCHING -> favorites.filter { it.unwatchedCount > 0 }
    AnimeCategoryFilter.COMPLETED -> favorites.filter { it.unwatchedCount == 0 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeLibraryScreen(
    onAnimeClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    viewModel: AnimeLibraryViewModel = koinViewModel(),
) {
    val favorites by viewModel.favorites.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val pullState = rememberPullToRefreshState()
    val scale = LocalKawabiScale.current

    var sort by remember { mutableStateOf(AnimeLibrarySort.LAST_WATCHED) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(AnimeCategoryFilter.ALL) }

    val sorted = remember(favorites, sort, selectedCategory) {
        filterByCategory(sortFavorites(favorites, sort), selectedCategory)
    }
    val continueRail = remember(favorites) {
        favorites.filter { it.anime.lastWatchedAt > 0 && it.unwatchedCount > 0 }
            .sortedByDescending { it.anime.lastWatchedAt }
            .take(CONTINUE_RAIL_MAX)
    }

    Scaffold(
        containerColor = NightSession.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Anime",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp * scale.font,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search anime", tint = NightSession.TextDim)
                    }
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort", tint = NightSession.TextDim)
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            AnimeLibrarySort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = { sort = option; sortMenuOpen = false },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NightSession.Background),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(NightSession.Background)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AnimeCategoryFilter.entries.forEach { filter ->
                    NightChip(label = filter.label, selected = selectedCategory == filter, onClick = { selectedCategory = filter })
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refreshAll,
                state = pullState,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (sorted.isEmpty()) {
                    EmptyAnimeLibrary(modifier = Modifier.fillMaxSize())
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (continueRail.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                ContinueRail(entries = continueRail, onAnimeClick = onAnimeClick)
                            }
                        }
                        items(sorted, key = { it.anime.id }) { entry ->
                            AnimeCard(entry, onClick = { onAnimeClick(entry.anime.key) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueRail(entries: List<AnimeWithUnwatchedCount>, onAnimeClick: (String) -> Unit) {
    val scale = LocalKawabiScale.current
    Column(modifier = Modifier.padding(bottom = 6.dp * scale.spacing)) {
        Text(
            text = "Continue watching",
            fontSize = 12.sp * scale.font,
            fontWeight = FontWeight.Bold,
            color = NightSession.Text,
            modifier = Modifier.padding(bottom = 6.dp * scale.spacing),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp * scale.spacing)) {
            items(entries, key = { it.anime.id }) { entry ->
                Box(modifier = Modifier.width(92.dp * scale.spacing)) {
                    AnimeCard(entry, onClick = { onAnimeClick(entry.anime.key) })
                }
            }
        }
    }
}

@Composable
private fun EmptyAnimeLibrary(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = "No anime in your library yet", style = MaterialTheme.typography.titleMedium, color = NightSession.Text)
    }
}

@Composable
private fun AnimeCard(entry: AnimeWithUnwatchedCount, onClick: () -> Unit) {
    val anime = entry.anime
    MediaGridCard(
        title = anime.title,
        coverUrl = anime.thumbnailUrl,
        subtitle = entry.lastWatchedEpisodeNumber?.let { "Ep. ${formatChapterNumber(it)}" },
        onClick = onClick,
        source = anime.source,
        // Local unwatched rows == total_episodes - watched once the episode list has been
        // fetched; before that there are no rows at all, so no pill rather than a wrong one.
        badge = entry.unwatchedCount.takeIf { it > 0 }?.let { "$it new" },
    )
}
