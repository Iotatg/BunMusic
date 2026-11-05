package app.bunmusic.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Haptics
import app.bunmusic.core.Ink
import app.bunmusic.core.Labels
import app.bunmusic.core.LocalBunWindow
import app.bunmusic.core.LocalReducedMotion
import app.bunmusic.core.SerifFamily
import app.bunmusic.core.Skin
import app.bunmusic.core.SoraFamily
import app.bunmusic.core.label
import app.bunmusic.data.Downloads
import app.bunmusic.data.Prefs
import app.bunmusic.data.Store
import app.bunmusic.player.Playback
import app.bunmusic.player.PlayerState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Nebula's own now-playing screen.
 *
 * The other four buns share one player layout and only swap colours. Nebula does
 * not: a sky that moves needs a composition built around it, not a gradient
 * poured into the standard column. The crescent poster and the record-disc are
 * gone. What replaced them, after looking at the SoundWave and Playo studies on
 * Dribbble and Mobbin's shipped now-playing patterns:
 *
 *  - a **squircle artwork** floating inside a slow orbital ring, not a spinning
 *    black disc with a hole in the middle;
 *  - a **waveform you can scrub** — the seek bar is the music, not a hairline
 *    two centimetres below your thumb;
 *  - **glass pills** for the transport and the secondary actions, with the one
 *    lit object (play) casting coloured light onto them;
 *  - a large serif title and a small "up next" chip, so the hierarchy reads at
 *    a glance instead of everything being 12 sp.
 *
 * Every control still reaches the same [Playback] calls and the same sheets as
 * the standard player; the overlays themselves remain in `PlayerScreen`, so
 * back handling, queue sharing and lyrics did not have to be duplicated.
 *
 * Motion credits: the artwork's spring entrance is the shared-element idea from
 * [skydoves/Orbital](https://github.com/skydoves/Orbital) reduced to a scale,
 * and the travelling cosine wave in [AuroraWave] is the bar geometry from
 * [zainkhalid91/compose-loaders](https://github.com/zainkhalid91/compose-loaders).
 */

private const val WAVE_BARS = 52
private const val TWO_PI = 2f * 3.141592653589793f

@Composable
fun NebulaPlayer(
    state: PlayerState,
    songTint: Color,
    glow: Float,
    settings: Prefs.Settings,
    onClose: () -> Unit,
    onMore: () -> Unit,
    onQueue: () -> Unit,
    onLyrics: () -> Unit,
    onSleep: () -> Unit,
    onSpeed: () -> Unit,
    onCredits: () -> Unit,
    onLikeChanged: (Boolean) -> Unit,
) {
    val track = state.track ?: return
    val window = LocalBunWindow.current
    // The artwork colour is a good mood but a bad button: a salmon cover used to
    // turn the one lit object on this page salmon too. In Nebula the accent is
    // the aurora, so the song only gets a fifth of the vote.
    val tint = lerp(Ink.Lime, songTint, 0.20f)
    var liked by remember(track.id) { mutableStateOf(Store.isLiked(track.id)) }
    var inlineLyrics by remember(track.id) { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
    ) {
        NebulaTopBar(track, onClose, onCredits, onQueue, onMore)

        // The two flexible gaps are what let the artwork breathe on a tall phone
        // and collapse to almost nothing on a short landscape window.
        FlexGap(0.30f, window.short)

        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = window.artCap())
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            NebulaArt(
                track = track,
                playing = state.isPlaying,
                tint = tint,
                glow = glow,
                short = window.short,
            )
        }

        FlexGap(0.08f, window.short)

        // A small, honest hint about what comes next — shipped players put this
        // on the now-playing screen because the queue is one tap away, not four.
        if (!window.short && state.queue.size > state.index + 1) {
            UpNextChip(state.queue[state.index + 1].title, onQueue)
            Spacer(Modifier.height(12.dp))
        }

        AuroraWave(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            playing = state.isPlaying,
            onSeek = { Playback.seekTo(it) },
        )

        Spacer(Modifier.height(10.dp))

        NebulaMeta(
            track = track,
            liked = liked,
            onLike = {
                val now = Store.toggleLike(track.id)
                liked = now
                if (now) Haptics.tick()
                onLikeChanged(now)
            },
        )

        val playbackError = state.error
        if (playbackError != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                playbackError,
                color = Ink.Rose,
                fontSize = 11.sp,
                fontFamily = SoraFamily,
                maxLines = 2,
            )
        }

        if (settings.lyricsOnPlayer) {
            LyricsPeek(track, state.positionMs, inlineLyrics) { inlineLyrics = !inlineLyrics }
        }

        Spacer(Modifier.height(14.dp))

        NebulaTransport(state, tint)

        // The A–B loop bar is a control, not decoration: dropping it because the
        // layout changed would have removed a feature for Nebula users.
        if (state.abActive) {
            Spacer(Modifier.height(8.dp))
            AbRepeatBar(state = state, tint = tint, modifier = Modifier.padding(horizontal = 6.dp))
        }

        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .glass(radius = 26.dp, topAlpha = 0.07f, bottomAlpha = 0.02f)
                .padding(vertical = 4.dp)
        ) {
            SecondaryRow(
                state = state,
                onLyrics = onLyrics,
                onSleep = onSleep,
                onSpeed = onSpeed,
                onQueue = onQueue,
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** A gap that collapses on a short screen, as in the standard player. */
@Composable
private fun ColumnScope.FlexGap(weight: Float, short: Boolean) {
    if (short) Spacer(Modifier.height(6.dp)) else Spacer(Modifier.weight(weight))
}

// ------------------------------------------------------------------- top bar

@Composable
private fun NebulaTopBar(
    track: app.bunmusic.data.Track,
    onClose: () -> Unit,
    onCredits: () -> Unit,
    onQueue: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable { onClose() }
                .iconLabel("Collapse player"),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.rotate(90f)) { Glyph(G.Back, Ink.Text, 20.dp) }
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "NOW PLAYING",
                color = Ink.Faint,
                fontSize = 9.5.sp,
                fontFamily = SoraFamily,
                letterSpacing = 1.8.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                track.album.ifBlank { "BunMusic" },
                color = Ink.Muted,
                fontSize = 11.sp,
                fontFamily = SoraFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(G.Receipt, Ink.Text, 36.dp, 17.dp) { onCredits() }
            IconBtn(G.Queue, Ink.Text, 36.dp, 18.dp) { onQueue() }
            IconBtn(G.More, Ink.Text, 36.dp, 18.dp, onClick = onMore)
        }
    }
}

