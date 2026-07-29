package com.mymonstervr.kawabi.app.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.common.MangaGridCard
import com.mymonstervr.kawabi.app.common.NightChip
import com.mymonstervr.kawabi.app.common.ResponsiveContainer
import com.mymonstervr.kawabi.app.theme.NightSession
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    sourceKey: String,
    onBack: () -> Unit,
    onResultClick: (String) -> Unit,
    viewModel: BrowseViewModel = koinViewModel(),
) {
    LaunchedEffect(sourceKey) { viewModel.load(sourceKey) }

    val sourceName by viewModel.sourceName.collectAsState()
    val supportsLatest by viewModel.supportsLatest.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val items by viewModel.items.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val error by viewModel.error.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()

    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState, items) {
        snapshotFlow {
            val layout = gridState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull() ?: return@snapshotFlow false
            lastVisible.index >= layout.totalItemsCount - gridColumns * 2
        }.collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
    }

    Scaffold(
        containerColor = NightSession.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        sourceName.ifEmpty { "Browse" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = NightSession.Text,
                    )
                },
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    NightChip(label = BrowseSort.POPULAR.label, selected = sort == BrowseSort.POPULAR, onClick = { viewModel.setSort(BrowseSort.POPULAR) })
                    if (supportsLatest) {
                        NightChip(label = BrowseSort.LATEST.label, selected = sort == BrowseSort.LATEST, onClick = { viewModel.setSort(BrowseSort.LATEST) })
                    }
                }

                when {
                    isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                    items.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No results", color = NightSession.TextDim, fontSize = 11.5.sp)
                    }
                    else -> LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(gridColumns),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items, key = { it.url }) { result ->
                            MangaGridCard(result = result, onClick = { onResultClick(result.url) })
                        }
                        if (isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
