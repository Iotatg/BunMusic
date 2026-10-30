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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SerifFamily
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Lyrics
import app.bunmusic.data.LyricsApi
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import app.bunmusic.player.Playback
import app.bunmusic.player.PlayerState

@Composable
fun PlayerScreen(state: PlayerState, onClose: () -> Unit) {
    val track = state.track
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF241B33),
                    0.42f to Color(0xFF150F20),
                    1f to Ink.Void,
                )
            )
    ) {
        if (track == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing playing", color = Ink.Muted, fontFamily = SoraFamily)
            }
            return@Box
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {

            // ---- top bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.rotate(-90f)) {
                        Glyph(G.Back, Ink.Text, 20.dp, 2.dp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "PLAYING FROM",
                        color = Ink.Faint,
                        fontSize = 8.5.sp,
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
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { showQueue = true },
                    contentAlignment = Alignment.Center,
                ) { Glyph(G.Queue, Ink.Text, 19.dp) }
            }

            Spacer(Modifier.weight(0.55f))

            // ---- artwork
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxWidth(0.86f)
                        .aspectRatio(1f)
                        .background(
                            Brush.radialGradient(
                                listOf(Ink.Lavender.copy(alpha = 0.16f), Color.Transparent)
                            )
                        )
                )
                val rotation = remember { Animatable(0f) }
                LaunchedEffect(state.isPlaying, track.id) {
                    if (state.isPlaying) {
                        while (true) {
                            rotation.animateTo(
                                rotation.value + 360f,
                                tween(26000, easing = LinearEasing),
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth(0.70f)
                        .aspectRatio(1f)
                        .rotate(if (state.isPlaying) rotation.value else 0f)
                ) {
                    ArtImage(track.artUrl, track.artAsset, Modifier.fillMaxSize(), CircleShape)
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Ink.Void)
                    )
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Ink.Void.copy(alpha = 0.45f))
                    )
                }
            }

            Spacer(Modifier.weight(0.42f))

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
                        if (track.isLocal) {
                            Text(
                                "OFFLINE",
                                color = Ink.Mint,
                                fontSize = 8.sp,
                                fontFamily = SoraFamily,
                                fontWeight = FontWeight.SemiBold,
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
                    Modifier.size(42.dp).clip(CircleShape).clickable {
                        liked = Store.toggleLike(track.id)
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

            // ---- seek
            Column(Modifier.padding(horizontal = 22.dp)) {
                SeekBar(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    onSeek = { Playback.seekTo(it) },
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(fmtTime(state.positionMs), color = Ink.Faint, fontSize = 10.5.sp, fontFamily = SoraFamily)
                    Spacer(Modifier.weight(1f))
                    Text(fmtTime(state.durationMs), color = Ink.Faint, fontSize = 10.5.sp, fontFamily = SoraFamily)
                }
                if (state.error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.error ?: "",
                        color = Ink.Rose,
                        fontSize = 10.5.sp,
                        fontFamily = SoraFamily,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---- transport
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { Playback.toggleShuffle() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(G.Shuffle, if (state.shuffle) Ink.Lime else Ink.Faint, 18.dp) }

                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable { Playback.previous() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(G.Prev, Ink.Text, 24.dp) }

                Box(
                    Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(Ink.Lime)
                        .clickable { Playback.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.buffering && !state.isPlaying) {
                        Glyph(G.Wave, Ink.OnAccent, 26.dp)
                    } else {
                        Glyph(if (state.isPlaying) G.Pause else G.Play, Ink.OnAccent, 26.dp)
                    }
                }

                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable { Playback.next() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(G.Next, Ink.Text, 24.dp) }

                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { Playback.cycleRepeat() },
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(
                        if (state.repeat == 2) G.RepeatOne else G.Repeat,
                        if (state.repeat != 0) Ink.Lime else Ink.Faint,
                        18.dp,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---- secondary actions
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 34.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SecondaryAction("Lyrics", G.Mic) { showLyrics = true }
                SecondaryAction("Queue", G.List) { showQueue = true }
                SecondaryAction(
                    "Share",
                    G.Share,
                ) { }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ---- queue sheet
        AnimatedVisibility(
            visible = showQueue,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            QueueSheet(state = state, onClose = { showQueue = false })
        }

        // ---- lyrics overlay
        AnimatedVisibility(
            visible = showLyrics,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            LyricsSheet(track = track, positionMs = state.positionMs, onClose = { showLyrics = false })
        }
    }
}

@Composable
private fun SecondaryAction(label: String, glyph: G, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(glyph, Ink.Muted, 18.dp)
        Spacer(Modifier.height(5.dp))
        Text(label, color = Ink.Faint, fontSize = 9.5.sp, fontFamily = SoraFamily, letterSpacing = 0.4.sp)
    }
}

@Composable
private fun QueueSheet(state: PlayerState, onClose: () -> Unit) {
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
                Text(
                    "${state.queue.size} tracks",
                    color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily,
                )
            }
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Ink.Card).clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) { Glyph(G.Close, Ink.Text, 16.dp) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
            itemsIndexed(state.queue) { i, t ->
                Box(Modifier.padding(horizontal = 8.dp)) {
                    TrackRow(
                        track = t,
                        playing = state.isPlaying,
                        isCurrent = i == state.index,
                        onPlay = { Playback.jumpTo(i) },
                    )
                }
            }
        }
    }
}

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
    val activeIndex = lines.indexOfLast { it.atMs <= positionMs }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            runCatching { listState.animateScrollToItem(activeIndex.coerceAtLeast(0)) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1D1730), Ink.Void))
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Lyrics", color = Ink.Text, fontSize = 17.sp, fontFamily = SerifFamily)
                Text(track.title, color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Ink.Card).clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) { Glyph(G.Close, Ink.Text, 16.dp) }
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
