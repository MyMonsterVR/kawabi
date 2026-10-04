package com.mymonstervr.kawabi.app.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mymonstervr.kawabi.app.common.CoverPill
import com.mymonstervr.kawabi.app.common.SectionHeader
import com.mymonstervr.kawabi.app.common.SegmentedTabs
import com.mymonstervr.kawabi.app.common.glass
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.data.network.dto.AnimeCardDto
import com.mymonstervr.kawabi.data.network.resolveCoverUrl
import com.mymonstervr.kawabi.domain.model.AnimeLibraryEntry
import com.mymonstervr.kawabi.domain.model.AnimeWatchStatus
import com.mymonstervr.kawabi.domain.model.NewEpisode
import com.mymonstervr.kawabi.domain.model.formatChapterNumber
import com.mymonstervr.kawabi.domain.model.formatRelativeTime

private val CONTINUE_CARD_WIDTH = 128.dp
private val PROGRESS_RED = Color(0xFFEF4D55)
private const val FEED_PAGE_SIZE = 9
private const val FEED_COLUMNS = 3
private const val NEW_EPISODE_WINDOW_MS = 3L * 24 * 60 * 60 * 1000

private class FeedCard(
    val key: String,
    val title: String,
    val coverUrl: String?,
    val source: String?,
    val pill: String?,
    val isNew: Boolean,
    val subtitle: String?,
    val onClick: () -> Unit,
)

