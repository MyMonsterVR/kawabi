package com.mymonstervr.kawabi.app.common

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Below this item count the list is short enough to just scroll normally -- a thumb
// only earns its keep once flinging through the whole list by hand gets tedious.
private const val FAST_SCROLL_MIN_ITEMS = 30

/**
 * A draggable thumb on the trailing edge of a [LazyListState]'s list, for jumping around
 * a long list (e.g. 300+ chapters) faster than flinging. `itemCount`/`headerOffset`
 * describe the *scrollable* items the thumb should span -- headerOffset is how many
 * non-target items (hero, description, controls, ...) sit before item 0 in the list.
 * The list scrolls live as the thumb is dragged -- no separate label/bubble.
 *
 * The touch target is the *entire* track width/height passed in via [modifier], not just
 * the thin visual thumb -- a tap or drag anywhere in that strip jumps straight to that
 * vertical position (like a real fast-scroller), rather than requiring a precise grab on
 * a 12dp-wide bar.
 */
@Composable
fun FastScroller(
    listState: LazyListState,
    itemCount: Int,
    headerOffset: Int,
    modifier: Modifier = Modifier,
) {
    if (itemCount < FAST_SCROLL_MIN_ITEMS) return
    // Stay hidden while the header (hero/description/controls) is still on screen --
    // once it's fully scrolled past, the viewport is chapters top to bottom and the
    // track's fixed top offset actually lines up with what's visible.
    if (listState.firstVisibleItemIndex < headerOffset) return

    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val currentFraction = if (isDragging) {
        dragFraction
    } else {
        val first = listState.firstVisibleItemIndex - headerOffset
        (first.coerceIn(0, itemCount - 1).toFloat() / (itemCount - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    fun jumpTo(fraction: Float) {
        dragFraction = fraction.coerceIn(0f, 1f)
        val targetIndex = (dragFraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
        scope.launch { listState.scrollToItem(headerOffset + targetIndex) }
    }

    Box(
        modifier = modifier
            .padding(vertical = 8.dp)
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        if (trackHeightPx > 0f) jumpTo(offset.y / trackHeightPx)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                ) { change, _ ->
                    change.consume()
                    if (trackHeightPx > 0f) jumpTo(change.position.y / trackHeightPx)
                }
            },
    ) {
        val thumbOffsetPx = currentFraction * (trackHeightPx - THUMB_HEIGHT_PX).coerceAtLeast(0f)
        Surface(
            shape = RoundedCornerShape(100),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                .size(width = 12.dp, height = with(density) { THUMB_HEIGHT_PX.toDp() }),
        ) {}
    }
}

private val THUMB_HEIGHT_PX = 120f
