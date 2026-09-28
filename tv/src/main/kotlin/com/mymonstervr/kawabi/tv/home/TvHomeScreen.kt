package com.mymonstervr.kawabi.tv.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mymonstervr.kawabi.data.network.dto.AnimeCardDto
import com.mymonstervr.kawabi.data.network.resolveCoverUrl
import com.mymonstervr.kawabi.tv.theme.TvColors
import com.mymonstervr.kawabi.tv.theme.TvDimens
import com.mymonstervr.kawabi.tv.theme.TvFocus
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

@Composable
fun TvHomeScreen(
    onSignOut: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAnime: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    viewModel: TvHomeViewModel = koinViewModel(),
) {
    val continueWatching by viewModel.continueWatching.collectAsState()
    val newReleases by viewModel.newReleases.collectAsState()
    val hero = continueWatching.firstOrNull()

    Row(modifier = Modifier.fillMaxSize().background(TvColors.Background)) {
        NavRail(onOpenSearch = onOpenSearch, onOpenSettings = onOpenSettings)

        // The whole screen scrolls -- hero + every row used to sit in a plain, non-scrolling
        // Column, which only ever fit inside the viewport by accident. At the real 960x540dp
        // canvas a 460dp-tall hero plus a full row (label + 310dp cards + title) is nowhere
        // close to fitting: confirmed live on real TV hardware, row card titles (and most of
        // the row itself) rendered entirely below the visible screen, permanently unreachable
        // by any input. LazyColumn makes the overflow scrollable instead of silently clipped,
        // and D-pad down from the hero button now naturally continues scrolling into it.
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
            // Hero
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(
                        Brush.linearGradient(listOf(TvColors.BackgroundGradientTop, TvColors.Background)),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(TvColors.Background, TvColors.Background.copy(alpha = 0.35f), androidx.compose.ui.graphics.Color.Transparent),
                            ),
                        ),
                )
                if (hero != null) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = TvDimens.OverscanHorizontal, bottom = 24.dp),
                    ) {
                        Text("CONTINUE WATCHING", color = TvColors.Accent, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(hero.anime.title, color = TvColors.Text, style = MaterialTheme.typography.displayLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Episode ${hero.resumeEpisode.episodeNumber.toInt()}".let {
                                if (hero.resumeEpisode.name.isNotBlank()) "$it -- ${hero.resumeEpisode.name}" else it
                            },
                            color = TvColors.TextSecondary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        hero.anime.description?.takeIf { it.isNotBlank() }?.let { synopsis ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                synopsis,
                                color = TvColors.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                modifier = Modifier.widthIn(max = 640.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { onOpenEpisode(hero.resumeEpisode.key) },
                            colors = ButtonDefaults.colors(
                                containerColor = TvColors.Accent,
                                contentColor = TvColors.OnAccent,
                                focusedContainerColor = TvColors.Accent,
                                focusedContentColor = TvColors.OnAccent,
                            ),
                        ) { Text("Resume") }
                    }
                }
            }
            }

            item { Spacer(Modifier.height(24.dp)) }

            if (continueWatching.isNotEmpty()) {
                item {
                    AnimeRow(
                        title = "Continue Watching",
                        items = continueWatching,
                        cover = { it.anime.thumbnailUrl },
                        source = { null },
                        label = { it.anime.title },
                        onClick = { onOpenEpisode(it.resumeEpisode.key) },
                    )
                }
                item { Spacer(Modifier.height(32.dp)) }
            }

            item {
                AnimeRow(
                    title = "New Releases",
                    items = newReleases,
                    cover = { it.cover_url },
                    source = { it.source },
                    label = { it.title },
                    onClick = { onOpenAnime(it.key) },
                )
            }

            item { Spacer(Modifier.height(TvDimens.OverscanVertical)) }
        }
    }
}

@Composable
private fun NavRail(onOpenSearch: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .width(TvDimens.RailWidth)
            .fillMaxSize()
            .background(TvColors.Surface.copy(alpha = 0.4f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        RailIcon(Icons.Filled.Home, "Home", selected = true, onClick = {})
        Spacer(Modifier.height(28.dp))
        RailIcon(Icons.Filled.Search, "Search", selected = false, onClick = onOpenSearch)
        Spacer(Modifier.weight(1f))
        RailIcon(Icons.Filled.Settings, "Settings", selected = false, onClick = onOpenSettings, modifier = Modifier.padding(bottom = 40.dp))
    }
}

@Composable
private fun RailIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = ClickableSurfaceDefaults.shape(shape = ClickableShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) TvColors.ChipHover else androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = TvColors.Accent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocus.ButtonScale),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            // TextDim reads fine for body copy but was reported genuinely hard to see as an
            // unfocused nav icon on real TV hardware (lower brightness/contrast than a
            // monitor) -- TextSecondary is the same family, just enough brighter to read
            // from a couch while still visually receding behind the focused/selected state.
            androidx.tv.material3.Icon(icon, contentDescription = label, tint = if (selected) TvColors.Accent else TvColors.TextSecondary)
        }
    }
}

private val ClickableShape = RoundedCornerShape(TvDimens.RadiusMd)

@Composable
private fun <T> AnimeRow(
    title: String,
    items: List<T>,
    cover: (T) -> String?,
    source: (T) -> String?,
    label: (T) -> String,
    onClick: (T) -> Unit,
) {
    Column {
        Text(
            title.uppercase(),
            color = TvColors.TextDim,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = TvDimens.OverscanHorizontal),
        )
        Spacer(Modifier.height(16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(TvDimens.CardSpacing),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = TvDimens.OverscanHorizontal),
        ) {
            items(items) { item ->
                Surface(
                    onClick = { onClick(item) },
                    shape = ClickableSurfaceDefaults.shape(shape = ClickableShape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocus.CardScale),
                    border = TvFocus.border(ClickableShape),
                    glow = TvFocus.glow(),
                    colors = ClickableSurfaceDefaults.colors(containerColor = TvColors.Surface),
                ) {
                    Column(modifier = Modifier.width(TvDimens.CardWidth)) {
                        AsyncImage(
                            model = resolveCoverUrl(cover(item), source(item)),
                            contentDescription = label(item),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(TvDimens.CardWidth)
                                .height(TvDimens.CardHeight)
                                .clip(ClickableShape)
                                .background(TvColors.Surface),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            label(item),
                            color = TvColors.Text,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
