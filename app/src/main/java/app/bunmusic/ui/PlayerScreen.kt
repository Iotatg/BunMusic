package app.bunmusic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.Ambient
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Haptics
import app.bunmusic.core.Ink
import app.bunmusic.core.LocalBunWindow
import app.bunmusic.core.SerifFamily
import app.bunmusic.core.Skin
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Downloads
import app.bunmusic.data.Lyrics
import app.bunmusic.data.LyricsApi
import app.bunmusic.data.Prefs
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import app.bunmusic.player.Playback
import app.bunmusic.player.PlayerState
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    state: PlayerState,
    onClose: () -> Unit,
    onMore: () -> Unit,
    openQueueOnStart: Boolean = false,
) {
    val track = state.track
    val settings by Prefs.settings.collectAsState()
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var inlineLyrics by remember { mutableStateOf(false) }
    val window = LocalBunWindow.current
    // The widget's queue button opens the player *and* the queue. It used to
    // open the player and leave the listener to find the button themselves.
    LaunchedEffect(openQueueOnStart) { if (openQueueOnStart) showQueue = true }

    val tint = Ambient.colorFor(track)
    val glowAlpha = when (settings.glow) {
        Prefs.Glow.OFF -> 0f
        Prefs.Glow.SOFT -> 0.30f
        Prefs.Glow.BOLD -> 0.55f
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                if (Ink.isLight) {
                    Brush.verticalGradient(listOf(Ink.Base, Ink.Base))
                } else {
                    Brush.verticalGradient(
                        0f to tint.copy(alpha = 0.22f),
                        0.45f to Color(0xFF140F1E),
                        1f to Ink.Void,
                    )
                }
            )
    ) {
        if (glowAlpha > 0f && !Ink.isLight) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(tint.copy(alpha = glowAlpha), Color.Transparent),
                        )
                    )
            )
        }

        if (track == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing playing", color = Ink.Muted, fontFamily = SoraFamily)
            }
            return@Box
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {

            if (Ink.isLight) {
                // Charcoal keeps its single centred column at every size — but on
                // a tablet the column is capped, or the artwork becomes a wall.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.fillMaxHeight().widthIn(max = window.readingMax)) {
                        CharcoalPlayer(
                            state = state,
                            track = track,
                            onClose = onClose,
                            onMore = onMore,
                            onQueue = { showQueue = true },
                            onLyrics = { showLyrics = true },
                            onSleep = { showSleep = true },
                            onSpeed = { showSpeed = true },
                            window = window,
                        )
                    }
                }
                return@Column
            }

            // Two panes from 840 dp in landscape: artwork left, controls right.
            // A single column there is 300 dp of artwork in 700 dp of nothing,
            // with the transport row pinned to the bottom bezel.
            if (window.twoPane) {
                WidePlayer(
                    state = state,
                    track = track,
                    style = settings.playerStyle,
                    tint = tint,
                    onClose = onClose,
                    onMore = onMore,
                    onQueue = { showQueue = true },
                    onLyrics = { showLyrics = true },
                    onSleep = { showSleep = true },
                    onSpeed = { showSpeed = true },
                )
                return@Column
            }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxHeight().widthIn(max = window.readingMax)) {

            // ---- top bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.rotate(90f)) { Glyph(G.Back, Ink.Text, 20.dp, 2.dp) }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "PLAYING FROM",
                        color = Ink.Faint,
                        fontSize = 10.sp,
                        fontFamily = SoraFamily,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        track.album.ifBlank { "BunMusic" },
                        color = Ink.Text,
                        fontSize = 11.5.sp,
                        fontFamily = SoraFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(G.Queue, Ink.Text, 38.dp, 18.dp) { showQueue = true }
                    IconBtn(G.More, Ink.Text, 38.dp, 18.dp, onMore)
                }
            }

            Spring(0.45f, window.short)

            // ---- artwork
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = window.artCap())
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when (settings.playerStyle) {
                    Prefs.PlayerStyle.DISC -> DiscArt(track, state.isPlaying, tint)
                    Prefs.PlayerStyle.VINYL -> VinylArt(track, state.isPlaying, tint)
                    Prefs.PlayerStyle.CARD -> CardArt(track, tint)
                }
            }

            Spring(0.30f, window.short)

            // ---- inline lyrics peek
            if (settings.lyricsOnPlayer) {
                LyricsPeek(track, state.positionMs, expanded = inlineLyrics) { inlineLyrics = !inlineLyrics }
            }

            Spacer(Modifier.height(6.dp))

            // ---- title + like
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        color = Ink.Text,
                        fontSize = 23.sp,
                        fontFamily = SerifFamily,
                        lineHeight = 27.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (Downloads.isDownloaded(track.id) || track.isLocal) {
                            Text(
                                "OFFLINE",
                                color = Ink.Mint,
                                fontSize = 10.sp,
                                fontFamily = SoraFamily,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.7.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Ink.Mint.copy(alpha = 0.14f))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp),
                            )
                            Spacer(Modifier.width(7.dp))
                        }
                        Text(
                            track.artist,
                            color = Ink.Muted,
                            fontSize = 12.5.sp,
                            fontFamily = SoraFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                var liked by remember(track.id) { mutableStateOf(Store.isLiked(track.id)) }
                Box(
                    Modifier.size(42.dp).clip(CircleShape).clickable { liked = Store.toggleLike(track.id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(
                        if (liked) G.HeartFill else G.Heart,
                        if (liked) Ink.Rose else Ink.Muted,
                        20.dp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- seek
            SeekBlock(state, tint, Modifier.padding(horizontal = 22.dp))

            Spacer(Modifier.height(6.dp))

            // ---- transport
            TransportRow(state, tint, Modifier.padding(horizontal = 24.dp))

            Spacer(Modifier.height(10.dp))

            // ---- secondary actions
            SecondaryRow(
                state = state,
                onLyrics = { showLyrics = true },
                onSleep = { showSleep = true },
                onSpeed = { showSpeed = true },
                onQueue = { showQueue = true },
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Spacer(Modifier.height(8.dp))
            }
            }
        }

        AnimatedVisibility(
            visible = showQueue,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            QueueSheet(state = state, onClose = { showQueue = false })
        }

        AnimatedVisibility(
            visible = showLyrics,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            LyricsSheet(track = track, positionMs = state.positionMs, onClose = { showLyrics = false })
        }

        if (showSleep) {
            SleepSheet(
                onDismiss = { showSleep = false },
                onPick = { minutes -> Playback.sleepIn(minutes) },
                onEndOfTrack = { Playback.sleepAtEndOfTrack() },
            )
        }

        if (showSpeed) {
            SpeedSheet(
                current = state.speed,
                onDismiss = { showSpeed = false },
                onPick = { Playback.setSpeed(it) },
            )
        }
    }
}


// ------------------------------------------------------------ shared pieces

/**
 * A gap that collapses on a short screen.
 *
 * The phone layout breathes with `weight` spacers — on an 891 × 411 landscape
 * window those weights resolve to a few dp each and the page still overflows,
 * so they become a fixed 8 dp and the controls keep their room.
 */
@Composable
private fun ColumnScope.Spring(weight: Float, short: Boolean) {
    if (short) Spacer(Modifier.height(8.dp)) else Spacer(Modifier.weight(weight))
}

/** Seek bar, its two timestamps, and the last playback error. Shared by both layouts. */
@Composable
private fun SeekBlock(state: PlayerState, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        SeekBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            onSeek = { Playback.seekTo(it) },
            activeColor = tint,
        )
        Row(Modifier.fillMaxWidth()) {
            Text(fmtTime(state.positionMs), color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily)
            Spacer(Modifier.weight(1f))
            Text(fmtTime(state.durationMs), color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily)
        }
        if (state.error != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                state.error ?: "",
                color = Ink.Rose,
                fontSize = 11.sp,
                fontFamily = SoraFamily,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Shuffle · previous · play · next · repeat. */
@Composable
private fun TransportRow(state: PlayerState, tint: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconBtn(G.Shuffle, if (state.shuffle) tint else Ink.Faint, 40.dp, 18.dp) { Playback.toggleShuffle() }
        IconBtn(G.Prev, Ink.Text, 48.dp, 24.dp) { Playback.previous() }
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(tint)
                .clickable { Playback.toggle() },
            contentAlignment = Alignment.Center,
        ) {
            if (state.buffering && !state.isPlaying) {
                Glyph(G.Wave, Ink.Void, 26.dp)
            } else {
                Glyph(if (state.isPlaying) G.Pause else G.Play, Ink.Void, 26.dp)
            }
        }
        IconBtn(G.Next, Ink.Text, 48.dp, 24.dp) { Playback.next() }
        IconBtn(
            if (state.repeat == 2) G.RepeatOne else G.Repeat,
            if (state.repeat != 0) tint else Ink.Faint,
            40.dp, 18.dp,
        ) { Playback.cycleRepeat() }
    }
}

