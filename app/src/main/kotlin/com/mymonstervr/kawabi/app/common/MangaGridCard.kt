package com.mymonstervr.kawabi.app.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.mymonstervr.kawabi.data.network.dto.SearchResultDto
import com.mymonstervr.kawabi.data.network.resolveCoverUrl

// Shared by Search and Browse — both grids render the same SearchResultDto
// shape (cover + title + source name), and should stay visually identical.
@Composable
fun MangaGridCard(result: SearchResultDto, onClick: () -> Unit) {
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
