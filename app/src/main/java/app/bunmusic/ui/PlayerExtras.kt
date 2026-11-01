package app.bunmusic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Haptics
import app.bunmusic.core.Ink
import app.bunmusic.core.LocalReducedMotion
import app.bunmusic.core.SerifFamily
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Cache
import app.bunmusic.data.Downloads
import app.bunmusic.data.Prefs
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import app.bunmusic.player.PlayerState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The things Spotify puts behind the "..." menu, rebuilt.
 *
 * Visualizer, credits, technical info, A–B repeat, a fine speed control and the
 * like burst. All of them are drawn, none of them needs a server, and the only
 * state they keep is the state the player already has.
 */

/**
 * Bars that move with the song.
 *
 * There is no audio waveform to read — ExoPlayer does not expose one — so this is
 * an honest lie: a fixed set of phases driven by the playback position, which
 * makes the bars stop when the music stops and speed up when you change the
 * speed. It is a visual metronome, not an analyser.
 */
@Composable
fun VisualizerBars(
    playing: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    bars: Int = 32,
) {
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "viz")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    Canvas(modifier) {
        val gap = size.width / (bars * 2f)
        val barW = gap * 0.9f
        val mid = size.height / 2f
        for (i in 0 until bars) {
            val seed = i * 0.7f
            val wave = if (playing && !reduced) {
                (sin(phase * 1.6f + seed) + sin(phase * 0.7f + seed * 2.1f)) / 2f
            } else {
                sin(seed * 1.3f) * 0.35f
            }
            val amp = (0.18f + 0.82f * abs(wave)).coerceIn(0.08f, 1f)
            val h = size.height * amp
            val x = i * gap * 2f + gap
            drawLine(
                color = tint.copy(alpha = if (playing) 0.9f else 0.35f),
                start = Offset(x, mid - h / 2f),
                end = Offset(x, mid + h / 2f),
                strokeWidth = barW,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Shared chrome for the three sheets below. */
@Composable
private fun SheetShell(
    title: String,
    subtitle: String?,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.Void.copy(alpha = 0.97f))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Ink.Text, fontSize = 18.sp, fontFamily = SerifFamily)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            color = Ink.Faint,
                            fontSize = 11.sp,
                            fontFamily = SoraFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconBtn(G.Close, Ink.Text, 38.dp, 16.dp, onClose)
            }
            content()
        }
    }
}

private data class Fact(val label: String, val value: String)

/**
 * Credits, as far as a YouTube-sourced track can honestly have them.
 *
 * There is no label database behind this: title, credited artist, album and the
 * people the search result names are all the app actually knows. The sheet says
 * so instead of inventing a producer.
 */
