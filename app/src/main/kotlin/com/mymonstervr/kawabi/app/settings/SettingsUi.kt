package com.mymonstervr.kawabi.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.app.common.SegmentedTabs
import com.mymonstervr.kawabi.app.common.accentSoft
import com.mymonstervr.kawabi.app.common.glass
import com.mymonstervr.kawabi.app.theme.LocalKawabiScale
import com.mymonstervr.kawabi.app.theme.NightSession

@Composable
internal fun SettingsSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    val scale = LocalKawabiScale.current
    Column(modifier = Modifier.padding(top = 22.dp * scale.spacing)) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp * scale.font,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.3.sp,
            color = NightSession.TextDim,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp * scale.spacing),
        )
        Column(modifier = Modifier.fillMaxWidth().glass(), content = content)
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(color = NightSession.Hairline)
}

@Composable
internal fun IconTile(letter: String, modifier: Modifier = Modifier, filled: Boolean = false) {
    val scale = LocalKawabiScale.current
    Box(
        modifier = modifier
            .size(34.dp * scale.spacing)
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) MaterialTheme.colorScheme.primary else accentSoft(0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            fontSize = 13.sp * scale.font,
            fontWeight = FontWeight.Bold,
            color = if (filled) NightSession.OnAccent else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun Chevron(tint: Color = NightSession.TextDim) {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
}

@Composable
internal fun SettingsNavRow(
    letter: String,
    label: String,
    onClick: () -> Unit,
    note: String? = null,
    noteColor: Color? = null,
) {
    val scale = LocalKawabiScale.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp * scale.spacing)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp * scale.spacing, vertical = 8.dp * scale.spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(letter)
        Text(
            text = label,
            fontSize = 15.sp * scale.font,
            color = NightSession.Text,
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp * scale.spacing),
        )
        if (note != null) {
            Text(text = note, fontSize = 12.sp * scale.font, color = noteColor ?: NightSession.TextDim, modifier = Modifier.padding(end = 4.dp))
        }
        Chevron()
    }
}

@Composable
internal fun SettingsToggleRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val scale = LocalKawabiScale.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp * scale.spacing)
            .padding(horizontal = 16.dp * scale.spacing, vertical = 8.dp * scale.spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, fontSize = 15.sp * scale.font, color = NightSession.Text)
            if (subtitle != null) {
                Text(text = subtitle, fontSize = 12.sp * scale.font, color = NightSession.TextDim, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NightSession.OnAccent,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = NightSession.TextDim,
                uncheckedTrackColor = NightSession.Chip,
                uncheckedBorderColor = NightSession.Hairline,
            ),
        )
    }
}

@Composable
internal fun SettingsSliderRow(
    title: String,
    readout: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    subtitle: String? = null,
) {
    val scale = LocalKawabiScale.current
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp * scale.spacing, end = 16.dp * scale.spacing, top = 12.dp * scale.spacing, bottom = 4.dp * scale.spacing)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, fontSize = 14.sp * scale.font, color = NightSession.Text, modifier = Modifier.weight(1f))
            Text(text = readout, fontSize = 14.sp * scale.font, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        }
        if (subtitle != null) {
            Text(text = subtitle, fontSize = 12.sp * scale.font, color = NightSession.TextDim, modifier = Modifier.padding(top = 2.dp))
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = NightSession.Chip,
            ),
        )
    }
}

@Composable
internal fun SettingsSegmentRow(title: String, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val scale = LocalKawabiScale.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp * scale.spacing, vertical = 12.dp * scale.spacing)) {
        Text(text = title, fontSize = 14.sp * scale.font, color = NightSession.Text, modifier = Modifier.padding(bottom = 8.dp * scale.spacing))
        SegmentedTabs(options = options, selectedIndex = selectedIndex, onSelect = onSelect, modifier = Modifier.fillMaxWidth())
    }
}
