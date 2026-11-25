package com.tlisonbee.segue.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * What a list entry files under on the scrollbar, from its sort key ([com.tlisonbee.segue.library.sortKey]
 * or [com.tlisonbee.segue.library.fold]: lowercase, no accents, "The" already gone where the list
 * ignores it): its first letter, or with [two] its first two ("Sa"). Digits and punctuation go
 * under "#"; a second character that isn't a letter or digit (the space in "S Club") is left off.
 */
fun indexLetter(key: String?, two: Boolean = false): String? {
    val c = key?.firstOrNull() ?: return null
    if (!c.isLetter()) return "#"
    val second = key.getOrNull(1)?.takeIf { two && it.isLetterOrDigit() } ?: return c.uppercase()
    return c.uppercase() + second
}

/**
 * Lists with more entries than this show two letters on the scrollbar (the setting,
 * [com.tlisonbee.segue.PlayerSettings.twoLettersFrom]).
 */
val LocalTwoLettersFrom = compositionLocalOf { 1000 }

/** What the scroller needs from a list or a grid. */
private class Scrolled(
    val first: () -> Int,
    /** How far through the first visible item, 0 to 1. */
    val firstPart: () -> Float,
    val visible: () -> Int,
    val total: () -> Int,
    val scrollTo: suspend (Int) -> Unit,
)

/**
 * [content] (a lazy list using [state]) with a scrollbar on the right, always shown when the list
 * doesn't fit, in a narrow lane the list leaves free (so it doesn't cover the rows' ⋮ buttons).
 * Dragging its thumb jumps through the list, showing the letter (or two, on long lists:
 * [LocalTwoLettersFrom]) of the item at the top. [label] gives an item's sort key by the lazy
 * list's item index, headers included (null for those, or where letters mean nothing).
 */
@Composable
fun FastScrollBox(state: LazyListState, label: (Int) -> String?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val scrolled = remember(state) {
        Scrolled(
            first = { state.firstVisibleItemIndex },
            firstPart = {
                val size = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
                if (size > 0) (state.firstVisibleItemScrollOffset.toFloat() / size).coerceIn(0f, 1f) else 0f
            },
            visible = { state.layoutInfo.visibleItemsInfo.size },
            total = { state.layoutInfo.totalItemsCount },
            scrollTo = { state.scrollToItem(it) },
        )
    }
    FastScrollBox(scrolled, label, modifier, content)
}

/** [FastScrollBox] for a grid: the album covers. */
@Composable
fun FastScrollBox(state: LazyGridState, label: (Int) -> String?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val scrolled = remember(state) {
        Scrolled(
            first = { state.firstVisibleItemIndex },
            // A row holds several items; how far through it is close enough.
            firstPart = { 0f },
            visible = { state.layoutInfo.visibleItemsInfo.size },
            total = { state.layoutInfo.totalItemsCount },
            scrollTo = { state.scrollToItem(it) },
        )
    }
    FastScrollBox(scrolled, label, modifier, content)
}

private val THUMB_HEIGHT = 56.dp

/** The strip on the right the list leaves free for the scrollbar, so the bar never sits on a row. */
private val LANE = 16.dp

/** How wide the thumb is to the touch: a little into the rows, to be easy to catch. */
private val TOUCH_WIDTH = 32.dp

@Composable
private fun FastScrollBox(s: Scrolled, label: (Int) -> String?, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize()) {
        // The lane is kept whether or not the list is long enough for a bar, so rows don't shift when it is.
        Box(Modifier.fillMaxSize().padding(end = LANE), content = content)
        val currentLabel by rememberUpdatedState(label)
        // Always there, as long as the list doesn't fit on the screen.
        val scrollable by remember { derivedStateOf { s.visible() > 0 && s.total() > s.visible() } }
        if (!scrollable) return@Box

        val density = LocalDensity.current
        val thumbPx = with(density) { THUMB_HEIGHT.toPx() }
        var trackPx by remember { mutableIntStateOf(0) }
        val travel = (trackPx - thumbPx).coerceAtLeast(1f)

        var dragging by remember { mutableStateOf(false) }
        var dragY by remember { mutableFloatStateOf(0f) }
        var target by remember { mutableIntStateOf(-1) }
        val lastFirst = { (s.total() - s.visible()).coerceAtLeast(1) }

        // Where the list is, 0 to 1; while dragging, where the finger is.
        val listFraction by remember {
            derivedStateOf { ((s.first() + s.firstPart()) / lastFirst()).coerceIn(0f, 1f) }
        }
        val thumbY = if (dragging) dragY else listFraction * travel

        // Jumps follow the finger, skipping any it has already moved past.
        LaunchedEffect(s) {
            snapshotFlow { target }.collect { if (it >= 0) s.scrollTo(it) }
        }

        val dragState = rememberDraggableState { delta ->
            dragY = (dragY + delta).coerceIn(0f, travel)
            target = ((dragY / travel) * lastFirst()).roundToInt().coerceIn(0, (s.total() - 1).coerceAtLeast(0))
        }

        Box(
            Modifier.align(Alignment.TopEnd).fillMaxHeight().width(TOUCH_WIDTH).onSizeChanged { trackPx = it.height },
        ) {
            Box(
                Modifier
                    .offset { IntOffset(0, thumbY.roundToInt()) }
                    .size(TOUCH_WIDTH, THUMB_HEIGHT)
                    .draggable(
                        dragState, Orientation.Vertical,
                        startDragImmediately = true,
                        onDragStarted = { dragY = listFraction * travel; dragging = true },
                        onDragStopped = { dragging = false; target = -1 },
                    ),
                contentAlignment = Alignment.CenterEnd,
            ) {
                // Centred in the lane.
                Box(
                    Modifier
                        .padding(end = (LANE - 8.dp) / 2 + (if (dragging) 0.dp else 1.dp))
                        .size(if (dragging) 8.dp else 6.dp, THUMB_HEIGHT)
                        .background(
                            if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(4.dp),
                        ),
                )
            }
        }

        if (dragging) {
            // The first labelled item on screen: lists start with a header or two (Play, New playlist…).
            val two = s.total() > LocalTwoLettersFrom.current
            val letter = remember(s.first(), s.visible(), two) {
                (s.first() until s.first() + s.visible().coerceAtLeast(1)).firstNotNullOfOrNull { indexLetter(currentLabel(it), two) }
            }
            if (letter != null) {
                val bubble = 64.dp
                val bubbleY = (thumbY + thumbPx / 2 - with(density) { bubble.toPx() } / 2).coerceAtLeast(0f)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(-with(density) { (TOUCH_WIDTH + 8.dp).roundToPx() }, bubbleY.roundToInt()) }
                        .size(bubble)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 32.dp, bottomEnd = 4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(letter, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}
