package com.mymonstervr.kawabi.tv.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val KawabiTvColorScheme: ColorScheme = darkColorScheme(
    primary = TvColors.Accent,
    onPrimary = TvColors.OnAccent,
    secondary = TvColors.Accent,
    onSecondary = TvColors.OnAccent,
    background = TvColors.Background,
    onBackground = TvColors.Text,
    surface = TvColors.Surface,
    onSurface = TvColors.Text,
    surfaceVariant = TvColors.SurfaceOverlay,
    onSurfaceVariant = TvColors.TextSecondary,
    border = TvColors.Hairline,
    error = androidx.compose.ui.graphics.Color(0xFFCF6679),
)

@Composable
fun KawabiTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KawabiTvColorScheme,
        typography = tvTypography(),
        content = content,
    )
}
