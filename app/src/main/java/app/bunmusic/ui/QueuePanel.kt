package app.bunmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Haptics
import app.bunmusic.core.Ink
import app.bunmusic.core.SerifFamily
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.QueueMath
import app.bunmusic.data.Track
import app.bunmusic.player.Playback
import app.bunmusic.player.PlayerState
import kotlin.math.roundToInt

/**
 * The queue, in three parts: what just played, what is playing, what comes next.
 *
 * It used to be one flat list with the current row somewhere in the middle, which
 * made "skip back" feel like scrolling a document. Spotify separates the past from
 * the future; so does this. The past is read-only — you cannot drag a song back
 * into history — and the future is draggable, with the running time at the top so
 * you know whether you have time for all of it.
 */
@Composable
fun QueuePanel(
    state: PlayerState,
    onClose: () -> Unit,
    onSaved: (String) -> Unit = {},
) {
    val played = remember(state.queue, state.index) { QueueMath.played(state.queue, state.index) }
    val upcoming = remember(state.queue, state.index) { QueueMath.upcoming(state.queue, state.index) }
    val totalMs = remember(state.queue) { QueueMath.totalMs(state.queue) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Void.copy(alpha = 0.97f))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Queue", color = Ink.Text, fontSize = 18.sp, fontFamily = SerifFamily)
                Text(
                    "${QueueMath.positionLabel(state.index, state.queue.size)} · ${fmtDuration(totalMs)} total",
                    color = Ink.Faint,
                    fontSize = 11.sp,
                    fontFamily = SoraFamily,
                )
            }
            GhostBtn("Save", G.Plus) {
                val name = Playback.saveQueueAsPlaylist()
                if (name.isNotBlank()) onSaved(name)
            }
            Spacer(Modifier.width(8.dp))
            IconBtn(G.Close, Ink.Text, 38.dp, 16.dp, onClose)
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (state.track != null) {
                item { QueueHeading("Now playing", null) }
                item {
                    TrackRow(
                        track = state.track,
                        playing = state.isPlaying,
                        isCurrent = true,
                        onPlay = { Playback.toggle() },
                    )
                }
            }

            if (played.isNotEmpty()) {
                item {
                    QueueHeading(
                        "Recently played",
                        played.size,
                        action = "Clear",
                    ) {
                        // Clearing history here means "forget the rows above", not
                        // "delete the play log" — the stats screen owns that.
                        Playback.clearPlayed()
                    }
                }
                items(played.take(8)) { t ->
                    TrackRow(
                        track = t,
                        onPlay = {
                            val at = state.queue.indexOfFirst { it.id == t.id }
                            if (at >= 0) Playback.jumpTo(at)
                        },
                    )
                }
            }

            item {
                QueueHeading(
                    "Up next",
                    upcoming.size,
                    action = "Shuffle",
                ) { Playback.shuffleUpcoming() }
            }

            if (upcoming.isEmpty()) {
                item {
                    Text(
                        "Nothing queued. Radio will add songs when this one ends.",
                        color = Ink.Faint,
                        fontSize = 11.5.sp,
                        fontFamily = SoraFamily,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
            } else {
                itemsIndexed(upcoming) { i, t ->
                    val real = state.index + 1 + i
                    DraggableQueueRow(
                        track = t,
                        isPlaying = state.isPlaying,
                        onPlay = { Playback.jumpTo(real) },
                        onMove = { shift ->
                            Playback.moveInQueue(real, (real + shift).coerceIn(0, state.queue.lastIndex))
                        },
                        onRemove = { Playback.removeAt(real) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueHeading(
    title: String,
    count: Int?,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            color = Ink.Faint,
            fontSize = 9.5.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
        if (count != null && count > 0) {
            Spacer(Modifier.width(6.dp))
            Text("$count", color = Ink.Faint, fontSize = 9.5.sp, fontFamily = SoraFamily)
        }
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                color = Ink.Muted,
                fontSize = 10.5.sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onAction() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun DraggableQueueRow(
    track: Track,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var drag by remember { mutableStateOf(0f) }
    val rowPx = with(LocalDensity.current) { 62.dp.toPx() }
    Row(
        Modifier
            .padding(horizontal = 8.dp)
            .zIndex(if (drag != 0f) 1f else 0f)
            .alpha(if (drag != 0f) 0.92f else 1f)
            .pointerInput(track.id, rowPx) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val shift = (drag / rowPx).roundToInt()
                        if (shift != 0) {
                            onMove(shift)
                            Haptics.tick()
                        }
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { _, amount -> drag += amount }
            }
            .offset { IntOffset(0, drag.roundToInt()) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            TrackRow(
                track = track,
                playing = isPlaying,
                onPlay = onPlay,
            )
        }
        Box(
            Modifier.size(30.dp).clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) { Glyph(G.Drag, Ink.Faint, 14.dp) }
        Box(
            Modifier.size(26.dp).clip(CircleShape).clickable { onRemove() },
            contentAlignment = Alignment.Center,
        ) { Glyph(G.Close, Ink.Faint, 12.dp) }
    }
}