@Composable
private fun UpNextChip(title: String, onQueue: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(radius = 18.dp, topAlpha = 0.07f, bottomAlpha = 0.02f)
            .clickable { onQueue() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "UP NEXT",
            color = Ink.Mint,
            fontSize = 9.sp,
            fontFamily = SoraFamily,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = Ink.Muted,
            fontSize = 11.5.sp,
            fontFamily = SoraFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Glyph(G.Forward, Ink.Faint, 15.dp)
    }
}

// ------------------------------------------------------------------- artwork

/**
 * A floating squircle inside a slow orbital ring.
 *
 * The spring entrance is Orbital's shared-element idea in miniature: when the
 * track changes the artwork arrives from slightly small rather than cutting,
 * which is the difference between "the song changed" and "the screen flashed".
 * Reduced motion snaps it to rest.
 */
@Composable
private fun NebulaArt(
    track: app.bunmusic.data.Track,
    playing: Boolean,
    tint: Color,
    glow: Float,
    short: Boolean,
) {
    val reduced = LocalReducedMotion.current
    val entrance = remember { Animatable(1f) }
    LaunchedEffect(track.id) {
        if (reduced) {
            entrance.snapTo(1f)
        } else {
            entrance.snapTo(0.86f)
            entrance.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }
    val ringTransition = if (reduced) null else rememberInfiniteTransition(label = "orbit")
    val ringState = ringTransition?.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    ) ?: remember { mutableStateOf(24f) }

    val boxSize = if (short) 190.dp else 306.dp
    val shape = RoundedCornerShape(30.dp)

    Box(Modifier.size(boxSize), contentAlignment = Alignment.Center) {
        SoftGlow(
            color = tint,
            modifier = Modifier.fillMaxSize(),
            active = playing,
            alpha = 0.30f + glow * 0.28f,
        )
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f
            val ringR = r * 0.985f
            drawCircle(
                color = Color.White.copy(alpha = 0.09f),
                radius = ringR,
                center = c,
                style = Stroke(1.dp.toPx()),
            )
            val a = ringState.value
            drawArc(
                color = Ink.Lime.copy(alpha = 0.62f),
                startAngle = a,
                sweepAngle = 58f,
                useCenter = false,
                topLeft = Offset(c.x - ringR, c.y - ringR),
                size = Size(ringR * 2f, ringR * 2f),
                style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = Ink.Mint.copy(alpha = 0.40f),
                startAngle = a + 180f,
                sweepAngle = 36f,
                useCenter = false,
                topLeft = Offset(c.x - ringR, c.y - ringR),
                size = Size(ringR * 2f, ringR * 2f),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
            )
            val rad = Math.toRadians(a.toDouble())
            drawCircle(
                color = Ink.Lavender,
                radius = 2.6f.dp.toPx(),
                center = Offset(
                    c.x + cos(rad).toFloat() * ringR,
                    c.y + sin(rad).toFloat() * ringR,
                ),
            )
        }
        Box(
            Modifier
                .fillMaxSize(0.79f)
                .aspectRatio(1f)
                .graphicsLayer {
                    val s = entrance.value
                    scaleX = s
                    scaleY = s
                    alpha = 0.55f + 0.45f * s
                }
                .neon(tint, radius = 30.dp, elevation = 20.dp)
                .clip(shape)
                .background(tint.copy(alpha = 0.20f))
        ) {
            val hasArt = !track.artUrl.isNullOrBlank() || !track.artAsset.isNullOrBlank()
            if (hasArt) {
                ArtImage(track.artUrl, track.artAsset, Modifier.fillMaxSize(), shape)
            } else {
                NebulaCover(track.title, tint)
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent, Ink.Void.copy(alpha = 0.46f))
                    )
                )
            )
        }
        Box(
            Modifier
                .fillMaxSize(0.79f)
                .aspectRatio(1f)
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.42f),
                            Color.White.copy(alpha = 0.05f),
                            Ink.Lime.copy(alpha = 0.30f),
                        )
                    ),
                    shape,
                )
        )
    }
}

