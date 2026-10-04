package com.mymonstervr.kawabi.app.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.data.network.dto.SearchResultDto
import com.mymonstervr.kawabi.data.network.resolveCoverUrl

/**
 * One cover tile in any of the app's grids -- manga search/browse and the anime equivalents.
 * [coverUrl] is passed through [resolveCoverUrl] here rather than by the caller so every
 * grid gets the same proxy handling, [source] being the site key that decides it.
 */
@Composable
fun MediaGridCard(
    title: String,
    coverUrl: String?,
    subtitle: String?,
    onClick: () -> Unit,
    source: String? = null,
    badge: String? = null,
    pill: String? = null,
) {
    val scale = LocalKawabiScale.current
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = resolveCoverUrl(coverUrl, source),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(NightSession.RadiusMd))
                    .border(1.dp, NightSession.Hairline, RoundedCornerShape(NightSession.RadiusMd))
                    .background(NightSession.Cover),
            )
            if (badge != null) {
                Text(
                    text = badge,
                    fontSize = 11.sp * scale.font,
                    fontWeight = FontWeight.Bold,
                    color = NightSession.OnAccent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp * scale.spacing)
                        .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            if (pill != null) {
                CoverPill(
                    text = pill,
                    modifier = Modifier.align(Alignment.BottomStart).padding(7.dp * scale.spacing),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp * scale.spacing))
        Text(
            text = title,
            fontSize = 12.sp * scale.font,
            lineHeight = 15.6.sp * scale.font,
            fontWeight = FontWeight.Medium,
            color = NightSession.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = 10.sp * scale.font,
                color = NightSession.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Manga search/browse results, which all arrive as [SearchResultDto]. */
@Composable
fun MangaGridCard(result: SearchResultDto, onClick: () -> Unit) {
    val sourceCount = result.alternates?.takeIf { it.isNotEmpty() }?.let { it.size + 1 }
    MediaGridCard(
        title = result.title,
        coverUrl = result.cover_url,
        subtitle = if (sourceCount == null) result.source_name else null,
        onClick = onClick,
        pill = sourceCount?.let { "$it sources" },
    )
}
