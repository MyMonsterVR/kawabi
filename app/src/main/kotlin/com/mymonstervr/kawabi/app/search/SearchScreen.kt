package com.mymonstervr.kawabi.app.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import com.mymonstervr.kawabi.app.common.PageTitle
import com.mymonstervr.kawabi.app.common.SectionHeader
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.common.LoadingStateBox
import com.mymonstervr.kawabi.app.common.MangaGridCard
import com.mymonstervr.kawabi.app.common.NightChip
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import org.koin.androidx.compose.koinViewModel

@Composable
fun SearchScreen(
    onResultClick: (String) -> Unit,
    onBrowseClick: (String) -> Unit,
    onAnimeClick: (String) -> Unit = {},
    viewModel: SearchViewModel = koinViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val error by viewModel.error.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val scale = LocalKawabiScale.current
    val accent = MaterialTheme.colorScheme.primary
    var focused by remember { mutableStateOf(false) }
    val highlighted = focused || query.isNotEmpty()
    val fieldShape = RoundedCornerShape(16.dp)

    Scaffold(containerColor = NightSession.Background) { padding ->
        com.mymonstervr.kawabi.app.common.ResponsiveContainer(modifier = Modifier.padding(padding)) {
        Column(modifier = Modifier.fillMaxSize().background(NightSession.Background)) {
            PageTitle("Search", modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp))
            com.mymonstervr.kawabi.app.common.SegmentedTabs(
                options = listOf("Manga", "Anime"),
                selectedIndex = 0,
                onSelect = { if (it == 1) onAnimeClick(query) },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(52.dp * scale.spacing)
                    .drawBehind {
                        if (highlighted) {
                            val inset = 2.dp.toPx()
                            drawRoundRect(
                                color = accent.copy(alpha = 0.16f),
                                topLeft = Offset(-inset, -inset),
                                size = Size(size.width + inset * 2, size.height + inset * 2),
                                cornerRadius = CornerRadius(16.dp.toPx() + inset),
                                style = Stroke(width = inset * 2),
                            )
                        }
                    }
                    .clip(fieldShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, if (highlighted) accent else Color.White.copy(alpha = 0.07f), fieldShape)
                    .padding(horizontal = 16.dp),
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = if (highlighted) accent else NightSession.TextDim,
                    modifier = Modifier.size(20.dp),
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("Search titles", color = NightSession.TextDim, fontSize = 16.sp * scale.font)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(color = NightSession.Text, fontSize = 16.sp * scale.font),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable { viewModel.onQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = NightSession.Text, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (sources.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
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
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = if (results.size == 1) "1 result" else "${results.size} results")
                    }
                    items(results, key = { it.url }) { result ->
                        MangaGridCard(result = result, onClick = { onResultClick(result.url) })
                    }
                }
            }
        }
        }
    }
}
