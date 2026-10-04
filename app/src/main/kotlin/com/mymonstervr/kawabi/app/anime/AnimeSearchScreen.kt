package com.mymonstervr.kawabi.app.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.common.LoadingStateBox
import com.mymonstervr.kawabi.app.common.MediaGridCard
import com.mymonstervr.kawabi.app.common.NightChip
import com.mymonstervr.kawabi.app.common.PageTitle
import com.mymonstervr.kawabi.app.common.ResponsiveContainer
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import org.koin.androidx.compose.koinViewModel

@Composable
fun AnimeSearchScreen(
    initialQuery: String = "",
    onResultClick: (String) -> Unit,
    onBrowseClick: (String) -> Unit,
    onMangaClick: () -> Unit = {},
    viewModel: AnimeSearchViewModel = koinViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val error by viewModel.error.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val scale = LocalKawabiScale.current

    LaunchedEffect(initialQuery) { viewModel.prefillAndSearch(initialQuery) }

    Scaffold(
        containerColor = NightSession.Background,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(NightSession.Background)) {
                PageTitle(
                    text = "Search",
                    modifier = Modifier
                        .padding(start = 16.dp * scale.spacing, end = 16.dp * scale.spacing, top = 18.dp * scale.spacing, bottom = 14.dp * scale.spacing),
                )
                com.mymonstervr.kawabi.app.common.SegmentedTabs(
                    options = listOf("Manga", "Anime"),
                    selectedIndex = 1,
                    onSelect = { if (it == 0) onMangaClick() },
                    modifier = Modifier.padding(horizontal = 16.dp * scale.spacing).padding(bottom = 8.dp).fillMaxWidth(),
                )
            }
        },
    ) { padding ->
        ResponsiveContainer(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().background(NightSession.Background)) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp * scale.spacing, vertical = 12.dp * scale.spacing)) {
                    TextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("Search anime", color = NightSession.TextDim, fontSize = 12.5.sp * scale.font) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = NightSession.TextDim,
                                modifier = Modifier.size(16.dp * scale.spacing),
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = NightSession.Chip,
                            unfocusedContainerColor = NightSession.Chip,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = NightSession.Text,
                            unfocusedTextColor = NightSession.Text,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp)),
                    )
                }

                if (sources.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp * scale.spacing),
                        horizontalArrangement = Arrangement.spacedBy(6.dp * scale.spacing),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp * scale.spacing),
                    ) {
                        items(sources, key = { it.key }) { source ->
                            NightChip(label = source.name, onClick = { onBrowseClick(source.key) })
                        }
                    }
                }

                LoadingStateBox(
                    isLoading = isSearching,
                    error = error,
                    isEmpty = results.isEmpty(),
                    emptyMessage = "No results yet",
                    emptyFontSize = 11.5.sp * scale.font,
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        contentPadding = PaddingValues(16.dp * scale.spacing),
                        horizontalArrangement = Arrangement.spacedBy(10.dp * scale.spacing),
                        verticalArrangement = Arrangement.spacedBy(18.dp * scale.spacing),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(results, key = { it.key }) { result ->
                            MediaGridCard(
                                title = result.displayTitle ?: result.title,
                                coverUrl = result.cover_url,
                                subtitle = result.source_name,
                                onClick = { viewModel.openKeyFor(result, onResultClick) },
                                source = result.source,
                            )
                        }
                    }
                }
            }
        }
    }
}