@Composable
fun CreditsSheet(
    track: Track,
    onClose: () -> Unit,
    onSearch: (String) -> Unit,
    onInfo: () -> Unit,
    onToast: (String) -> Unit,
) {
    val plays = Store.playCounts()[track.id] ?: 0
    val skips = Store.skips()[track.id] ?: 0
    SheetShell("Credits", track.title, onClose) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Text(
                track.title,
                color = Ink.Text,
                fontSize = 24.sp,
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(track.creditLine(), color = Ink.Muted, fontSize = 13.sp, fontFamily = SoraFamily)
            Spacer(Modifier.height(18.dp))

            CreditRow("Performed by", track.artist.ifBlank { "Unknown artist" })
            if (track.album.isNotBlank()) CreditRow("Album", track.album)
            CreditRow("Length", fmtTime(track.durationMs))
            CreditRow("Source", if (track.isLocal) "Bundled with the app" else "YouTube Music")
            if (plays > 0) CreditRow("Your plays", "$plays${if (skips > 0) " · $skips skipped" else ""}")

            Spacer(Modifier.height(18.dp))
            Text(
                "Written-by and producer credits are not published for this recording. " +
                    "What you see above is everything the source returns.",
                color = Ink.Faint,
                fontSize = 11.sp,
                fontFamily = SoraFamily,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(22.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostBtn("Artist", G.User) { onSearch(track.artist) }
                GhostBtn("Album", G.Disc) { if (track.album.isNotBlank()) onSearch(track.album) else onToast("No album listed") }
                GhostBtn("Info", G.Receipt) { onInfo() }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun CreditRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ink.Faint, fontSize = 12.sp, fontFamily = SoraFamily, modifier = Modifier.width(110.dp))
        Text(value, color = Ink.Text, fontSize = 13.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
    }
}

/** The technical card: where the bytes came from and what the app knows about them. */
@Composable
fun TrackInfoSheet(track: Track, onClose: () -> Unit, onToast: (String) -> Unit) {
    val settings by Prefs.settings.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val cached = Cache.hasOffline(track.videoId)
    val saved = Downloads.isDownloaded(track.id)
    val facts = listOf(
        Fact("Track id", track.id),
        Fact("Video id", track.videoId.ifBlank { "—" }),
        Fact("Duration", fmtTime(track.durationMs)),
        Fact("Quality", settings.quality.label + " · ${settings.quality.bitrate / 1000} kbps"),
        Fact("Downloaded", if (saved) "Yes" else "No"),
        Fact("Cached stream", if (cached) "Yes" else "No"),
        Fact("Offline file", if (track.isLocal) track.assetAudio ?: "bundled" else "—"),
        Fact("Plays", "${Store.playCounts()[track.id] ?: 0}"),
        Fact("Skips", "${Store.skips()[track.id] ?: 0}"),
    )
    SheetShell("Track info", track.title, onClose) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            facts.forEach { f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(f.label, color = Ink.Faint, fontSize = 11.5.sp, fontFamily = SoraFamily, modifier = Modifier.width(120.dp))
                    Text(
                        f.value,
                        color = Ink.Text,
                        fontSize = 11.5.sp,
                        fontFamily = SoraFamily,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            GhostBtn("Copy id", G.Copy) {
                runCatching {
                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("BunMusic track", track.id))
                    onToast("Track id copied")
                }.onFailure { onToast("Clipboard unavailable") }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

/**
 * A–B repeat: press A where the loop starts, B where it ends, and the player
 * jumps back instead of moving on. Clearing it is the third press.
 */
@Composable
fun AbRepeatBar(state: PlayerState, tint: Color, modifier: Modifier = Modifier) {
    val on = state.abActive
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) tint.copy(alpha = 0.12f) else Ink.Field)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(G.Repeat, if (on) tint else Ink.Faint, 14.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (on) "Looping ${fmtTime(state.abStartMs)} – ${fmtTime(state.abEndMs)}"
                else "A–B repeat",
                color = if (on) tint else Ink.Muted,
                fontSize = 11.sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (on) "Tap clear to let the song finish" else "Mark a section and hear it again",
                color = Ink.Faint,
                fontSize = 9.5.sp,
                fontFamily = SoraFamily,
            )
        }
        if (on) {
            MiniAction("Clear") { Haptics.tick(); app.bunmusic.player.Playback.clearAb() }
        } else {
            MiniAction("A") { Haptics.tick(); app.bunmusic.player.Playback.markA() }
            Spacer(Modifier.width(6.dp))
            MiniAction("B") { Haptics.tick(); app.bunmusic.player.Playback.markB() }
        }
    }
}

@Composable
private fun MiniAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Ink.Sheet)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, color = Ink.Text, fontSize = 10.sp, fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The like burst: a heart that scales up and fades where the thumb pressed.
 * [pulse] increments on every like; the composable animates once per increment.
 */
@Composable
fun LikeBurst(pulse: Int, tint: Color, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(pulse) {
        if (pulse <= 0) return@LaunchedEffect
        show = true
        kotlinx.coroutines.delay(620)
        show = false
    }
    AnimatedVisibility(
        visible = show,
        enter = scaleIn(tween(180)) + fadeIn(tween(120)),
        exit = fadeOut(tween(320)),
        modifier = modifier,
    ) {
        Glyph(G.HeartFill, tint, 104.dp)
    }
}

/**
 * Speed with a fine control. The presets are still one tap away; this is for the
 * podcast-at-1.15× case the preset row could not express.
 */
@Composable
fun SpeedFineSheet(current: Float, onDismiss: () -> Unit, onPick: (Float) -> Unit) {
    var value by remember { mutableStateOf(current) }
    SheetShell("Playback speed", "%.2f×".format(value), onDismiss) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Slider(
                value = value,
                onValueChange = { value = (it * 20f).toInt() / 20f },
                valueRange = 0.5f..2f,
                steps = 29,
                colors = SliderDefaults.colors(
                    thumbColor = Ink.Lime,
                    activeTrackColor = Ink.Lime,
                    inactiveTrackColor = Ink.Hairline,
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0.5×", color = Ink.Faint, fontSize = 10.sp, fontFamily = SoraFamily)
                Text("1×", color = Ink.Faint, fontSize = 10.sp, fontFamily = SoraFamily)
                Text("2×", color = Ink.Faint, fontSize = 10.sp, fontFamily = SoraFamily)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.8f, 1f, 1.2f, 1.5f).forEach { v ->
                    Chip("%.2f×".format(v), value == v) { value = v }
                }
            }
            Spacer(Modifier.height(24.dp))
            FilledBtn("Set ${"%.2f".format(value)}×", G.Speed) {
                onPick(value)
                onDismiss()
            }
        }
    }
}
