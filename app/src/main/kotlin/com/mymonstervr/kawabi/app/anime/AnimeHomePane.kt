package com.mymonstervr.kawabi.app.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mymonstervr.kawabi.app.common.MediaGridCard
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.data.network.dto.AnimeCardDto
import com.mymonstervr.kawabi.data.network.resolveCoverUrl
import com.mymonstervr.kawabi.domain.model.AnimeLibraryEntry
import com.mymonstervr.kawabi.domain.model.AnimeWatchStatus
import com.mymonstervr.kawabi.domain.model.NewEpisode
import com.mymonstervr.kawabi.domain.model.formatChapterNumber
import com.mymonstervr.kawabi.domain.model.formatRelativeTime

private val CONTINUE_CARD_WIDTH = 116.dp
private val RELEASE_CARD_WIDTH = 104.dp
private val EPISODE_THUMB_WIDTH = 46.dp
private val EPISODE_THUMB_HEIGHT = 64.dp

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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp * scale.spacing),
    ) {
        if (continueRail.isNotEmpty()) {
            item {
                AnimeSectionHeader("Continue watching")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp * scale.spacing),
                    horizontalArrangement = Arrangement.spacedBy(10.dp * scale.spacing),
                ) {
                    items(continueRail, key = { it.anime.id }) { entry ->
                        ContinueCard(entry = entry, onClick = { onContinue(entry.anime.id, entry.anime.key) })
                    }
                    item { SeeAllTile(onClick = onSeeAllWatching) }
                }
            }
        }

        item {
            AnimeSectionHeader("New episodes for you", loading = newEpisodes is SectionState.Loading)
        }
        when (newEpisodes) {
            is SectionState.Loading -> item { ShimmerRail() }
            is SectionState.Error -> item {
                AnimeSectionError(message = newEpisodes.message, onRetry = onRetryNewEpisodes)
            }
            is SectionState.Loaded -> {
                if (newEpisodes.items.isEmpty()) {
                    item { AnimeSectionNote("No new episodes in the last two weeks.") }
                } else {
                    items(newEpisodes.items, key = { it.episodeId }) { episode ->
                        NewEpisodeRow(episode = episode, onClick = { onEpisodeClick(episode.episodeKey) })
                    }
                }
            }
        }

        // Hidden entirely only once loaded with nothing -- a broken rail is worse than no
        // rail on a screen that's otherwise fully usable offline, but a loading/error state
        // still needs to render so the section doesn't look like it's missing.
        when (newReleases) {
            is SectionState.Loading -> item {
                AnimeSectionHeader("New releases", loading = true)
                ShimmerRail()
            }
            is SectionState.Error -> item {
                AnimeSectionHeader("New releases")
                AnimeSectionError(message = newReleases.message, onRetry = onRetryNewReleases)
            }
            is SectionState.Loaded -> if (newReleases.items.isNotEmpty()) {
                item {
                    AnimeSectionHeader("New releases")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp * scale.spacing),
                        horizontalArrangement = Arrangement.spacedBy(10.dp * scale.spacing),
                    ) {
                        items(newReleases.items, key = { it.key }) { card ->
                            Box(modifier = Modifier.width(RELEASE_CARD_WIDTH * scale.spacing)) {
                                MediaGridCard(
                                    title = card.title,
                                    coverUrl = card.cover_url,
                                    subtitle = card.source_name,
                                    onClick = { onReleaseClick(card) },
                                    source = card.source,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShimmerRail() {
    val scale = LocalKawabiScale.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp * scale.spacing),
        horizontalArrangement = Arrangement.spacedBy(10.dp * scale.spacing),
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(RELEASE_CARD_WIDTH * scale.spacing)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(NightSession.RadiusMd))
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
    Column(
        modifier = Modifier
            .width(CONTINUE_CARD_WIDTH * scale.spacing)
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = resolveCoverUrl(entry.anime.thumbnailUrl, entry.anime.source),
                contentDescription = entry.anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(NightSession.RadiusMd))
                    .border(1.dp, NightSession.Hairline, RoundedCornerShape(NightSession.RadiusMd))
                    .background(NightSession.Cover),
            )
            Icon(
                Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = NightSession.OnAccent,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp * scale.spacing)
                    .clip(RoundedCornerShape(100))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(3.dp * scale.spacing)
                    .size(13.dp * scale.spacing),
            )
        }
        // Progress is drawn as its own bar under the cover rather than over it: the
        // watched/total ratio is the point of this rail, and an overlay on a busy cover
        // is the first thing to become unreadable.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp * scale.spacing)
                .height(3.dp)
                .clip(RoundedCornerShape(100))
                .background(NightSession.Chip),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(entry.watchedFraction)
                    .height(3.dp)
                    .clip(RoundedCornerShape(100))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(modifier = Modifier.height(5.dp * scale.spacing))
        Text(
            text = entry.anime.title,
            fontSize = 10.5.sp * scale.font,
            fontWeight = FontWeight.SemiBold,
            color = NightSession.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.nextUnwatchedNumber?.let { "Ep ${formatChapterNumber(it)}" }
                ?: "${entry.unwatchedCount} to watch",
            fontSize = 9.5.sp * scale.font,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SeeAllTile(onClick: () -> Unit) {
    val scale = LocalKawabiScale.current
    Column(modifier = Modifier.width(CONTINUE_CARD_WIDTH * scale.spacing).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(NightSession.RadiusMd))
                .background(NightSession.Chip)
                .border(1.dp, NightSession.Hairline, RoundedCornerShape(NightSession.RadiusMd)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = NightSession.TextDim,
                modifier = Modifier.size(18.dp * scale.spacing),
            )
        }
        Spacer(modifier = Modifier.height(11.dp * scale.spacing))
        Text(
            text = "See all",
            fontSize = 10.5.sp * scale.font,
            fontWeight = FontWeight.SemiBold,
            color = NightSession.TextDim,
        )
    }
}

@Composable
private fun NewEpisodeRow(episode: NewEpisode, onClick: () -> Unit) {
    val scale = LocalKawabiScale.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp * scale.spacing, vertical = 6.dp * scale.spacing),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp * scale.spacing),
    ) {
        AsyncImage(
            model = resolveCoverUrl(episode.animeThumbnailUrl, episode.animeSource),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(EPISODE_THUMB_WIDTH * scale.spacing)
                .height(EPISODE_THUMB_HEIGHT * scale.spacing)
                .clip(RoundedCornerShape(NightSession.RadiusSm))
                .border(1.dp, NightSession.Hairline, RoundedCornerShape(NightSession.RadiusSm))
                .background(NightSession.Cover),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.animeTitle,
                fontSize = 11.5.sp * scale.font,
                fontWeight = FontWeight.SemiBold,
                color = NightSession.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = newEpisodeLabel(episode),
                fontSize = 10.5.sp * scale.font,
                color = NightSession.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        formatRelativeTime(episode.dateUpload)?.let { relative ->
            Text(text = relative, fontSize = 9.5.sp * scale.font, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun newEpisodeLabel(episode: NewEpisode): String = episode.name.ifBlank {
    if (episode.episodeNumber >= 0) "Episode ${formatChapterNumber(episode.episodeNumber)}" else "New episode"
}
