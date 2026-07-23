package com.mymonstervr.kawabi.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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

// Screen-size-aware scale, derived once from WindowWidthSizeClass and provided down
// via LocalKawabiScale -- COMPACT (phones, the vast majority of usage) is exactly 1x
// on every axis, a deliberate no-op so this system can never visibly change phone
// rendering. MEDIUM/EXPANDED (tablets) scale font size, spacing/padding, and the
// content-width cap together, superseding the old per-screen widthIn(max=...)
// band-aids that only capped width and left text/touch-targets phone-sized.
data class KawabiScale(val font: Float, val spacing: Float, val maxContentWidth: Dp)

private val CompactScale = KawabiScale(font = 1f, spacing = 1f, maxContentWidth = Dp.Unspecified)
private val MediumScale = KawabiScale(font = 1.1f, spacing = 1.15f, maxContentWidth = 680.dp)
private val ExpandedScale = KawabiScale(font = 1.22f, spacing = 1.3f, maxContentWidth = 860.dp)

val LocalKawabiScale = staticCompositionLocalOf { CompactScale }

private fun scaleOf(windowSizeClass: WindowSizeClass): KawabiScale = when (windowSizeClass.widthSizeClass) {
    WindowWidthSizeClass.Expanded -> ExpandedScale
    WindowWidthSizeClass.Medium -> MediumScale
    else -> CompactScale
}

private fun TextUnit.scaled(factor: Float): TextUnit =
    if (isSp) value.times(factor).sp else this

private fun TextStyle.scaled(factor: Float): TextStyle =
    copy(fontSize = fontSize.scaled(factor), lineHeight = lineHeight.scaled(factor))

// Spec uses the platform system font stack throughout (-apple-system/Segoe UI/Roboto),
// not a custom typeface -- Compose's FontFamily.Default already resolves to Roboto on
// Android, so this is a faithful port, not a placeholder. font=1f (COMPACT) reproduces
// this byte-for-byte; MEDIUM/EXPANDED multiply every style's fontSize/lineHeight.
private fun kawabiTypography(font: Float): Typography {
    val base = Typography().let {
        it.copy(
            titleLarge = it.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
            titleMedium = it.titleMedium.copy(fontWeight = FontWeight.Bold),
            titleSmall = it.titleSmall.copy(fontWeight = FontWeight.Bold),
            labelLarge = it.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            labelMedium = it.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            labelSmall = it.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = it.bodyLarge.copy(fontWeight = FontWeight.Medium),
        )
    }
    if (font == 1f) return base
    return base.copy(
        displayLarge = base.displayLarge.scaled(font),
        displayMedium = base.displayMedium.scaled(font),
        displaySmall = base.displaySmall.scaled(font),
        headlineLarge = base.headlineLarge.scaled(font),
        headlineMedium = base.headlineMedium.scaled(font),
        headlineSmall = base.headlineSmall.scaled(font),
        titleLarge = base.titleLarge.scaled(font),
        titleMedium = base.titleMedium.scaled(font),
        titleSmall = base.titleSmall.scaled(font),
        bodyLarge = base.bodyLarge.scaled(font),
        bodyMedium = base.bodyMedium.scaled(font),
        bodySmall = base.bodySmall.scaled(font),
        labelLarge = base.labelLarge.scaled(font),
        labelMedium = base.labelMedium.scaled(font),
        labelSmall = base.labelSmall.scaled(font),
    )
}

@Composable
fun KawabiTheme(windowSizeClass: WindowSizeClass, content: @Composable () -> Unit) {
    val preferences = koinInject<AppPreferences>()
    val accentIndex by preferences.accentIndex.collectAsState(initial = 0)
    val accent = NightSession.Accents.getOrElse(accentIndex) { NightSession.Accents.first() }.color
    val scale = scaleOf(windowSizeClass)

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
    CompositionLocalProvider(LocalKawabiScale provides scale) {
        MaterialTheme(colorScheme = colorScheme, typography = kawabiTypography(scale.font), content = content)
    }
}
