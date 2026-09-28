package com.mymonstervr.kawabi.tv.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography

// The design mockups spec Space Grotesk (display) + Manrope (body), but the phone app's
// own KawabiTheme.kt deliberately settled on FontFamily.Default (resolves to Roboto) instead
// of bundling/downloading those -- same call here, for the same reason: no extra asset/
// network-font-provider weight for a look that's carried by weight/letter-spacing anyway.
private val Display = FontFamily.Default
private val Body = FontFamily.Default

fun tvTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 56.sp),
        headlineLarge = base.headlineLarge.copy(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 44.sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 32.sp),
        titleLarge = base.titleLarge.copy(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = base.titleMedium.copy(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
        bodyLarge = base.bodyLarge.copy(fontFamily = Body, fontSize = 19.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = Body, fontSize = 16.sp),
        labelLarge = base.labelLarge.copy(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.5.sp),
        labelMedium = base.labelMedium.copy(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 13.sp),
    )
}
