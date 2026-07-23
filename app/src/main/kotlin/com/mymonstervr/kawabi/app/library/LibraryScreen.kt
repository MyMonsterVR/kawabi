package com.mymonstervr.kawabi.app.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.data.network.resolveCoverUrl
import com.mymonstervr.kawabi.domain.model.Manga
import com.mymonstervr.kawabi.domain.model.MangaWithUnreadCount
import org.koin.androidx.compose.koinViewModel

// Mirrors the standard LibrarySortMode.Type shape -- only a few have real backing data right
// now (no last-read timestamp / chapter-fetch-date tracking yet), the rest are listed
// per the design spec but fall back to insertion order until that data exists.
private enum class LibrarySort(val label: String) {
    ALPHABETICAL("Alphabetical"),
    LAST_READ("Last read"),
    LAST_UPDATED("Last updated"),
    UNREAD_COUNT("Unread count"),
    TOTAL_CHAPTERS("Total chapters"),
    LATEST_CHAPTER("Latest chapter"),
    CHAPTER_FETCH_DATE("Chapter fetch date"),
    DATE_ADDED("Date added"),
    TRACKER_SCORE("Tracker score"),
    RANDOM("Random"),
}

// "On hold" has no real backing data yet -- it needs actual user-managed Categories
// (categories/mangas_categories tables already exist but there's no assignment UI, see
// PLAN.md "phase 2/3"), unlike Reading/Completed which are just derived from unread
// count. Selecting it is a visible no-op (shows everything) until that's built, rather
// than a heuristic guess that could be wrong.
private enum class CategoryFilter(val label: String) {
    ALL("All"), READING("Reading"), COMPLETED("Completed"), ON_HOLD("On hold")
}

private fun filterByCategory(favorites: List<MangaWithUnreadCount>, filter: CategoryFilter): List<MangaWithUnreadCount> = when (filter) {
    CategoryFilter.ALL -> favorites
    CategoryFilter.READING -> favorites.filter { it.unreadCount > 0 }
    CategoryFilter.COMPLETED -> favorites.filter { it.unreadCount == 0 }
    CategoryFilter.ON_HOLD -> favorites
}

// Port of kawabi-web's dedupeByManga (src/app/(shell)/page.tsx) second pass: a manga
// migrated from one source to another in the old reader app (e.g. MangaFire -> AsuraScans) ends up
// as two separate native tracker entries with different source_urls -- no shared key to
// merge on server- or DB-side, since kawabi_app's mangas table is keyed by (source, url)
// by design and these are genuinely different rows. This is a display-only merge, same as
// kawabi-web: group by normalized title, keep the row with more total_chapters (the more
// complete/current source), combine unread counts by taking the min (favors whichever
// side has more progress).
private fun normalizeTitle(title: String): String =
    title.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .replace(Regex("^(the|an|a)-"), "")

private fun dedupeByManga(favorites: List<MangaWithUnreadCount>): List<MangaWithUnreadCount> =
    favorites.groupBy { normalizeTitle(it.manga.title).ifBlank { it.manga.url } }
        .map { (_, group) ->
            group.reduce { primary, other ->
                val keepPrimary = primary.manga.totalChapters >= other.manga.totalChapters
                val base = if (keepPrimary) primary else other
                val discarded = if (keepPrimary) other else primary
                base.copy(unreadCount = minOf(primary.unreadCount, other.unreadCount))
                    .let { merged ->
                        if (merged.manga.thumbnailUrl == null && discarded.manga.thumbnailUrl != null) {
                            merged.copy(manga = merged.manga.copy(thumbnailUrl = discarded.manga.thumbnailUrl))
                        } else {
                            merged
                        }
                    }
                    .let { merged ->
                        merged.copy(manga = merged.manga.copy(lastReadAt = maxOf(primary.manga.lastReadAt, other.manga.lastReadAt)))
                    }
            }
        }

