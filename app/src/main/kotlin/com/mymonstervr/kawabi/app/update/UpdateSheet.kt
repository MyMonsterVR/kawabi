package com.mymonstervr.kawabi.app.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.common.glass
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession
import com.mymonstervr.kawabi.data.update.AppUpdateInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    info: AppUpdateInfo,
    currentVersion: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    val scale = LocalKawabiScale.current
    val accent = MaterialTheme.colorScheme.primary
    val notes = remember(info.info) { parseReleaseNotes(info.info) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = NightSession.Cover,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp * scale.spacing),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(accent.over(Color.White, 0.35f), accent))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = NightSession.OnAccent, modifier = Modifier.size(30.dp))
                }
                Column {
                    Text(
                        text = "Update available",
                        fontSize = 22.sp * scale.font,
                        fontWeight = FontWeight.Bold,
                        color = NightSession.Text,
                    )
                    Text(
                        text = "Version ${info.version} · you have $currentVersion",
                        fontSize = 13.sp * scale.font,
                        color = NightSession.TextDim,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            if (notes.isNotEmpty()) {
                Text(
                    text = "WHAT'S NEW",
                    fontSize = 11.sp * scale.font,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.3.sp,
                    color = NightSession.TextDim,
                    modifier = Modifier.padding(top = 20.dp * scale.spacing, bottom = 8.dp * scale.spacing),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    notes.forEach { note ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(text = "•", fontSize = 14.sp * scale.font, color = accent)
                            Text(text = note, fontSize = 14.sp * scale.font, lineHeight = 20.sp * scale.font, color = NightSession.Text.copy(alpha = 0.85f))
                        }
                    }
                }
            }

            Text(
                text = "Downloads in the background. You can keep using the app.",
                fontSize = 12.sp * scale.font,
                color = NightSession.TextDim,
                modifier = Modifier
                    .padding(top = 14.dp * scale.spacing)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.035f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp * scale.spacing),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), RoundedCornerShape(16.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "Later", fontSize = 15.sp * scale.font, color = NightSession.TextDim)
                }
                GradientActionButton(label = "Download update", onClick = onDownload, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun GradientActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    corner: Dp = 16.dp,
    fontSize: Float = 16f,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(accent.over(Color.White, 0.12f), accent)))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold, color = NightSession.OnAccent)
    }
}

private fun Color.over(other: Color, fraction: Float): Color = Color(
    red = red + (other.red - red) * fraction,
    green = green + (other.green - green) * fraction,
    blue = blue + (other.blue - blue) * fraction,
    alpha = alpha,
)

private fun parseReleaseNotes(raw: String): List<String> =
    raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { it.removePrefix("-").removePrefix("*").removePrefix("•").trim() }
        .filter { it.isNotEmpty() }
        .take(6)
        .toList()
