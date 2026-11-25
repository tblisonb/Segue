package com.tlisonbee.segue.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.tlisonbee.segue.R
import com.tlisonbee.segue.library.LibraryData
import kotlinx.coroutines.delay

/** The position in the current track, updated twice a second while [active]. */
@UnstableApi
@Composable
private fun rememberPosition(vm: PlayerViewModel, active: Boolean): Long {
    var position by remember { mutableLongStateOf(vm.positionMs()) }
    LaunchedEffect(active) {
        while (true) {
            position = vm.positionMs()
            if (!active) break
            delay(500)
        }
    }
    return position
}

const val EXPAND_MS = 350

/** What the mini player and Now Playing need to share their container and cover while one turns into the other. */
@OptIn(ExperimentalSharedTransitionApi::class)
class Transition(val shared: SharedTransitionScope, val visibility: AnimatedVisibilityScope) {
    @Composable
    fun Modifier.container(): Modifier = with(shared) {
        sharedBounds(
            rememberSharedContentState("player"), visibility,
            boundsTransform = { _, _ -> tween(EXPAND_MS, easing = FastOutSlowInEasing) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }

    @Composable
    fun Modifier.cover(): Modifier = with(shared) {
        sharedElement(rememberSharedContentState("cover"), visibility, boundsTransform = { _, _ -> tween(EXPAND_MS, easing = FastOutSlowInEasing) })
    }
}

/** The bar above the bottom of the screens: what's playing, play/pause and next. */
@UnstableApi
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MiniPlayer(ui: Ui, state: PlaybackState, data: LibraryData, transition: Transition) = with(transition) {
    val vm = ui.vm
    val item = state.current ?: return@with
    val track = vm.track(item.mediaId)
    val position = rememberPosition(vm, state.isPlaying)
    // Swiping up opens Now Playing, as tapping does: once the finger has gone far enough up, or on a flick.
    val openAt = with(LocalDensity.current) { 40.dp.toPx() }
    var dragged by remember { mutableFloatStateOf(0f) }
    val swipeUp = rememberDraggableState { delta ->
        dragged += delta
        if (dragged < -openAt && !vm.nowPlayingOpen) vm.nowPlayingOpen = true
    }
    Surface(
        Modifier.container().draggable(
            swipeUp, Orientation.Vertical,
            onDragStarted = { dragged = 0f },
            onDragStopped = { velocity -> if (velocity < -1000f) vm.nowPlayingOpen = true },
        ),
        tonalElevation = 3.dp,
    ) {
        // Kept at the bottom while the container is still taller than the bar.
        Column(Modifier.wrapContentHeight(Alignment.Bottom).navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                drawStopIndicator = {},
            )
            Row(
                Modifier.fillMaxWidth().clickable { vm.nowPlayingOpen = true }.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Cover(ui.covers, track?.cover, 44.dp, Modifier.cover())
                Column(Modifier.weight(1f)) {
                    Text(item.mediaMetadata.title?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.mediaMetadata.artist?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconAction(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play, if (state.isPlaying) "Pause" else "Play") { vm.togglePlay() }
                IconAction(R.drawable.ic_next, "Next") { vm.next() }
            }
        }
    }
}

@UnstableApi
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NowPlayingScreen(ui: Ui, state: PlaybackState, data: LibraryData, transition: Transition) = with(transition) {
    val vm = ui.vm
    val item = state.current
    val track = item?.let { vm.track(it.mediaId) }
    // Swiping down anywhere (the cover, the titles) pulls the screen down with the finger; far or
    // fast enough and it closes into the mini player, else it springs back. Not over the queue,
    // whose list scrolls.
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val closeAt = with(LocalDensity.current) { 120.dp.toPx() }
    val pullDown = { delta: Float -> scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }; Unit }
    val letGo = { velocity: Float ->
        if (drag.value > closeAt || velocity > 1500f) vm.nowPlayingOpen = false else scope.launch { drag.animateTo(0f) }
        Unit
    }
    val dragState = rememberDraggableState { delta -> pullDown(delta) }
    // The cover slides sideways with the finger; far or fast enough and it changes track.
    val slide = remember { Animatable(0f) }
    val playingId by rememberUpdatedState(item?.mediaId)
    Surface(
        Modifier
            // Before the shared container, so closing starts from where the finger left it.
            .offset { IntOffset(0, drag.value.roundToInt()) }
            .container()
            .fillMaxSize()
            .draggable(
                dragState, Orientation.Vertical, enabled = !vm.queueOpen,
                onDragStopped = { velocity -> letGo(velocity) },
            ),
    ) {
        Column(Modifier.safeDrawingPadding().fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(R.drawable.ic_collapse, "Close") { if (vm.queueOpen) vm.queueOpen = false else vm.nowPlayingOpen = false }
                Text(if (vm.queueOpen) "Queue" else "Now playing", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { vm.queueOpen = !vm.queueOpen }) {
                    androidx.compose.material3.Icon(
                        androidx.compose.ui.res.painterResource(R.drawable.ic_queue), "Queue",
                        tint = if (vm.queueOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (item == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Missing("Nothing is playing.") }
                return@Column
            }
            if (vm.queueOpen) {
                QueueList(ui, state, data, Modifier.weight(1f))
            } else {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val width = constraints.maxWidth.toFloat()
                    val coverSize = (min(maxWidth, maxHeight) - 48.dp).coerceAtLeast(0.dp)
                    Box(
                        Modifier.fillMaxSize().coverSwipe(
                            sideways = { delta ->
                                // Heavy going where there's no track to go to.
                                val forward = slide.value + delta < 0
                                val give = if (vm.canSkip(forward)) 1f else 0.25f
                                scope.launch { slide.snapTo(slide.value + delta * give) }
                            },
                            sidewaysEnd = { velocity ->
                                val forward = slide.value < 0
                                val far = abs(slide.value) > width / 4
                                val flung = abs(velocity) > 1200f && (velocity < 0) == forward
                                scope.launch {
                                    if ((far || flung) && slide.value != 0f && vm.canSkip(forward)) {
                                        val out = if (forward) -width else width
                                        slide.animateTo(out, tween(150))
                                        val before = playingId
                                        vm.skip(forward)
                                        // Comes back in from the other side once the new track is the one showing.
                                        withTimeoutOrNull(600) { snapshotFlow { playingId }.first { it != before } }
                                        slide.snapTo(-out)
                                        slide.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                                    } else {
                                        slide.animateTo(0f)
                                    }
                                }
                            },
                            down = pullDown,
                            downEnd = letGo,
                        ).padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Cover(
                            ui.covers, track?.cover, coverSize,
                            Modifier
                                .graphicsLayer {
                                    translationX = slide.value
                                    alpha = 1f - (abs(slide.value) / width).coerceIn(0f, 1f) * 0.6f
                                }
                                .cover(),
                            corner = 12.dp,
                        )
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Text(item.mediaMetadata.title?.toString() ?: "", style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        item.mediaMetadata.artist?.toString() ?: "",
                        Modifier.clickable(enabled = track != null && !track.isTidal) { track?.let { vm.navigate(Route.ArtistPage(it.albumArtistOrArtist)) } },
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(item.mediaMetadata.albumTitle?.toString(), track?.year?.takeIf { it.isNotEmpty() }).joinToString(" · "),
                        Modifier.clickable(enabled = track != null && !track.isTidal) { track?.let { vm.navigate(Route.AlbumPage(it.albumKey)) } },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    LastFmPlays(vm, track, item.mediaMetadata.artist?.toString(), item.mediaMetadata.title?.toString())
                }
            }
            Seek(vm, state)
            Controls(vm, state)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@UnstableApi
@Composable
private fun Seek(vm: PlayerViewModel, state: PlaybackState) {
    val position = rememberPosition(vm, state.isPlaying)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = state.durationMs.coerceAtLeast(1)
    val shown = if (dragging) (dragValue * duration).toLong() else position
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Slider(
            value = if (dragging) dragValue else (position.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { vm.seekTo((dragValue * duration).toLong()); dragging = false },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration(shown).ifEmpty { "0:00" }, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(state.durationMs), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@UnstableApi
@Composable
private fun Controls(vm: PlayerViewModel, state: PlaybackState) {
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.toggleShuffle() }) {
            androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_shuffle), if (state.shuffle) "Shuffle on" else "Shuffle off", tint = if (state.shuffle) on else off)
        }
        IconButton(onClick = { vm.previous() }, Modifier.size(56.dp)) { Icon(R.drawable.ic_previous, "Previous", Modifier.size(36.dp)) }
        FilledIconButton(onClick = { vm.togglePlay() }, Modifier.size(72.dp), colors = IconButtonDefaults.filledIconButtonColors()) {
            Icon(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play, if (state.isPlaying) "Pause" else "Play", Modifier.size(40.dp))
        }
        IconButton(onClick = { vm.next() }, Modifier.size(56.dp)) { Icon(R.drawable.ic_next, "Next", Modifier.size(36.dp)) }
        IconButton(onClick = { vm.cycleRepeat() }) {
            val (icon, label) = when (state.repeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one to "Repeat this song"
                Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat to "Repeat all"
                else -> R.drawable.ic_repeat to "Repeat off"
            }
            androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(icon), label, tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) on else off)
        }
    }
}

@UnstableApi
@Composable
private fun QueueList(ui: Ui, state: PlaybackState, data: LibraryData, modifier: Modifier) {
    val vm = ui.vm
    val current = state.current ?: return
    LazyColumn(modifier.fillMaxWidth()) {
        item { SectionHeader("Now playing") }
        item {
            val t = vm.track(current.mediaId)
            TrackRow(
                current.mediaMetadata.title?.toString() ?: "", current.mediaMetadata.artist?.toString() ?: "",
                lead = { Cover(ui.covers, t?.cover, 44.dp) }, durationMs = t?.durationMs, playing = true, onClick = {}, menu = emptyList(),
            )
        }
        if (state.upNext.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionHeader("Up next · ${state.upNext.size}") }
                    TextButton(onClick = { vm.clearUpNext() }, Modifier.padding(end = 8.dp)) { Text("Clear") }
                }
            }
        }
        // Long queues show their first few hundred; the rest still plays.
        items(state.upNext.take(500), key = { "q${it.index}:${it.item.mediaId}" }) { e ->
            val t = vm.track(e.item.mediaId)
            TrackRow(
                e.item.mediaMetadata.title?.toString() ?: "",
                listOfNotNull("Queued".takeIf { e.queued }, e.item.mediaMetadata.artist?.toString()).joinToString(" · "),
                lead = { Cover(ui.covers, t?.cover, 44.dp) }, durationMs = t?.durationMs, playing = false, remote = t?.isRemote == true,
                onClick = { vm.jumpTo(e.index) },
                menu = listOf("Play next" to { vm.moveToNext(e.index) }, "Remove from queue" to { vm.removeFromQueue(e.index); Unit }),
            )
        }
    }
}

/**
 * Swipes on the cover, sideways ([sideways], left being next) or down ([down], to close). A swipe
 * keeps the one direction it starts in: once the finger has moved a little, whichever way it moved
 * more decides, and from then on the other way's movement is ignored. So a slanted swipe down
 * never changes track, and a slanted swipe sideways never closes the screen. The cover takes the
 * whole gesture, so the screen's own swipe down doesn't also see it. Velocities are pixels a second.
 */
@Composable
private fun Modifier.coverSwipe(
    sideways: (Float) -> Unit,
    sidewaysEnd: (Float) -> Unit,
    down: (Float) -> Unit,
    downEnd: (Float) -> Unit,
): Modifier {
    val onSideways by rememberUpdatedState(sideways)
    val onSidewaysEnd by rememberUpdatedState(sidewaysEnd)
    val onDown by rememberUpdatedState(down)
    val onDownEnd by rememberUpdatedState(downEnd)
    return pointerInput(Unit) {
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false)
            // A little past the usual slop, so the direction is a fair reading of the swipe.
            val decideAt = viewConfiguration.touchSlop * 1.5f
            val tracker = VelocityTracker()
            tracker.addPosition(first.uptimeMillis, first.position)
            var moved = Offset.Zero
            var horizontal: Boolean? = null
            var velocity = Velocity.Zero
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == first.id } ?: break
                if (!change.pressed) {
                    velocity = tracker.calculateVelocity()
                    break
                }
                tracker.addPosition(change.uptimeMillis, change.position)
                val delta = change.positionChange()
                change.consume()
                if (horizontal == null) {
                    moved += delta
                    if (moved.getDistance() < decideAt) continue
                    horizontal = abs(moved.x) > abs(moved.y)
                    // What moved while deciding counts too.
                    if (horizontal) onSideways(moved.x) else onDown(moved.y)
                } else if (horizontal) {
                    onSideways(delta.x)
                } else {
                    onDown(delta.y)
                }
            }
            when (horizontal) {
                true -> onSidewaysEnd(velocity.x)
                false -> onDownEnd(velocity.y)
                null -> {}
            }
        }
    }
}
