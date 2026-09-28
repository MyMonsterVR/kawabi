package com.mymonstervr.kawabi.tv.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.mymonstervr.kawabi.data.network.dto.AnimeDetailResponse
import com.mymonstervr.kawabi.data.network.dto.EpisodeDto
import com.mymonstervr.kawabi.data.network.resolveCoverUrl
import com.mymonstervr.kawabi.tv.theme.TvColors
import com.mymonstervr.kawabi.tv.theme.TvDimens
import com.mymonstervr.kawabi.tv.theme.TvFocus
import org.koin.androidx.compose.koinViewModel

@Composable
fun TvDetailScreen(
    animeKey: String,
    onBack: () -> Unit,
    onOpenEpisode: (String) -> Unit,
    viewModel: TvDetailViewModel = koinViewModel(),
) {
    LaunchedEffect(animeKey) { viewModel.load(animeKey) }
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(TvColors.Background)) {
        when (val currentState = state) {
            TvDetailUiState.Loading -> Text(
                "Loading...",
                color = TvColors.TextDim,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center),
            )
            is TvDetailUiState.Error -> Text(
                currentState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center),
            )
            is TvDetailUiState.Loaded -> DetailContent(currentState.detail, currentState.resumeEpisodeKey, onOpenEpisode)
        }
    }
}

@Composable
private fun DetailContent(detail: AnimeDetailResponse, resumeEpisodeKey: String?, onOpenEpisode: (String) -> Unit) {
    val shape = RoundedCornerShape(TvDimens.RadiusMd)
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(480.dp)) {
            // Cover art is portrait poster art, not a wide banner -- a plain Crop over this
            // box's width zooms in far too tight. Blur it full-bleed as a backdrop instead
            // and show the actual poster small and sharp in front, Netflix/Crunchyroll-style.
            AsyncImage(
                model = resolveCoverUrl(detail.cover_url, detail.source),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(36.dp)
                    .background(TvColors.BackgroundGradientTop),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TvColors.Background.copy(alpha = 0.55f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(androidx.compose.ui.graphics.Color.Transparent, TvColors.Background),
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = TvDimens.OverscanHorizontal, bottom = 24.dp)
                    .widthIn(max = 800.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                AsyncImage(
                    model = resolveCoverUrl(detail.cover_url, detail.source),
                    contentDescription = detail.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(140.dp)
                        .height(200.dp)
                        .clip(shape)
                        .background(TvColors.Surface),
                )
                Spacer(Modifier.width(20.dp))
                Column(modifier = Modifier.widthIn(max = 620.dp)) {
                    Text(detail.title, color = TvColors.Text, style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        listOfNotNull(
                            detail.author,
                            detail.status.ifBlank { null },
                            detail.total_episodes.takeIf { it > 0 }?.let { "${it.toInt()} episodes" },
                            detail.source_name.ifBlank { null },
                        ).joinToString(" · "),
                        color = TvColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(16.dp))
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    Button(
                        onClick = { resumeEpisodeKey?.let(onOpenEpisode) },
                        modifier = Modifier.focusRequester(focusRequester),
                        colors = ButtonDefaults.colors(
                            containerColor = TvColors.Accent,
                            contentColor = TvColors.OnAccent,
                            focusedContainerColor = TvColors.Accent,
                            focusedContentColor = TvColors.OnAccent,
                        ),
                    ) { Text(if (resumeEpisodeKey != null) "Play" else "Unavailable") }
                }
            }
        }

        Column(modifier = Modifier.padding(top = 24.dp)) {
            detail.description?.let { synopsis ->
                Text(
                    synopsis,
                    color = TvColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    modifier = Modifier.padding(horizontal = TvDimens.OverscanHorizontal).fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
            }
            Text(
                "EPISODES",
                color = TvColors.TextDim,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = TvDimens.OverscanHorizontal),
            )
            Spacer(Modifier.height(16.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = TvDimens.OverscanHorizontal),
            ) {
                items(detail.episodes.sortedBy { it.number }, key = { it.key }) { episode ->
                    EpisodeCard(episode, onClick = { onOpenEpisode(episode.key) })
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(episode: EpisodeDto, onClick: () -> Unit) {
    val shape = RoundedCornerShape(TvDimens.RadiusMd)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocus.ButtonScale),
        border = TvFocus.border(shape),
        glow = TvFocus.glow(),
        colors = ClickableSurfaceDefaults.colors(containerColor = TvColors.Surface),
    ) {
        Column(modifier = Modifier.width(220.dp).padding(16.dp)) {
            Text(
                episode.title.ifBlank { "Episode ${episode.number.toInt()}" },
                color = TvColors.Text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
            )
        }
    }
}