/** Lyrics · sleep · speed · queue. */
@Composable
private fun SecondaryRow(
    state: PlayerState,
    onLyrics: () -> Unit,
    onSleep: () -> Unit,
    onSpeed: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        SecondaryAction("Lyrics", G.Mic, active = false) { onLyrics() }
        SecondaryAction(
            if (state.sleepActive) fmtTime(state.sleepRemainingMs) else "Sleep",
            G.Moon,
            active = state.sleepActive,
        ) { onSleep() }
        SecondaryAction(
            if (state.speed == 1f) "Speed" else "${state.speed}x",
            G.Speed,
            active = state.speed != 1f,
        ) { onSpeed() }
        SecondaryAction("Queue", G.List, active = false) { onQueue() }
    }
}

/**
 * The tablet now-playing screen: artwork on the left, everything you can touch
 * on the right.
 *
 * Same components as the phone, arranged rather than stacked. The artwork is
 * squared off the *height* so a 16:9 tablet doesn't produce a disc wider than
 * the pane, and the control column never exceeds [BunWindow.readingMax].
 */
@Composable
private fun WidePlayer(
    state: PlayerState,
    track: Track,
    style: Prefs.PlayerStyle,
    tint: Color,
    onClose: () -> Unit,
    onMore: () -> Unit,
    onQueue: () -> Unit,
    onLyrics: () -> Unit,
    onSleep: () -> Unit,
    onSpeed: () -> Unit,
) {
    var liked by remember(track.id) { mutableStateOf(Store.isLiked(track.id)) }

    Row(
        Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxHeight(0.92f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                when (style) {
                    Prefs.PlayerStyle.DISC -> DiscArt(track, state.isPlaying, tint)
                    Prefs.PlayerStyle.VINYL -> VinylArt(track, state.isPlaying, tint)
                    Prefs.PlayerStyle.CARD -> CardArt(track, tint)
                }
            }
        }

        Spacer(Modifier.width(34.dp))

        // 1 : 0.85 — the artwork is the thing you look at; the controls only
        // need enough width for five buttons and a title.
        Column(Modifier.weight(0.85f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.rotate(90f)) { Glyph(G.Back, Ink.Text, 20.dp, 2.dp) }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "NOW PLAYING",
                    color = Ink.Faint,
                    fontSize = 10.sp,
                    fontFamily = SoraFamily,
                    letterSpacing = 1.4.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                IconBtn(G.Queue, Ink.Text, 38.dp, 18.dp) { onQueue() }
                IconBtn(G.More, Ink.Text, 38.dp, 18.dp, onMore)
            }

            Spacer(Modifier.weight(0.4f))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        color = Ink.Text,
                        fontSize = 26.sp,
                        fontFamily = SerifFamily,
                        lineHeight = 30.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (Downloads.isDownloaded(track.id) || track.isLocal) {
                            Text(
                                "OFFLINE",
                                color = Ink.Mint,
                                fontSize = 10.sp,
                                fontFamily = SoraFamily,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.7.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Ink.Mint.copy(alpha = 0.14f))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp),
                            )
                            Spacer(Modifier.width(7.dp))
                        }
                        Text(
                            track.artist,
                            color = Ink.Muted,
                            fontSize = 12.5.sp,
                            fontFamily = SoraFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(
                    Modifier.size(42.dp).clip(CircleShape).clickable {
                        liked = Store.toggleLike(track.id)
                        Haptics.tick()
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(
                        if (liked) G.HeartFill else G.Heart,
                        if (liked) Ink.Rose else Ink.Muted,
                        20.dp,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            SeekBlock(state, tint)

            Spacer(Modifier.height(8.dp))

            TransportRow(state, tint)

            Spacer(Modifier.height(16.dp))

            SecondaryRow(state, onLyrics, onSleep, onSpeed, onQueue)

            Spacer(Modifier.weight(0.3f))
        }
    }
}


// ------------------------------------------------------------- charcoal bun

/**
 * The Charcoal now-playing screen: centred, typographic, no ambient wash. The
 * artwork is the only colour on the page, and the seek bar is a waveform.
 */
@Composable
private fun ColumnScope.CharcoalPlayer(
    state: PlayerState,
    track: Track,
    onClose: () -> Unit,
    onMore: () -> Unit,
    onQueue: () -> Unit,
    onLyrics: () -> Unit,
    onSleep: () -> Unit,
    onSpeed: () -> Unit,
    window: app.bunmusic.core.BunWindow,
) {
    var liked by remember(track.id) { mutableStateOf(Store.isLiked(track.id)) }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.rotate(90f)) { Glyph(G.Back, Ink.Text, 21.dp, 2.2.dp) }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Now Playing",
            color = Ink.Text,
            fontSize = 17.sp,
            fontFamily = SerifFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable { onQueue() },
            contentAlignment = Alignment.Center,
        ) { Glyph(G.Queue, Ink.Text, 20.dp, 2.dp) }
    }

    Spring(0.5f, window.short)

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .then(
                    if (window.short) Modifier.size(window.artCap())
                    else Modifier.fillMaxWidth(0.80f)
                )
                .aspectRatio(1f)
                .shadow(26.dp, RoundedCornerShape(30.dp), clip = false, spotColor = Ink.Shadow, ambientColor = Ink.Shadow)
        ) {
            ArtImage(
                url = track.artUrl,
                asset = track.artAsset,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(30.dp),
            )
        }
    }

    Spring(0.42f, window.short)

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).clickable {
                liked = Store.toggleLike(track.id)
                Haptics.tick()
            },
            contentAlignment = Alignment.Center,
        ) {
            Glyph(
                if (liked) G.HeartFill else G.Heart,
                if (liked) Ink.Rose else Ink.Faint,
                21.dp,
                2.dp,
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                track.title,
                color = Ink.Text,
                fontSize = 29.sp,
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                lineHeight = 33.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                track.artist,
                color = Ink.Muted,
                fontSize = 15.sp,
                fontFamily = SoraFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.size(38.dp).clip(CircleShape).clickable { onMore() },
            contentAlignment = Alignment.Center,
        ) { Glyph(G.More, Ink.Faint, 20.dp, 2.dp) }
    }

    Spacer(Modifier.height(10.dp))

    Column(Modifier.padding(horizontal = 24.dp)) {
        if (Skin.isCloud) {
            LineSeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = { Playback.seekTo(it) },
                activeColor = Ink.Lime,
            )
        } else {
            WaveSeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = { Playback.seekTo(it) },
                activeColor = Ink.Lime,
                seed = track.id.hashCode(),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(fmtTime(state.positionMs), color = Ink.Muted, fontSize = 11.sp, fontFamily = SoraFamily)
            Spacer(Modifier.weight(1f))
            Text(fmtTime(state.durationMs), color = Ink.Muted, fontSize = 11.sp, fontFamily = SoraFamily)
        }
        if (state.error != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                state.error ?: "",
                color = Ink.Rose,
                fontSize = 11.sp,
                fontFamily = SoraFamily,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable { Playback.toggleShuffle() },
            contentAlignment = Alignment.Center,
        ) { Glyph(G.Shuffle, if (state.shuffle) Ink.Text else Ink.Faint, 20.dp, 1.9.dp) }
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable { Playback.previous() },
            contentAlignment = Alignment.Center,
        ) { Glyph(G.Prev, Ink.Text, 26.dp, 2.2.dp) }
        Box(
            Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Ink.Lime)
                .clickable { Playback.toggle() },
            contentAlignment = Alignment.Center,
        ) {
            Glyph(
                if (state.buffering && !state.isPlaying) G.Wave else if (state.isPlaying) G.Pause else G.Play,
                Ink.OnAccent,
                25.dp,
                2.4.dp,
            )
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable { Playback.next() },
            contentAlignment = Alignment.Center,
        ) { Glyph(G.Next, Ink.Text, 26.dp, 2.2.dp) }
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable { Playback.cycleRepeat() },
            contentAlignment = Alignment.Center,
        ) {
            Glyph(
                if (state.repeat == 2) G.RepeatOne else G.Repeat,
                if (state.repeat != 0) Ink.Text else Ink.Faint,
                20.dp,
                1.9.dp,
            )
        }
    }

    Spacer(Modifier.height(6.dp))

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SecondaryAction("Lyrics", G.Mic, active = false) { onLyrics() }
        SecondaryAction(
            if (state.sleepActive) fmtTime(state.sleepRemainingMs) else "Sleep",
            G.Moon,
            active = state.sleepActive,
        ) { onSleep() }
        SecondaryAction(
            if (state.speed == 1f) "Speed" else "${state.speed}x",
            G.Speed,
            active = state.speed != 1f,
        ) { onSpeed() }
        SecondaryAction("Queue", G.List, active = false) { onQueue() }
    }
    Spacer(Modifier.height(8.dp))
}

