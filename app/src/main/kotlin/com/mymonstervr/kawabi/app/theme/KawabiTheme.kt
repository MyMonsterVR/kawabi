package com.mymonstervr.kawabi.app.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mymonstervr.kawabi.data.settings.AppPreferences
import com.mymonstervr.kawabi.data.settings.ThemePalette
import org.koin.compose.koinInject

/**
 * Palette-independent color tokens. NightSession's own color accessors (below) now delegate
 * to LocalKawabiColors.current instead of hardcoded constants -- every one of the ~234
 * existing `NightSession.X` call sites across the app keeps working completely unchanged,
 * only the *source* of the color became swappable per-palette.
 */
data class KawabiColors(
    val background: Color,
    val text: Color,
    val textDim: Color,
    val onAccent: Color,
    val read: Color,
    val chip: Color,
    val cover: Color,
    val hairline: Color,
    val danger: Color,
)

// The original locked "Night Session" spec values (see NightSession's doc comment for
// provenance), ported byte-for-byte -- this is the default palette, unchanged from before
// theme picking existed.
private val NightSessionPalette = KawabiColors(
    background = Color(0xFF000000),
    text = Color(0xFFEFE9E2),
    textDim = Color(0xFF82796D),
    onAccent = Color(0xFF1A1206),
    read = Color(0xFF5F7350),
    chip = Color(0xFF151513),
    cover = Color(0xFF26221C),
    hairline = Color(0xFF211F1A),
    danger = Color(0xFFC0392B),
)

// Catppuccin Mocha (https://catppuccin.com/palette, hex values verified against the official
// site, not guessed). Semantic mapping follows Catppuccin's own elevation convention
// (Base -> Surface0 -> Surface1 for background -> chip -> cover) and UI guidance (Base as
// the "on accent" text color for a colored button). Read reuses Green (already a
// semantically "positive/done" color here), danger uses Red.
private val CatppuccinMochaPalette = KawabiColors(
    background = Color(0xFF1E1E2E), // Base
    text = Color(0xFFCDD6F4), // Text
    textDim = Color(0xFFA6ADC8), // Subtext0
    onAccent = Color(0xFF1E1E2E), // Base
    read = Color(0xFFA6E3A1), // Green
    chip = Color(0xFF313244), // Surface0
    cover = Color(0xFF45475A), // Surface1
    hairline = Color(0xFF313244), // Surface0
    danger = Color(0xFFF38BA8), // Red
)

// Catppuccin's own Mauve -- used as this palette's accent when selected, since the existing
// accent picker (NightSession.Accents/accentIndex, a separate palette-independent mechanism)
// wasn't designed around Catppuccin's own colors and a Night-Session-tuned accent would clash.
private val CatppuccinMochaAccent = Color(0xFFCBA6F7)

private fun paletteFor(theme: ThemePalette): KawabiColors = when (theme) {
    ThemePalette.NIGHT_SESSION -> NightSessionPalette
    ThemePalette.CATPPUCCIN_MOCHA -> CatppuccinMochaPalette
}

private fun accentFor(theme: ThemePalette, pickedAccent: Color): Color = when (theme) {
    ThemePalette.NIGHT_SESSION -> pickedAccent
    ThemePalette.CATPPUCCIN_MOCHA -> CatppuccinMochaAccent
}

// AMOLED true-black: forces background back to pure black regardless of the selected
// palette's own background (Catppuccin Mocha's Base is a dark blue-grey, not black).
private fun KawabiColors.withAmoledOverride(enabled: Boolean): KawabiColors =
    if (enabled) copy(background = Color(0xFF000000)) else this

private fun fromDynamicScheme(scheme: ColorScheme): KawabiColors = KawabiColors(
    background = scheme.background,
    text = scheme.onBackground,
    textDim = scheme.onSurfaceVariant,
    onAccent = scheme.onPrimary,
    read = scheme.tertiary,
    chip = scheme.surface,
    cover = scheme.surfaceVariant,
    hairline = scheme.outlineVariant,
    danger = scheme.error,
)

val LocalKawabiColors = staticCompositionLocalOf { NightSessionPalette }

/**
 * Design tokens ported directly from the locked "Night Session" interactive spec
 * (https://claude.ai/code/artifact/e8b3e155-4a16-4bbe-b9cd-ce080dda7754) -- values are
 * copied verbatim from that HTML's `.screen { --c-* }` custom properties, not
 * reinterpreted. Color accessors now resolve through LocalKawabiColors (see KawabiTheme)
 * so a different palette (or Material You dynamic color, or the AMOLED override) can
 * swap every one of them without touching any of this object's ~234 call sites.
 */
object NightSession {
    val Background: Color @Composable get() = LocalKawabiColors.current.background
    val Text: Color @Composable get() = LocalKawabiColors.current.text
    val TextDim: Color @Composable get() = LocalKawabiColors.current.textDim
    val OnAccent: Color @Composable get() = LocalKawabiColors.current.onAccent
    val Read: Color @Composable get() = LocalKawabiColors.current.read
    val Chip: Color @Composable get() = LocalKawabiColors.current.chip
    val Cover: Color @Composable get() = LocalKawabiColors.current.cover
    val Hairline: Color @Composable get() = LocalKawabiColors.current.hairline
    val Danger: Color @Composable get() = LocalKawabiColors.current.danger

    val RadiusSm = 8.dp
    val RadiusMd = 12.dp

    // Swappable, data-driven accent list (locked decision) -- Ember is the default.
    // Palette-independent: picking Catppuccin Mocha overrides the resolved accent color
    // (see accentFor above) but doesn't change this list or accentIndex itself, so
    // switching back to Night Session restores whichever accent was previously picked.
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
    val themePalette by preferences.themePalette.collectAsState(initial = ThemePalette.NIGHT_SESSION)
    val amoledBlack by preferences.amoledBlack.collectAsState(initial = false)
    val dynamicColorEnabled by preferences.dynamicColor.collectAsState(initial = false)
    val context = LocalContext.current
    val scale = scaleOf(windowSizeClass)

    // dynamicDarkColorScheme requires API 31+ -- the literal SDK_INT check (not just the
    // derived dynamicColorEnabled bool) is what lint's version-gating actually looks for.
    val dynamicScheme = if (dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        null
    }

    val pickedAccent = NightSession.Accents.getOrElse(accentIndex) { NightSession.Accents.first() }.color
    val colors = (dynamicScheme?.let(::fromDynamicScheme) ?: paletteFor(themePalette)).withAmoledOverride(amoledBlack)
    val accent = dynamicScheme?.primary ?: accentFor(themePalette, pickedAccent)
    val onAccent = dynamicScheme?.onPrimary ?: colors.onAccent

    val colorScheme = darkColorScheme(
        background = colors.background,
        onBackground = colors.text,
        surface = colors.chip,
        onSurface = colors.text,
        surfaceVariant = colors.cover,
        onSurfaceVariant = colors.textDim,
        primary = accent,
        onPrimary = onAccent,
        secondary = accent,
        onSecondary = onAccent,
        tertiary = colors.read,
        outline = colors.hairline,
        outlineVariant = colors.hairline,
        error = colors.danger,
        onError = Color.White,
    )
    CompositionLocalProvider(LocalKawabiScale provides scale, LocalKawabiColors provides colors) {
        MaterialTheme(colorScheme = colorScheme, typography = kawabiTypography(scale.font), content = content)
    }
}
