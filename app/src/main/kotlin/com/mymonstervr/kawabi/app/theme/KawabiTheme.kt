package com.mymonstervr.kawabi.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.data.settings.AppPreferences
import org.koin.compose.koinInject

/**
 * Design tokens ported directly from the locked "Night Session" interactive spec
 * (https://claude.ai/code/artifact/e8b3e155-4a16-4bbe-b9cd-ce080dda7754) -- values are
 * copied verbatim from that HTML's `.screen { --c-* }` custom properties, not
 * reinterpreted. Dark-only by design (locked decision, no light theme).
 */
object NightSession {
    val Background = Color(0xFF000000)
    val Text = Color(0xFFEFE9E2)
    val TextDim = Color(0xFF82796D)
    val OnAccent = Color(0xFF1A1206)
    val Read = Color(0xFF5F7350)
    val Chip = Color(0xFF151513)
    val Cover = Color(0xFF26221C)
    val Hairline = Color(0xFF211F1A)
    val Danger = Color(0xFFC0392B)

    val RadiusSm = 8.dp
    val RadiusMd = 12.dp

    // Swappable, data-driven accent list (locked decision) -- Ember is the default.
    // Appearance/accent picker UI is deferred (PLAN.md step 8), so only the default is
    // wired up today, but nothing about this list is hardcoded to Ember specifically.
    data class Accent(val label: String, val color: Color)
    val Accents = listOf(
        Accent("Ember", Color(0xFFE2984F)),
        Accent("Rust", Color(0xFFD9633D)),
        Accent("Moss", Color(0xFF7FAE8A)),
        Accent("Signal Blue", Color(0xFF3D8BD9)),
        Accent("Violet", Color(0xFFB07DE2)),
        Accent("Rose", Color(0xFFD9527A)),
    )
    val DefaultAccent = Accents.first().color
}

private val kawabiTypography = Typography().let { base ->
    // Spec uses the platform system font stack throughout (-apple-system/Segoe UI/Roboto),
    // not a custom typeface -- Compose's FontFamily.Default already resolves to Roboto on
    // Android, so this is a faithful port, not a placeholder.
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontWeight = FontWeight.Medium),
    )
}

@Composable
fun KawabiTheme(content: @Composable () -> Unit) {
    val preferences = koinInject<AppPreferences>()
    val accentIndex by preferences.accentIndex.collectAsState(initial = 0)
    val accent = NightSession.Accents.getOrElse(accentIndex) { NightSession.Accents.first() }.color

    val colorScheme = darkColorScheme(
        background = NightSession.Background,
        onBackground = NightSession.Text,
        surface = NightSession.Chip,
        onSurface = NightSession.Text,
        surfaceVariant = NightSession.Cover,
        onSurfaceVariant = NightSession.TextDim,
        primary = accent,
        onPrimary = NightSession.OnAccent,
        secondary = accent,
        onSecondary = NightSession.OnAccent,
        tertiary = NightSession.Read,
        outline = NightSession.Hairline,
        outlineVariant = NightSession.Hairline,
        error = NightSession.Danger,
        onError = Color.White,
    )
    MaterialTheme(colorScheme = colorScheme, typography = kawabiTypography, content = content)
}