/**
 * A generated cover for the many tracks that arrive with no artwork.
 *
 * `ArtImage`'s fallback is a 22 dp disc glyph in a flat card — correct on a 48 dp
 * list row, hopeless at 240 dp, where it reads as a broken image. A music app
 * that shows a black hole for most of its library looks unfinished, so the
 * Nebula player paints one: the song's colour, concentric rings, and a big
 * serif initial. Deterministic, no network, no asset.
 */
@Composable
private fun NebulaCover(title: String, tint: Color) {
    val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "B"
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        tint.copy(alpha = 0.62f),
                        Ink.Card,
                        Ink.Void,
                    )
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension
            for (i in 1..4) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.055f),
                    radius = r * 0.11f * i,
                    center = center,
                    style = Stroke(1.dp.toPx()),
                )
            }
        }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                initial,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 96.sp,
                fontFamily = SerifFamily,
                lineHeight = 96.sp,
            )
            Text(
                "BUNMUSIC",
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 9.sp,
                fontFamily = SoraFamily,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ------------------------------------------------------------------ waveform

/**
 * The seek bar is the waveform. Bars left of the playhead burn violet→cyan, the
 * rest are dim glass; tapping or dragging anywhere seeks.
 *
 * The travelling wave is `0.5 - 0.5·cos(2π(phase - i·0.5/bars))` from
 * compose-loaders' `WaveBars`, with a deterministic envelope per bar so a paused
 * song still looks like a recording rather than a flat comb. The [phaseState]
 * value is read in the draw lambda — no recomposition per frame.
 */
@Composable
private fun AuroraWave(
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val transition = if (reduced || !playing) null else rememberInfiniteTransition(label = "wave")
    val phaseState = transition?.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_800, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    ) ?: remember { mutableStateOf(0.24f) }

    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .semantics {
                    contentDescription = "Seek, ${fmtTime(positionMs)} of ${fmtTime(durationMs)}"
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                }
                .pointerInput(durationMs) {
                    detectTapGestures { off ->
                        if (durationMs > 0) {
                            val f = (off.x / size.width).coerceIn(0f, 1f)
                            onSeek((f * durationMs).toLong())
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures { change, _ ->
                        if (durationMs > 0) {
                            val f = (change.position.x / size.width).coerceIn(0f, 1f)
                            onSeek((f * durationMs).toLong())
                            change.consume()
                        }
                    }
                }
        ) {
            val phase = phaseState.value
            val barW = size.width / (WAVE_BARS + (WAVE_BARS - 1) * 0.6f)
            val gap = barW * 0.6f
            for (i in 0 until WAVE_BARS) {
                val x = i * (barW + gap)
                val envelope = 0.30f +
                    0.70f * abs(sin(i * 0.31f + 1.3f)) * (0.55f + 0.45f * abs(cos(i * 0.11f)))
                val wave = 0.5f - 0.5f * cos(TWO_PI * (phase - i * 0.5f / WAVE_BARS))
                val motion = if (playing) 0.45f + 0.55f * wave else 0.40f
                val h = size.height * (0.13f + 0.80f * envelope * motion)
                val played = (i.toFloat() / WAVE_BARS) <= progress
                val colour = if (played) {
                    lerp(Ink.Lime, Ink.Mint, i.toFloat() / WAVE_BARS)
                } else {
                    Color.White.copy(alpha = 0.14f)
                }
                drawRoundRect(
                    color = colour,
                    topLeft = Offset(x, (size.height - h) / 2f),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                )
            }
            val px = size.width * progress
            drawLine(
                color = Ink.Text.copy(alpha = 0.85f),
                start = Offset(px, 0f),
                end = Offset(px, size.height),
                strokeWidth = 1.4f.dp.toPx(),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text(fmtTime(positionMs), color = Ink.Faint, fontSize = 10.5.sp, fontFamily = SoraFamily)
            Spacer(Modifier.weight(1f))
            Text(fmtTime(durationMs), color = Ink.Faint, fontSize = 10.5.sp, fontFamily = SoraFamily)
        }
    }
}

// ---------------------------------------------------------------------- meta

@Composable
private fun NebulaMeta(
    track: app.bunmusic.data.Track,
    liked: Boolean,
    onLike: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = Ink.Text,
                fontSize = 29.sp,
                fontFamily = SerifFamily,
                lineHeight = 32.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (Downloads.isDownloaded(track.id) || track.isLocal) {
                    Text(
                        "OFFLINE",
                        color = Ink.Mint,
                        fontSize = 9.sp,
                        fontFamily = SoraFamily,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
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
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .glass(radius = 22.dp, topAlpha = 0.09f)
                .tapable(label = Labels.like(liked)) { onLike() },
            contentAlignment = Alignment.Center,
        ) {
            MorphIcon(
                glyph = if (liked) G.HeartFill else G.Heart,
                tint = if (liked) Ink.Rose else Ink.Muted,
                size = 20.dp,
            )
        }
    }
}

// ----------------------------------------------------------------- transport

@Composable
private fun NebulaTransport(state: PlayerState, tint: Color) {
    val active = readableOn(tint, Ink.Base)
    Row(
        Modifier
            .fillMaxWidth()
            .glass(radius = 34.dp, topAlpha = 0.09f, bottomAlpha = 0.025f)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape)
                .tapable(label = Labels.shuffle(state.shuffle)) { Playback.toggleShuffle() },
            contentAlignment = Alignment.Center,
        ) {
            Glyph(G.Shuffle, if (state.shuffle) active else Ink.Faint, 19.dp)
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).tapable(label = G.Prev.label) { Playback.previous() },
            contentAlignment = Alignment.Center,
        ) {
            Glyph(G.Prev, Ink.Text, 24.dp)
        }
        MorphPlayButton(
            playing = state.isPlaying,
            tint = tint,
            ink = inkOn(tint),
            size = 64.dp,
            glyphSize = 25.dp,
            busy = state.buffering,
            stroke = 2.4.dp,
            // The one lit object on the page casts coloured light onto the glass.
            modifier = Modifier.neon(Ink.Lime, radius = 32.dp, elevation = 24.dp),
        ) { Playback.toggle() }
        Box(
            Modifier.size(44.dp).clip(CircleShape).tapable(label = G.Next.label) { Playback.next() },
            contentAlignment = Alignment.Center,
        ) {
            Glyph(G.Next, Ink.Text, 24.dp)
        }
        Box(
            Modifier.size(42.dp).clip(CircleShape)
                .tapable(label = Labels.repeat(state.repeat)) { Playback.cycleRepeat() },
            contentAlignment = Alignment.Center,
        ) {
            MorphIcon(
                glyph = if (state.repeat == 2) G.RepeatOne else G.Repeat,
                tint = if (state.repeat != 0) active else Ink.Faint,
                size = 19.dp,
            )
        }
    }
}
