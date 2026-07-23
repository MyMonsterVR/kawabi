package com.mymonstervr.kawabi.app.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.data.network.dto.SearchResultDto
import com.mymonstervr.kawabi.data.network.resolveCoverUrl
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onResultClick: (String) -> Unit, viewModel: SearchViewModel = koinViewModel()) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val error by viewModel.error.collectAsState()
    val cardSize by viewModel.cardSize.collectAsState()

    Scaffold(
        containerColor = NightSession.Background,
        topBar = {
            TopAppBar(
                title = { Text("Search", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NightSession.Text) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NightSession.Background),
            )
        },
    ) { padding ->
        com.mymonstervr.kawabi.app.common.ResponsiveContainer(modifier = Modifier.padding(padding)) {
        Column(modifier = Modifier.fillMaxSize().background(NightSession.Background)) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                TextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("Search titles", color = NightSession.TextDim, fontSize = 12.5.sp) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = NightSession.TextDim, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(NightSession.RadiusMd),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = NightSession.Chip,
                        unfocusedContainerColor = NightSession.Chip,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedTextColor = NightSession.Text,
                        unfocusedTextColor = NightSession.Text,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NightSession.Hairline, RoundedCornerShape(NightSession.RadiusMd)),
                )
            }

            when {
                isSearching -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
                results.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No results yet", color = NightSession.TextDim, fontSize = 11.5.sp)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = cardSize.minWidthDp.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(results, key = { it.url }) { result ->
                        SearchResultCard(result = result, onClick = { onResultClick(result.url) })
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun SearchResultCard(result: SearchResultDto, onClick: () -> Unit) {
    val scale = LocalKawabiScale.current
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        AsyncImage(
            model = resolveCoverUrl(result.cover_url),
            contentDescription = result.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(NightSession.RadiusMd))
                .border(1.dp, NightSession.Hairline, RoundedCornerShape(NightSession.RadiusMd))
                .background(NightSession.Cover),
        )
        Spacer(modifier = Modifier.height(5.dp * scale.spacing))
        Text(
            text = result.title,
            fontSize = 10.5.sp * scale.font,
            fontWeight = FontWeight.SemiBold,
            color = NightSession.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = result.source_name,
            fontSize = 9.sp * scale.font,
            color = NightSession.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
