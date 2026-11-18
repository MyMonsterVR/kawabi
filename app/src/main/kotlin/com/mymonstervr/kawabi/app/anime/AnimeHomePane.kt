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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mymonstervr.kawabi.app.common.CoverPill
import com.mymonstervr.kawabi.app.common.FallbackCover
import com.mymonstervr.kawabi.app.common.SectionHeader
import com.mymonstervr.kawabi.app.common.SegmentedTabs
import com.mymonstervr.kawabi.app.common.glass
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.data.network.dto.AnimeCardDto
import com.mymonstervr.kawabi.data.network.resolveCoverUrl
import com.mymonstervr.kawabi.domain.model.AnimeLibraryEntry
import com.mymonstervr.kawabi.domain.model.AnimeWatchStatus
import com.mymonstervr.kawabi.domain.model.AUDIO_DUB
import com.mymonstervr.kawabi.domain.model.AUDIO_SUB
import com.mymonstervr.kawabi.domain.model.formatChapterNumber

private val CONTINUE_CARD_WIDTH = 128.dp
private val PROGRESS_RED = Color(0xFFEF4D55)
private const val FEED_PAGE_SIZE = 9
private const val FEED_COLUMNS = 3
private const val DAY_MS = 24L * 60 * 60 * 1000
private const val NEW_EPISODE_WINDOW_MS = 3 * DAY_MS

private class FeedCard(
    val key: String,
    val title: String,
    val coverUrl: String?,
    val coverFallbackUrl: String?,
    val source: String?,
    val pill: String?,
    val isNew: Boolean,
    val isDub: Boolean,
    val subtitle: String?,
    val onClick: () -> Unit,
)

private fun relativeDay(epochMillis: Long): String? {
    if (epochMillis <= 0) return null
    val days = ((System.currentTimeMillis() - epochMillis) / DAY_MS).coerceAtLeast(0)
    return when (days) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> "$days days ago"
    }
}

private fun releaseFilterIndex(filter: String) = when (filter) {
    AUDIO_SUB -> 1
    AUDIO_DUB -> 2
    else -> 0
}

