package com.mymonstervr.kawabi.app.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession

// Shared pill-chip look used by Library's category filter and Search's
// source row. Non-selectable usages (e.g. tap-to-navigate) just omit
// `selected` and get the unselected look.
@Composable
fun NightChip(label: String, selected: Boolean = false, onClick: () -> Unit) {
    val scale = LocalKawabiScale.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .background(if (selected) MaterialTheme.colorScheme.primary else NightSession.Chip)
            .border(1.dp, if (selected) Color.Transparent else NightSession.Hairline, RoundedCornerShape(100))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp * scale.spacing, vertical = 6.dp * scale.spacing),
    ) {
        Text(
            text = label,
            fontSize = 11.sp * scale.font,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) NightSession.OnAccent else NightSession.TextDim,
        )
    }
}