private fun sortFavorites(favorites: List<MangaWithUnreadCount>, sort: LibrarySort): List<MangaWithUnreadCount> = when (sort) {
    LibrarySort.ALPHABETICAL -> favorites.sortedBy { it.manga.title.lowercase() }
    LibrarySort.LAST_READ -> favorites.sortedByDescending { it.manga.lastReadAt }
    LibrarySort.UNREAD_COUNT -> favorites.sortedByDescending { it.unreadCount }
    LibrarySort.TOTAL_CHAPTERS -> favorites.sortedByDescending { it.manga.totalChapters }
    LibrarySort.DATE_ADDED -> favorites.sortedByDescending { it.manga.dateAdded }
    LibrarySort.RANDOM -> favorites.shuffled()
    // Last updated, latest chapter, chapter fetch date, tracker score: no backing data
    // yet -- keep DB order rather than pretend to sort by something we don't track.
    else -> favorites
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onMangaClick: (String) -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val favorites by viewModel.favorites.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val cardSize by viewModel.cardSize.collectAsState()
    val pullState = rememberPullToRefreshState()

    var sort by remember { mutableStateOf(LibrarySort.LAST_READ) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(CategoryFilter.ALL) }

    val sorted = remember(favorites, sort, selectedCategory) {
        filterByCategory(sortFavorites(dedupeByManga(favorites), sort), selectedCategory)
    }

    Scaffold(
        containerColor = NightSession.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Kawabi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp * LocalKawabiScale.current.font,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort", tint = NightSession.TextDim)
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            LibrarySort.entries.forEach { option ->
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
                CategoryFilter.entries.forEach { filter ->
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
                    EmptyLibrary(modifier = Modifier.fillMaxSize())
                } else {
                    LazyVerticalGrid(
                        // Adaptive, not Fixed(3) -- 3 columns on a phone-width screen is
                        // the same math as before, but this scales up to 6-8+ on a
                        // tablet instead of stretching 3 giant covers across the width.
                        // minSize itself is user-adjustable (Settings -> Library card size).
                        columns = GridCells.Adaptive(minSize = cardSize.minWidthDp.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(sorted, key = { it.manga.id }) { entry ->
                            MangaCard(entry, onClick = { onMangaClick(entry.manga.url) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NightChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scale = LocalKawabiScale.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .background(if (selected) MaterialTheme.colorScheme.primary else NightSession.Chip)
            .border(1.dp, if (selected) androidx.compose.ui.graphics.Color.Transparent else NightSession.Hairline, RoundedCornerShape(100))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp * scale.spacing, vertical = 6.dp * scale.spacing),
    ) {
        Text(
            text = label,
            fontSize = 11.sp * scale.font,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) NightSession.OnAccent else NightSession.TextDim,
        )
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = "Your library is empty", style = MaterialTheme.typography.titleMedium, color = NightSession.Text)
    }
}

@Composable
private fun MangaCard(entry: MangaWithUnreadCount, onClick: () -> Unit) {
    val manga: Manga = entry.manga
    val scale = LocalKawabiScale.current
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = resolveCoverUrl(manga.thumbnailUrl),
                contentDescription = manga.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(NightSession.RadiusMd))
                    .border(1.dp, NightSession.Hairline, RoundedCornerShape(NightSession.RadiusMd))
                    .background(NightSession.Cover),
            )
            if (entry.unreadCount > 0) {
                Text(
                    text = "${entry.unreadCount}",
                    fontSize = 9.sp * scale.font,
                    fontWeight = FontWeight.Bold,
                    color = NightSession.OnAccent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp * scale.spacing)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(horizontal = 6.dp * scale.spacing, vertical = 2.dp * scale.spacing),
                )
            }
        }
        Spacer(modifier = Modifier.height(5.dp * scale.spacing))
        Text(
            text = manga.title,
            fontSize = 10.5.sp * scale.font,
            fontWeight = FontWeight.SemiBold,
            color = NightSession.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        entry.lastReadChapterNumber?.let { lastRead ->
            Text(
                text = "Ch. ${formatChapterNumber(lastRead)}",
                fontSize = 9.sp * scale.font,
                color = NightSession.TextDim,
            )
        }
    }
}

private fun formatChapterNumber(number: Double): String =
    if (number == number.toLong().toDouble()) number.toLong().toString() else number.toString()
