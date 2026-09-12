package com.mymonstervr.kawabi.app.anime

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.data.network.resolveCoverUrl
import com.mymonstervr.kawabi.domain.model.AnimeLibraryEntry
import com.mymonstervr.kawabi.domain.model.AnimeWatchStatus
import com.mymonstervr.kawabi.domain.model.formatChapterNumber
import com.mymonstervr.kawabi.domain.model.formatRelativeTime

private val ROW_THUMB_WIDTH = 50.dp
private val ROW_THUMB_HEIGHT = 70.dp

@Composable
internal fun AnimeWatchingPane(
    entries: List<AnimeLibraryEntry>,
    onContinue: (Long, String) -> Unit,
    onDetails: (String) -> Unit,
) {
    val scale = LocalKawabiScale.current
    val watching = remember(entries) {
        entries.filter { it.status == AnimeWatchStatus.WATCHING }
            .sortedWith(compareByDescending<AnimeLibraryEntry> { it.anime.lastWatchedAt }.thenBy { it.anime.title.lowercase() })
    }

    if (watching.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyAnimeMessage(
                title = "Nothing in progress",
                hint = "Anything you're partway through, or that a linked tracker marks as watching, shows up here.",
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 6.dp * scale.spacing),
    ) {
        items(watching, key = { it.anime.id }) { entry ->
            WatchingRow(
                entry = entry,
                onClick = { onContinue(entry.anime.id, entry.anime.key) },
                onLongClick = { onDetails(entry.anime.key) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WatchingRow(entry: AnimeLibraryEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val scale = LocalKawabiScale.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp * scale.spacing, vertical = 8.dp * scale.spacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp * scale.spacing),
        ) {
            AsyncImage(
                model = resolveCoverUrl(entry.anime.thumbnailUrl, entry.anime.source),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(ROW_THUMB_WIDTH * scale.spacing)
                    .height(ROW_THUMB_HEIGHT * scale.spacing)
                    .clip(RoundedCornerShape(NightSession.RadiusSm))
                    .border(1.dp, NightSession.Hairline, RoundedCornerShape(NightSession.RadiusSm))
                    .background(NightSession.Cover),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.anime.title,
                    fontSize = 12.sp * scale.font,
                    fontWeight = FontWeight.SemiBold,
                    color = NightSession.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${entry.watchedCount} / ${entry.episodeCount} watched",
                    fontSize = 10.5.sp * scale.font,
                    color = NightSession.TextDim,
                    modifier = Modifier.padding(top = 2.dp * scale.spacing),
                )
                formatRelativeTime(entry.latestEpisodeAt)?.let { relative ->
                    Text(
                        text = "Latest episode $relative",
                        fontSize = 9.5.sp * scale.font,
                        color = NightSession.TextDim,
                    )
                }
            }
            entry.nextUnwatchedNumber?.let { next ->
                Text(
                    text = "Ep ${formatChapterNumber(next)}",
                    fontSize = 10.5.sp * scale.font,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        HorizontalDivider(color = NightSession.Hairline, modifier = Modifier.padding(horizontal = 16.dp * scale.spacing))
    }
}
