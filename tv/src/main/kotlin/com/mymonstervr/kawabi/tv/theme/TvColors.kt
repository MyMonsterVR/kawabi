package com.mymonstervr.kawabi.tv.theme

import androidx.compose.ui.graphics.Color

/**
 * Exact palette from the approved Design Artifact (Main/Pairing/Player .dc.html mockups) --
 * same "Night Session" family as the phone app's KawabiTheme.kt, kept as its own object here
 * since the TV app is a separate module/process with its own Compose-for-TV MaterialTheme.
 */
object TvColors {
    val Background = Color(0xFF000000)
    val BackgroundGradientTop = Color(0xFF1C1611)

    val Text = Color(0xFFEFE9E2)
    val TextSecondary = Color(0xFFC9C2B8)
    val TextDim = Color(0xFF82796D)

    val Accent = Color(0xFFE2984F)
    val OnAccent = Color(0xFF1A1206)

    val Hairline = Color(0xFF211F1A)
    val Surface = Color(0xFF26221C)
    val SurfaceDim = Color(0xFF5A5147)
    val ChipHover = Color(0x29E2984F) // rgba(226,152,79,0.16)
    val SurfaceOverlay = Color(0x14EFE9E2) // rgba(239,233,226,0.08)

    val QrLight = Color(0xFFEFE9E2)
    val QrDark = Color(0xFF1A1206)
}
