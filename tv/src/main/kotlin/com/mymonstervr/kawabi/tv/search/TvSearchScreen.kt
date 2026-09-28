package com.mymonstervr.kawabi.tv.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.mymonstervr.kawabi.data.network.resolveCoverUrl
import com.mymonstervr.kawabi.tv.theme.TvColors
import com.mymonstervr.kawabi.tv.theme.TvDimens
import com.mymonstervr.kawabi.tv.theme.TvFocus
import org.koin.androidx.compose.koinViewModel

@Composable
fun TvSearchScreen(
    onBack: () -> Unit,
    onOpenAnime: (String) -> Unit,
    viewModel: TvSearchViewModel = koinViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val error by viewModel.error.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val firstResultFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val shape = RoundedCornerShape(TvDimens.RadiusMd)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(horizontal = TvDimens.OverscanHorizontal, vertical = TvDimens.OverscanVertical),
    ) {
        Text("Search", color = TvColors.Text, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { androidx.compose.material3.Text("Search anime...") },
            singleLine = true,
            modifier = Modifier
                .width(560.dp)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && results.isNotEmpty()) {
                        firstResultFocusRequester.requestFocus()
                        true
                    } else {
                        false
                    }
                },
            colors = TextFieldDefaults.colors(
                focusedTextColor = TvColors.Text,
                unfocusedTextColor = TvColors.Text,
                focusedContainerColor = TvColors.Surface,
                unfocusedContainerColor = TvColors.Surface,
                focusedIndicatorColor = TvColors.Accent,
                unfocusedIndicatorColor = TvColors.Hairline,
                cursorColor = TvColors.Accent,
                focusedPlaceholderColor = TvColors.TextDim,
                unfocusedPlaceholderColor = TvColors.TextDim,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = {
                    viewModel.search()
                    keyboardController?.hide()
                },
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
            ),
        )
        Spacer(Modifier.height(28.dp))

        when {
            isSearching -> Text("Searching...", color = TvColors.TextDim, style = MaterialTheme.typography.bodyLarge)
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            results.isEmpty() && query.isNotBlank() -> Text("No results", color = TvColors.TextDim, style = MaterialTheme.typography.bodyLarge)
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = TvDimens.CardWidth),
            horizontalArrangement = Arrangement.spacedBy(TvDimens.CardSpacing),
            verticalArrangement = Arrangement.spacedBy(TvDimens.CardSpacing),
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(results, key = { _, it -> it.key }) { index, card ->
                // Label is a fixed sibling below the artwork, not inside the same scaling/
                // clipped Surface -- see TvHomeScreen's AnimeRow for why (a focused card's
                // title got clipped by its own rounded-corner mask on real TV hardware).
                Column(modifier = Modifier.width(TvDimens.CardWidth)) {
                    Surface(
                        onClick = { onOpenAnime(card.key) },
                        modifier = if (index == 0) Modifier.focusRequester(firstResultFocusRequester) else Modifier,
                        shape = ClickableSurfaceDefaults.shape(shape = shape),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocus.CardScale),
                        border = TvFocus.border(shape),
                        glow = TvFocus.glow(),
                        colors = ClickableSurfaceDefaults.colors(containerColor = TvColors.Surface),
                    ) {
                        AsyncImage(
                            model = resolveCoverUrl(card.cover_url, card.source),
                            contentDescription = card.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(TvDimens.CardWidth)
                                .height(TvDimens.CardHeight)
                                .clip(shape)
                                .background(TvColors.Surface),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(card.title, color = TvColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }
        }
    }
}
