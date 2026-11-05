package com.mymonstervr.kawabi.app.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGridCard(
    title: String,
    coverUrl: String?,
    subtitle: String?,
    onClick: () -> Unit,
    source: String? = null,
    badge: String? = null,
    pill: String? = null,
    blurCover: Boolean = false,
) {
    val scale = LocalKawabiScale.current
    var revealed by remember { mutableStateOf(false) }
    val hidden = blurCover && !revealed
    Column(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = if (blurCover) ({ revealed = !revealed }) else null,
        ),
    ) {
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
                    .background(NightSession.Cover)
                    .then(
                        if (!hidden) Modifier
                        else if (Build.VERSION.SDK_INT >= 31) Modifier.blur(28.dp)
                        else Modifier.alpha(0.1f),
                    ),
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
fun MangaGridCard(result: SearchResultDto, onClick: () -> Unit, blurCover: Boolean = false) {
    val sourceCount = result.alternates?.takeIf { it.isNotEmpty() }?.let { it.size + 1 }
    MediaGridCard(
        title = result.title,
        coverUrl = result.cover_url,
        subtitle = if (sourceCount == null) result.source_name else null,
        onClick = onClick,
        pill = sourceCount?.let { "$it sources" },
        blurCover = blurCover,
    )
}
