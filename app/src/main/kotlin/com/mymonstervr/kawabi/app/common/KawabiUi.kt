package com.mymonstervr.kawabi.app.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.theme.DisplayFamily
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession

private val GlassFill = Color.White.copy(alpha = 0.045f)
private val GlassStroke = Color.White.copy(alpha = 0.07f)

/** Frosted-card surface used for grouped lists, bars and panels across the redesign. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(18.dp)): Modifier =
    this.clip(shape).background(GlassFill).border(1.dp, GlassStroke, shape)

/** Accent at low alpha, for selected-state fills (segmented tabs, current-row highlight). */
@Composable
fun accentSoft(alpha: Float = 0.18f): Color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)

/** Page title in the redesign: large, bold, tight tracking. */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    val scale = LocalKawabiScale.current
    Text(
        text = text,
        modifier = modifier,
        fontSize = 28.sp * scale.font,
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        color = NightSession.Text,
    )
}

/** Section heading with an optional right-aligned text action ("See all"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scale = LocalKawabiScale.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            fontSize = 18.sp * scale.font,
            fontFamily = DisplayFamily,
            fontWeight = FontWeight.SemiBold,
            color = NightSession.Text,
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                fontSize = 13.sp * scale.font,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .defaultMinSize(minHeight = 40.dp)
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

/** Segmented control: the selected option gets the soft accent fill. Each option is >= 40dp tall. */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    equalWidth: Boolean = true,
) {
    val scale = LocalKawabiScale.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .then(if (equalWidth) Modifier.weight(1f) else Modifier)
                    .height(40.dp * scale.spacing)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) accentSoft() else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp * scale.font,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else NightSession.TextDim,
                )
            }
        }
    }
}

/** Small dark pill laid over a cover corner: "EP 12", "3 sources", "418 ch". */
@Composable
fun CoverPill(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    val scale = LocalKawabiScale.current
    val shape = RoundedCornerShape(if (accent) 100 else 8)
    Text(
        text = text,
        modifier = modifier
            .clip(shape)
            .background(if (accent) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        fontSize = 10.sp * scale.font,
        fontWeight = FontWeight.Bold,
        color = if (accent) NightSession.OnAccent else NightSession.Text,
    )
}

/** Main call to action: accent gradient, 56dp tall, with an optional second line ("12 min left"). */
@Composable
fun PrimaryActionButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    height: Dp = 56.dp,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.92f).compositeOverWhite(0.12f), accent)))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NightSession.OnAccent)
        if (subtitle != null) {
            Text(text = subtitle, fontSize = 12.sp, color = NightSession.OnAccent.copy(alpha = 0.75f))
        }
    }
}

private fun Color.compositeOverWhite(fraction: Float): Color = Color(
    red = red + (1f - red) * fraction,
    green = green + (1f - green) * fraction,
    blue = blue + (1f - blue) * fraction,
    alpha = alpha,
)

/** 44dp round icon-button background, used for back / more / sort buttons. */
@Composable
fun Modifier.roundIconButton(onClick: () -> Unit): Modifier =
    this.height(44.dp)
        .defaultMinSize(minWidth = 44.dp)
        .clip(RoundedCornerShape(100))
        .background(Color.White.copy(alpha = 0.06f))
        .clickable(onClick = onClick)