@Composable
internal fun AnimeHomePane(
    entries: List<AnimeLibraryEntry>,
    newEpisodes: SectionState<NewEpisode>,
    newReleases: SectionState<AnimeCardDto>,
    onContinue: (Long, String) -> Unit,
    onEpisodeClick: (String) -> Unit,
    onAnimeClick: (String) -> Unit,
    onReleaseClick: (AnimeCardDto) -> Unit,
    onSeeAllWatching: () -> Unit,
    onRetryNewEpisodes: () -> Unit,
    onRetryNewReleases: () -> Unit,
) {
    val scale = LocalKawabiScale.current
    var feedTab by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val continueRail = remember(entries) {
        entries.filter { it.status == AnimeWatchStatus.WATCHING && it.unwatchedCount > 0 }
            .sortedByDescending { it.anime.lastWatchedAt }
            .take(CONTINUE_RAIL_MAX)
    }

    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyAnimeMessage(
                title = "Nothing here yet",
                hint = "Search for a show and add it to your library to start watching.",
            )
        }
        return
    }

    val feedState: SectionState<FeedCard> = if (feedTab == 0) {
        when (newEpisodes) {
            is SectionState.Loading -> SectionState.Loading
            is SectionState.Error -> SectionState.Error(newEpisodes.message)
            is SectionState.Loaded -> SectionState.Loaded(
                newEpisodes.items.map { episode ->
                    FeedCard(
                        key = "e${episode.episodeId}",
                        title = episode.animeTitle,
                        coverUrl = episode.animeThumbnailUrl,
                        source = episode.animeSource,
                        pill = if (episode.episodeNumber >= 0) "EP ${formatChapterNumber(episode.episodeNumber)}" else null,
                        isNew = episode.dateUpload > 0 && System.currentTimeMillis() - episode.dateUpload < NEW_EPISODE_WINDOW_MS,
                        subtitle = formatRelativeTime(episode.dateUpload),
                        onClick = { onEpisodeClick(episode.episodeKey) },
                    )
                },
            )
        }
    } else {
        when (newReleases) {
            is SectionState.Loading -> SectionState.Loading
            is SectionState.Error -> SectionState.Error(newReleases.message)
            is SectionState.Loaded -> SectionState.Loaded(
                newReleases.items.map { card ->
                    FeedCard(
                        key = "r${card.key}",
                        title = card.displayTitle ?: card.title,
                        coverUrl = card.cover_url,
                        source = card.source,
                        pill = null,
                        isNew = false,
                        subtitle = card.source_name.ifBlank { null },
                        onClick = { onReleaseClick(card) },
                    )
                },
            )
        }
    }
    val feedTotal = (feedState as? SectionState.Loaded)?.items?.size ?: 0
    val safePage = page.coerceIn(0, ((feedTotal - 1) / FEED_PAGE_SIZE).coerceAtLeast(0))
    val hasPrev = safePage > 0
    val hasNext = (safePage + 1) * FEED_PAGE_SIZE < feedTotal

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp * scale.spacing),
    ) {
        if (continueRail.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Continue watching",
                    actionLabel = "See all",
                    onAction = onSeeAllWatching,
                    modifier = Modifier.padding(start = 16.dp * scale.spacing, end = 8.dp * scale.spacing, top = 12.dp * scale.spacing),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp * scale.spacing, vertical = 8.dp * scale.spacing),
                    horizontalArrangement = Arrangement.spacedBy(12.dp * scale.spacing),
                ) {
                    items(continueRail, key = { it.anime.id }) { entry ->
                        ContinueCard(entry = entry, onClick = { onContinue(entry.anime.id, entry.anime.key) })
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp * scale.spacing)
                    .padding(top = 18.dp * scale.spacing, bottom = 14.dp * scale.spacing),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SegmentedTabs(
                    options = listOf("Episodes", "Releases"),
                    selectedIndex = feedTab,
                    onSelect = { feedTab = it; page = 0 },
                    equalWidth = false,
                )
                FeedPager(
                    hasPrev = hasPrev,
                    hasNext = hasNext,
                    onPrev = { page = safePage - 1 },
                    onNext = { page = safePage + 1 },
                )
            }
        }

        when (feedState) {
            is SectionState.Loading -> item { ShimmerGrid() }
            is SectionState.Error -> item {
                AnimeSectionError(
                    message = feedState.message,
                    onRetry = if (feedTab == 0) onRetryNewEpisodes else onRetryNewReleases,
                )
            }
            is SectionState.Loaded -> {
                if (feedState.items.isEmpty()) {
                    item {
                        AnimeSectionNote(
                            if (feedTab == 0) "No new episodes in the last two weeks." else "No new releases right now.",
                        )
                    }
                } else {
                    val rows = feedState.items
                        .drop(safePage * FEED_PAGE_SIZE)
                        .take(FEED_PAGE_SIZE)
                        .chunked(FEED_COLUMNS)
                    items(rows, key = { row -> row.first().key }) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp * scale.spacing)
                                .padding(bottom = 18.dp * scale.spacing),
                            horizontalArrangement = Arrangement.spacedBy(10.dp * scale.spacing),
                        ) {
                            row.forEach { card ->
                                Box(modifier = Modifier.weight(1f)) { FeedCardView(card) }
                            }
                            repeat(FEED_COLUMNS - row.size) { Box(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedPager(hasPrev: Boolean, hasNext: Boolean, onPrev: () -> Unit, onNext: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(modifier = Modifier.glass(shape).padding(3.dp)) {
        PagerButton(enabled = hasPrev, onClick = onPrev, newer = true)
        PagerButton(enabled = hasNext, onClick = onNext, newer = false)
    }
}

@Composable
private fun PagerButton(enabled: Boolean, onClick: () -> Unit, newer: Boolean) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (newer) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (newer) "Newer" else "Older",
            tint = if (enabled) MaterialTheme.colorScheme.primary else NightSession.TextDim.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun FeedCardView(card: FeedCard) {
    val scale = LocalKawabiScale.current
    val shape = RoundedCornerShape(12.dp)
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = card.onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(NightSession.Cover)
                .border(1.dp, Color.White.copy(alpha = 0.07f), shape),
        ) {
            AsyncImage(
                model = resolveCoverUrl(card.coverUrl, card.source),
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (card.pill != null) {
                CoverPill(
                    text = card.pill,
                    modifier = Modifier.align(Alignment.BottomStart).padding(7.dp),
                )
            }
            if (card.isNew) {
                CoverPill(
                    text = "NEW",
                    accent = true,
                    modifier = Modifier.align(Alignment.TopStart).padding(7.dp),
                )
            }
        }
        Text(
            text = card.title,
            fontSize = 12.sp * scale.font,
            lineHeight = 15.6.sp * scale.font,
            fontWeight = FontWeight.Medium,
            color = NightSession.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!card.subtitle.isNullOrBlank()) {
            Text(
                text = card.subtitle,
                fontSize = 11.sp * scale.font,
                color = NightSession.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ShimmerGrid() {
    val scale = LocalKawabiScale.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp * scale.spacing),
        horizontalArrangement = Arrangement.spacedBy(10.dp * scale.spacing),
    ) {
        repeat(FEED_COLUMNS) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NightSession.Chip),
            )
        }
    }
}

@Composable
private fun AnimeSectionError(message: String, onRetry: () -> Unit) {
    val scale = LocalKawabiScale.current
    Row(
        modifier = Modifier.padding(horizontal = 16.dp * scale.spacing, vertical = 6.dp * scale.spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Couldn't load", fontSize = 11.sp * scale.font, color = NightSession.TextDim)
        TextButton(onClick = onRetry) {
            Text(text = "Retry", fontSize = 11.sp * scale.font, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ContinueCard(entry: AnimeLibraryEntry, onClick: () -> Unit) {
    val scale = LocalKawabiScale.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .width(CONTINUE_CARD_WIDTH * scale.spacing)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(NightSession.Cover)
                .border(1.dp, Color.White.copy(alpha = 0.07f), shape),
        ) {
            AsyncImage(
                model = resolveCoverUrl(entry.anime.thumbnailUrl, entry.anime.source),
                contentDescription = entry.anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            entry.nextUnwatchedNumber?.let { next ->
                CoverPill(
                    text = "EP ${formatChapterNumber(next)}",
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(NightSession.Background.copy(alpha = 0.7f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(entry.watchedFraction.coerceIn(0f, 1f))
                        .background(PROGRESS_RED),
                )
            }
        }
        Text(
            text = entry.anime.title,
            fontSize = 13.sp * scale.font,
            fontWeight = FontWeight.Medium,
            color = NightSession.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 9.dp),
        )
        Text(
            text = "${entry.unwatchedCount} left",
            fontSize = 11.sp * scale.font,
            color = NightSession.TextDim,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
