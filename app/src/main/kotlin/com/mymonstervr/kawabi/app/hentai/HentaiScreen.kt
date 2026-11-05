package com.mymonstervr.kawabi.app.hentai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.anime.AnimeSearchScreen
import com.mymonstervr.kawabi.app.common.MediaGridCard
import com.mymonstervr.kawabi.app.common.ResponsiveContainer
import com.mymonstervr.kawabi.app.common.SegmentedTabs
import com.mymonstervr.kawabi.app.search.SearchScreen
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.domain.model.AnimeLibraryEntry
import com.mymonstervr.kawabi.domain.model.MangaWithUnreadCount
import com.mymonstervr.kawabi.domain.model.formatChapterNumber
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HentaiScreen(
    onBack: () -> Unit,
    onMangaClick: (String) -> Unit,
    onAnimeClick: (String) -> Unit,
    onMangaBrowse: (String) -> Unit,
    onAnimeBrowse: (String) -> Unit,
    viewModel: HentaiViewModel = koinViewModel(),
) {
    val enabled by viewModel.adultEnabled.collectAsState()
    LaunchedEffect(enabled) { if (enabled == false) onBack() }

    val mangaLibrary by viewModel.mangaLibrary.collectAsState()
    val animeLibrary by viewModel.animeLibrary.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    var mediaTab by rememberSaveable { mutableIntStateOf(0) }
    var modeTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = NightSession.Background,
        topBar = {
            TopAppBar(
                title = { Text("Hentai", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NightSession.Text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NightSession.Text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NightSession.Background),
            )
        },
    ) { padding ->
        ResponsiveContainer(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().background(NightSession.Background)) {
                SegmentedTabs(
                    options = listOf("Manga", "Anime"),
                    selectedIndex = mediaTab,
                    onSelect = { mediaTab = it },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp).fillMaxWidth(),
                )
                SegmentedTabs(
                    options = listOf("Library", "Search"),
                    selectedIndex = modeTab,
                    onSelect = { modeTab = it },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 8.dp).fillMaxWidth(),
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        mediaTab == 0 && modeTab == 0 -> MangaLibraryGrid(mangaLibrary, gridColumns, onMangaClick)
                        mediaTab == 1 && modeTab == 0 -> AnimeLibraryGrid(animeLibrary, gridColumns, onAnimeClick)
                        mediaTab == 0 -> SearchScreen(
                            onResultClick = onMangaClick,
                            onBrowseClick = onMangaBrowse,
                            adult = true,
                        )
                        else -> AnimeSearchScreen(
                            onResultClick = onAnimeClick,
                            onBrowseClick = onAnimeBrowse,
                            adult = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MangaLibraryGrid(entries: List<MangaWithUnreadCount>, columns: Int, onClick: (String) -> Unit) {
    if (entries.isEmpty()) {
        EmptyHint()
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(entries, key = { it.manga.id }) { entry ->
            MediaGridCard(
                title = entry.manga.title,
                coverUrl = entry.manga.thumbnailUrl,
                subtitle = null,
                onClick = { onClick(entry.manga.url) },
                badge = entry.unreadCount.takeIf { it > 0 }?.toString(),
                blurCover = true,
            )
        }
    }
}

@Composable
private fun AnimeLibraryGrid(entries: List<AnimeLibraryEntry>, columns: Int, onClick: (String) -> Unit) {
    if (entries.isEmpty()) {
        EmptyHint()
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(entries, key = { it.anime.id }) { entry ->
            MediaGridCard(
                title = entry.anime.title,
                coverUrl = entry.anime.thumbnailUrl,
                subtitle = entry.lastWatchedEpisodeNumber?.let { "Ep. ${formatChapterNumber(it)}" },
                onClick = { onClick(entry.anime.key) },
                source = entry.anime.source,
                badge = entry.unwatchedCount.takeIf { it > 0 }?.let { "$it new" },
                blurCover = true,
            )
        }
    }
}

@Composable
private fun EmptyHint() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Nothing here yet", color = NightSession.TextDim, fontSize = 13.sp)
    }
}