@Composable
internal fun AnimeHomePane(
    entries: List<AnimeLibraryEntry>,
    episodes: EpisodesUi,
    releases: ReleasesUi,
    onContinue: (Long, String) -> Unit,
    onEpisodeClick: (FeedEpisode) -> Unit,
    onReleaseClick: (AnimeCardDto) -> Unit,
    onSeeAllWatching: () -> Unit,
    onRetryNewEpisodes: () -> Unit,
    onRetryNewReleases: () -> Unit,
    onShowMoreDays: () -> Unit,
    onNeedReleases: (Int) -> Unit,
    onReleaseAudio: (String) -> Unit,
) {
    val scale = LocalKawabiScale.current
    val uriHandler = LocalUriHandler.current
    var feedTab by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val continueRail = remember(entries) {
        entries.filter { it.status == AnimeWatchStatus.WATCHING && it.unwatchedCount > 0 }
            .sortedByDescending { it.anime.lastWatchedAt }
            .take(CONTINUE_RAIL_MAX)
    }

    LaunchedEffect(feedTab, page) {
        if (feedTab == 1) onNeedReleases((page + 1) * FEED_PAGE_SIZE + 1)
    }

    val feedState: SectionState<FeedCard> = if (feedTab == 0) {
        when (val state = episodes.state) {
            is SectionState.Loading -> SectionState.Loading
            is SectionState.Error -> SectionState.Error(state.message)
            is SectionState.Loaded -> SectionState.Loaded(
                state.items.map { episode ->
                    FeedCard(
                        key = episode.key,
                        title = episode.title,
                        coverUrl = episode.coverUrl,
                        coverFallbackUrl = episode.coverFallbackUrl,
                        source = episode.source,
                        pill = if (episode.number.isFinite() && episode.number > 0) "EP ${formatChapterNumber(episode.number)}" else null,
                        isNew = episode.dateUpload > 0 && System.currentTimeMillis() - episode.dateUpload < NEW_EPISODE_WINDOW_MS,
                        isDub = false,
                        subtitle = relativeDay(episode.dateUpload),
                        onClick = { onEpisodeClick(episode) },
                    )
                },
            )
        }
    } else {
        when {
            releases.cards.isNotEmpty() -> SectionState.Loaded(
                releases.cards.map { release ->
                    val card = release.card
                    FeedCard(
                        key = "r${card.key}",
                        title = card.displayTitle ?: card.title,
                        coverUrl = card.cover_url,
                        coverFallbackUrl = card.coverFallbackUrl,
                        source = card.source,
                        pill = null,
                        isNew = false,
                        isDub = release.audio == AUDIO_DUB,
                        subtitle = card.source_name.ifBlank { null },
                        onClick = { onReleaseClick(card) },
                    )
                },
            )
            releases.failed -> SectionState.Error("Couldn't load new releases.")
            !releases.started || releases.loading || releases.checkingAudio -> SectionState.Loading
            else -> SectionState.Loaded(emptyList())
        }
    }
    val feedTotal = (feedState as? SectionState.Loaded)?.items?.size ?: 0
    val safePage = page.coerceIn(0, ((feedTotal - 1) / FEED_PAGE_SIZE).coerceAtLeast(0))
    val hasPrev = safePage > 0
    val hasNext = (safePage + 1) * FEED_PAGE_SIZE < feedTotal || (feedTab == 1 && releases.canGrow && !releases.failed)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp * scale.spacing),
    ) {
        if (entries.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyAnimeMessage(
                        title = "Nothing here yet",
                        hint = "Search for a show and add it to your library to start watching.",
                    )
                }
            }
        } else if (continueRail.isNotEmpty()) {
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

        if (feedTab == 1) {
            item {
                SegmentedTabs(
                    options = listOf("All", "Sub", "Dub"),
                    selectedIndex = releaseFilterIndex(releases.filter),
                    onSelect = {
                        page = 0
                        onReleaseAudio(listOf("", AUDIO_SUB, AUDIO_DUB)[it])
                    },
                    equalWidth = false,
                    modifier = Modifier
                        .padding(horizontal = 16.dp * scale.spacing)
                        .padding(bottom = 14.dp * scale.spacing),
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
                            if (feedTab == 0) {
                                "No new episodes in the last ${episodes.days} days."
                            } else when (releases.filter) {
                                AUDIO_SUB -> "No subbed releases right now."
                                AUDIO_DUB -> "No dubbed releases right now."
                                else -> "No new releases right now."
                            },
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

        if (feedTab == 0 && episodes.fromFeed && episodes.days < FEED_DAYS_MAX) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onShowMoreDays) {
                        Text(text = "Show 30 more days", fontSize = 12.sp * scale.font, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (feedTab == 1 && feedState is SectionState.Loaded && releases.checkingAudio) {
            item { AnimeSectionNote("Checking audio…") }
        }
        if (feedTab == 1 && releases.filter == AUDIO_DUB && releases.cards.isNotEmpty()) {
            item {
                Text(
                    text = "Dub dates powered by AnimeSchedule.net",
                    fontSize = 10.sp * scale.font,
                    color = NightSession.TextDim,
                    modifier = Modifier
                        .padding(horizontal = 16.dp * scale.spacing, vertical = 6.dp * scale.spacing)
                        .clickable { uriHandler.openUri("https://animeschedule.net") },
                )
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
            FallbackCover(
                coverUrl = card.coverUrl,
                fallbackUrl = card.coverFallbackUrl,
                source = card.source,
                contentDescription = card.title,
                modifier = Modifier.fillMaxSize(),
            )
            if (card.isDub) {
                CoverPill(
                    text = "DUB",
                    accent = true,
                    modifier = Modifier.align(Alignment.TopStart).padding(7.dp),
                )
            }
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
        Text(text = message, fontSize = 11.sp * scale.font, color = NightSession.TextDim)
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
