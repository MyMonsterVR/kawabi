package com.mymonstervr.kawabi.app.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DEFAULT_MAX_CONTENT_WIDTH = 640.dp

/**
 * Caps content width and centers it on wide screens (tablets) -- a no-op on phone-width
 * screens, since `widthIn(max = ...)` only constrains when the available width actually
 * exceeds it. Without this, text-heavy screens (Settings, Login, manga detail's
 * description) stretch edge-to-edge on a tablet, which reads badly at that line length;
 * a phone never hits the cap so its layout is unaffected.
 */
@Composable
fun ResponsiveContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = DEFAULT_MAX_CONTENT_WIDTH,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth().fillMaxHeight(), content = content)
    }
}