// ------------------------------------------------------------------- artwork

@Composable
private fun DiscArt(track: Track, playing: Boolean, tint: Color) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(playing, track.id) {
        if (playing) {
            while (true) {
                rotation.animateTo(rotation.value + 360f, tween(26000, easing = LinearEasing))
            }
        }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(0.86f)
                .aspectRatio(1f)
                .background(Brush.radialGradient(listOf(tint.copy(alpha = 0.18f), Color.Transparent)))
        )
        Box(
            Modifier
                .fillMaxWidth(0.70f)
                .aspectRatio(1f)
                .rotate(if (playing) rotation.value else 0f)
        ) {
            ArtImage(track.artUrl, track.artAsset, Modifier.fillMaxSize(), CircleShape)
            Box(
                Modifier.align(Alignment.Center).size(18.dp).clip(CircleShape).background(Ink.Void)
            )
            Box(
                Modifier.align(Alignment.Center).size(30.dp).clip(CircleShape).background(Ink.Void.copy(alpha = 0.45f))
            )
        }
    }
}

@Composable
private fun VinylArt(track: Track, playing: Boolean, tint: Color) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(playing, track.id) {
        if (playing) {
            while (true) {
                rotation.animateTo(rotation.value + 360f, tween(22000, easing = LinearEasing))
            }
        }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(0.80f)
                .aspectRatio(1f)
                .rotate(if (playing) rotation.value else 0f)
                .clip(CircleShape)
                .background(Color(0xFF0B0A0E))
                .border(1.dp, Ink.Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .fillMaxWidth(0.94f - i * 0.08f)
                        .aspectRatio(1f)
                        .border(1.dp, Ink.Hairline, CircleShape)
                )
            }
            ArtImage(
                url = track.artUrl,
                asset = track.artAsset,
                modifier = Modifier.fillMaxWidth(0.52f).aspectRatio(1f),
                shape = CircleShape,
            )
            Box(
                Modifier.align(Alignment.Center).size(10.dp).clip(CircleShape).background(Ink.Void)
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 30.dp)
                .size(width = 4.dp, height = 64.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tint.copy(alpha = 0.7f))
        )
    }
}

