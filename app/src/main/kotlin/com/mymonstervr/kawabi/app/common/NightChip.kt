package com.mymonstervr.kawabi.app.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession

@Composable
fun NightChip(label: String, selected: Boolean = false, onClick: () -> Unit) {
    val scale = LocalKawabiScale.current
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(100)
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 36.dp * scale.spacing)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f))
            .border(1.dp, if (selected) accent else Color.White.copy(alpha = 0.1f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp * scale.spacing),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp * scale.font,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else NightSession.TextDim,
            maxLines = 1,
        )
    }
}
