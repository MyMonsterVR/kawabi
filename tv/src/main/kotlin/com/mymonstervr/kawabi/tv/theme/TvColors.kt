package com.mymonstervr.kawabi.tv.theme

import androidx.compose.ui.graphics.Color

/**
 * Exact palette from the approved Design Artifact (Main/Pairing/Player .dc.html mockups) --
 * same "Night Session" family as the phone app's KawabiTheme.kt, kept as its own object here
 * since the TV app is a separate module/process with its own Compose-for-TV MaterialTheme.
 */
object TvColors {
    val Background = Color(0xFF000000)
    // Was a warm brown (0xFF1C1611) complementing the orange accent -- shifted cool/purple
    // to match, same lightness so the hero/detail gradients read the same otherwise.
    val BackgroundGradientTop = Color(0xFF181120)

    val Text = Color(0xFFEFE9E2)
    val TextSecondary = Color(0xFFC9C2B8)
    val TextDim = Color(0xFF82796D)

    // Purple instead of the original orange, per explicit request -- same lightness/
    // saturation weight as the old 0xFFE2984F so focus glow/contrast behave the same.
    val Accent = Color(0xFFB48CE8)
    val OnAccent = Color(0xFF1B1025)

    val Hairline = Color(0xFF211F1A)
    val Surface = Color(0xFF26221C)
    val SurfaceDim = Color(0xFF5A5147)
    val ChipHover = Color(0x29B48CE8) // rgba(180,140,232,0.16), same alpha as before
    val SurfaceOverlay = Color(0x14EFE9E2) // rgba(239,233,226,0.08)

    val QrLight = Color(0xFFEFE9E2)
    val QrDark = Color(0xFF1A1206)
}