@Composable
private fun CardArt(track: Track, tint: Color) {
    Box(
        Modifier.fillMaxWidth(0.78f).aspectRatio(1f),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(26.dp))
                .background(tint.copy(alpha = 0.25f))
        )
        ArtImage(
            url = track.artUrl,
            asset = track.artAsset,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(26.dp),
        )
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent, Ink.Void.copy(alpha = 0.55f))
                    )
                )
        )
        Text(
            track.title,
            color = Ink.Text,
            fontSize = 17.sp,
            fontFamily = SerifFamily,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
        )
    }
}

// ------------------------------------------------------------------ secondary

@Composable
private fun SecondaryAction(label: String, glyph: G, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(glyph, if (active) Ink.Lime else Ink.Muted, 18.dp, if (Ink.isLight) 1.9.dp else 1.6.dp)
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            color = if (active) Ink.Lime else Ink.Faint,
            fontSize = 10.sp,
            fontFamily = SoraFamily,
            letterSpacing = 0.4.sp,
        )
    }
}

// ---------------------------------------------------------------- queue sheet

@Composable
private fun QueueSheet(state: PlayerState, onClose: () -> Unit, onSaved: (String) -> Unit = {}) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Void.copy(alpha = 0.97f))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Up next", color = Ink.Text, fontSize = 17.sp, fontFamily = SerifFamily)
                Text("${state.queue.size} tracks", color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily)
            }
            GhostBtn("Save", G.Plus) {
                val name = Playback.saveQueueAsPlaylist()
                if (name.isNotBlank()) onSaved(name)
            }
            Spacer(Modifier.width(8.dp))
            GhostBtn("Clear", G.Trash) { Playback.clearQueue() }
            Spacer(Modifier.width(8.dp))
            IconBtn(G.Close, Ink.Text, 38.dp, 16.dp, onClose)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
            itemsIndexed(state.queue) { i, t ->
                var drag by remember(i) { mutableStateOf(0f) }
                val rowPx = with(androidx.compose.ui.platform.LocalDensity.current) { 62.dp.toPx() }
                Row(
                    Modifier
                        .padding(horizontal = 8.dp)
                        .zIndex(if (drag != 0f) 1f else 0f)
                        .offset { IntOffset(0, drag.roundToInt()) }
                        .alpha(if (drag != 0f) 0.9f else 1f)
                        .pointerInput(i, state.queue.size) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    val shift = (drag / rowPx).roundToInt()
                                    if (shift != 0) {
                                        Playback.moveInQueue(
                                            i,
                                            (i + shift).coerceIn(0, state.queue.lastIndex),
                                        )
                                        Haptics.tick()
                                    }
                                    drag = 0f
                                },
                                onDragCancel = { drag = 0f },
                            ) { _, amount -> drag += amount }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        TrackRow(
                            track = t,
                            playing = state.isPlaying,
                            isCurrent = i == state.index,
                            onPlay = { Playback.jumpTo(i) },
                        )
                    }
                    Box(
                        Modifier.size(30.dp).clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Glyph(G.Drag, Ink.Faint, 14.dp) }
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).clickable { Playback.removeAt(i) },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(G.Close, Ink.Faint, 12.dp) }
                }
            }
        }
    }
}

