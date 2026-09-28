package com.mymonstervr.kawabi.tv.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow

/**
 * One shared "focus treatment" recipe every focusable element in the app reuses (cards, rows,
 * buttons, chips) -- per the design pass, this consistency is what reads as polished from 10
 * feet away instead of a stretched phone UI. Values match the approved mockups: ~1.05-1.08x
 * scale (bigger for hero cards, smaller for buttons/chips), a 3dp accent border, and an accent
 * glow (`0 0 28px rgba(226,152,79,0.55)` in the mockup's CSS).
 */
object TvFocus {
    const val CardScale = 1.08f
    const val ButtonScale = 1.04f

    @Composable
    fun border(shape: RoundedCornerShape = RoundedCornerShape(12.dp)) =
        ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, TvColors.Accent), shape = shape),
        )

    @Composable
    fun glow() =
        ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = TvColors.Accent, elevation = 12.dp),
        )
}