// --------------------------------------------------------------- lyrics sheet

@Composable
private fun LyricsSheet(track: Track, positionMs: Long, onClose: () -> Unit) {
    var lyrics by remember(track.id) { mutableStateOf<Lyrics?>(null) }
    var loading by remember(track.id) { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(track.id) {
        loading = true
        lyrics = runCatching { LyricsApi.forTrack(track) }.getOrNull()
        loading = false
    }

    val lines = lyrics?.lines ?: emptyList()
    val shift = Prefs.settings.value.lyricsOffsetMs.toLong()
    val activeIndex = lines.indexOfLast { it.atMs <= positionMs + shift }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            runCatching { listState.animateScrollToItem(activeIndex.coerceAtLeast(0)) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(if (Ink.isLight) Ink.Base else Color(0xFF1D1730))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Lyrics", color = Ink.Text, fontSize = 17.sp, fontFamily = SerifFamily)
                Text(
                    track.title + if (lyrics?.provider?.isNotBlank() == true) " · ${lyrics?.provider}" else "",
                    color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconBtn(G.Close, Ink.Text, 38.dp, 16.dp, onClose)
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Finding lyrics…", color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily)
            }
            lyrics?.isEmpty == true -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Glyph(G.Mic, Ink.Faint, 26.dp)
                    Spacer(Modifier.height(10.dp))
                    Text("No lyrics for this one", color = Ink.Muted, fontSize = 12.5.sp, fontFamily = SoraFamily)
                }
            }
            lines.isEmpty() -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
                item {
                    Text(
                        lyrics?.plain ?: "",
                        color = Ink.Text,
                        fontSize = 14.sp,
                        fontFamily = SoraFamily,
                        lineHeight = 26.sp,
                    )
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(lines) { i, line ->
                    val active = i == activeIndex
                    Text(
                        line.text,
                        color = if (active) Ink.Lime else Ink.Text.copy(alpha = if (i < activeIndex) 0.42f else 0.86f),
                        fontSize = if (active) 19.sp else 16.sp,
                        fontFamily = SerifFamily,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsPeek(track: Track, positionMs: Long, expanded: Boolean, onToggle: () -> Unit) {
    var lyrics by remember(track.id) { mutableStateOf<Lyrics?>(null) }
    LaunchedEffect(track.id) {
        lyrics = runCatching { LyricsApi.forTrack(track) }.getOrNull()
    }
    val lines = lyrics?.lines ?: emptyList()
    if (lines.isEmpty()) return
    val active = lines.indexOfLast { it.atMs <= positionMs + Prefs.settings.value.lyricsOffsetMs }
    val current = lines.getOrNull(active)?.text ?: lines.firstOrNull()?.text ?: ""
    val next = lines.getOrNull(active + 1)?.text ?: ""

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggle() }
            .padding(vertical = 6.dp),
    ) {
        Text(
            current,
            color = Ink.Lime,
            fontSize = 14.sp,
            fontFamily = SerifFamily,
            maxLines = if (expanded) 3 else 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (next.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                next,
                color = Ink.Text.copy(alpha = 0.42f),
                fontSize = 12.5.sp,
                fontFamily = SerifFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ------------------------------------------------------------------ speed

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSheet(current: Float, onDismiss: () -> Unit, onPick: (Float) -> Unit) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Ink.Sheet,
        contentColor = Ink.Text,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "Playback speed",
                color = Ink.Text,
                fontSize = 16.sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                SheetAction(if (s == 1f) "Normal (1x)" else "${s}x", G.Speed) {
                    onPick(s)
                    onDismiss()
                }
            }
        }
    }
}
