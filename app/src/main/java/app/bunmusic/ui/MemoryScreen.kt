package app.bunmusic.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.BunEase
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Haptics
import app.bunmusic.core.Ink
import app.bunmusic.core.LocalReducedMotion
import app.bunmusic.core.SerifFamily
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Listening
import app.bunmusic.data.Memory
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import app.bunmusic.data.Wrapped
import app.bunmusic.player.Playback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Time machine.
 *
 * Two halves, and both are about the listener rather than the catalogue: a
 * profile computed from the last ninety days, and the same calendar date in
 * earlier years. Nothing here is generated — every number is counted from plays
 * that actually happened, which is the only reason a screen like this is worth
 * opening twice.
 */
@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val loaded by produceState<Loaded?>(null) {
        value = withContext(Dispatchers.IO) {
            val events = Store.events()
            val byId = Store.tracksById()
            val now = System.currentTimeMillis()
            val offset = Listening.zoneOffset()
            val days = events.map { Listening.dayIndex(it.atMs, offset) }.toSet()
            Loaded(
                dna = Memory.dna(events, { byId[it] }, now, offset),
                memories = Memory.onThisDay(events, { byId[it] }, now, offset),
                streak = Memory.currentStreak(days, Listening.dayIndex(now, offset)),
                best = Wrapped.longestStreak(days.toList()),
                days = days.size,
                plays = events.size,
            )
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 34.dp),
    ) {
        item {
            val context = LocalContext.current
            BackBar("Time machine", onBack, trailing = {
                // Only offered once there is a profile worth sharing. A share
                // button that produces an empty pentagon is worse than no button.
                val dna = loaded?.dna
                if (dna != null) {
                    IconBtn(G.Share, Ink.Muted, 38.dp, 17.dp) {
                        Haptics.tick()
                        ShareCard.shareDna(context, dna)
                    }
                }
            })
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Display("What this app remembers", 29)
                Spacer(Modifier.height(5.dp))
                val data = loaded
                Text(
                    if (data == null) "Counting…"
                    else "${data.plays} plays across ${data.days} days, on this device only.",
                    color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily,
                )
            }
        }

        val data = loaded
        if (data == null) {
            item { Spacer(Modifier.height(60.dp)) }
            return@LazyColumn
        }

        // ------------------------------------------------------------- streak
        item {
            Panel(Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth()) {
                Row(
                    Modifier.padding(18.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(54.dp).clip(CircleShape)
                            .background(Ink.Lime.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) { Glyph(G.Wave, Ink.Lime, 24.dp, 2.dp) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (data.streak == 0) "No streak running"
                            else "${data.streak} ${if (data.streak == 1) "day" else "days"} in a row",
                            color = Ink.Text, fontSize = 17.sp, fontFamily = SerifFamily,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (data.best > 0) "Best so far: ${data.best} days." else "Play something to start one.",
                            color = Ink.Muted, fontSize = 11.5.sp, fontFamily = SoraFamily,
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------------- DNA
        item { SectionLabel("Listening DNA") }
        item {
            val dna = data.dna
            if (dna == null) {
                Panel(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "Not enough yet",
                            color = Ink.Text, fontSize = 14.sp, fontFamily = SoraFamily,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "A profile needs ${Memory.MIN_PLAYS} plays in the last " +
                                "${Memory.WINDOW_DAYS} days. You have ${data.plays} in total — " +
                                "this fills in on its own, and it is not worth drawing a " +
                                "shape from a handful of songs.",
                            color = Ink.Muted, fontSize = 11.5.sp, fontFamily = SoraFamily,
                            lineHeight = 17.sp,
                        )
                    }
                }
            } else {
                Panel(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        DnaRadar(dna, Modifier.size(216.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${dna.plays} plays over ${dna.days} days",
                            color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily,
                        )
                        Spacer(Modifier.height(16.dp))
                        dna.axes.forEach { axis ->
                            AxisRow(axis)
                            Spacer(Modifier.height(11.dp))
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "These describe how you listen, not what the songs sound " +
                                "like — the app does not analyse audio, so it does not " +
                                "claim to know whether a track is fast or happy.",
                            color = Ink.Faint, fontSize = 10.5.sp, fontFamily = SoraFamily,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------- on this day
        item { SectionLabel("On this day") }
        if (data.memories.isEmpty()) {
            item {
                Panel(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "Nothing from this date",
                            color = Ink.Text, fontSize = 14.sp, fontFamily = SoraFamily,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "The app looks up to ${Memory.YEARS_BACK} years back for plays " +
                                "made on ${todayLabel()}. Today is the first one, or the " +
                                "history was cleared.",
                            color = Ink.Muted, fontSize = 11.5.sp, fontFamily = SoraFamily,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }
        } else {
            items(data.memories.size) { i ->
                val m = data.memories[i]
                MemoryCard(m)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Ink.Faint,
        fontSize = 10.sp,
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.9.sp,
        modifier = Modifier.padding(start = 24.dp, top = 22.dp, bottom = 8.dp),
    )
}

private data class Loaded(
    val dna: Memory.Dna?,
    val memories: List<Memory.DayMemory>,
    val streak: Int,
    val best: Int,
    val days: Int,
    val plays: Int,
)

@Composable
private fun DnaRadar(dna: Memory.Dna, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val reveal = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(dna) {
        if (reduced) {
            reveal.snapTo(1f)
        } else {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(720, easing = BunEase.Emphasised))
        }
    }
    val values = dna.axes.map { it.value }
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f - 8.dp.toPx()
        if (r <= 0f) return@Canvas

        fun polygon(points: List<Pair<Float, Float>>): Path = Path().apply {
            points.forEachIndexed { i, (x, y) ->
                if (i == 0) moveTo(cx + x, cy + y) else lineTo(cx + x, cy + y)
            }
            close()
        }

        // Three rings, not five: five turns the chart into a spider web and the
        // shape it is meant to describe stops being the loudest thing on it.
        for (ring in 1..3) {
            val pts = Memory.radar(List(values.size) { 100 }, r * ring / 3f)
            drawPath(polygon(pts), Ink.Hairline, style = Stroke(width = 1.dp.toPx()))
        }
        Memory.radar(List(values.size) { 100 }, r).forEach { (x, y) ->
            drawLine(Ink.Hairline, Offset(cx, cy), Offset(cx + x, cy + y), 1.dp.toPx())
        }

        val grown = values.map { (it * reveal.value).toInt() }
        val pts = Memory.radar(grown, r)
        if (pts.isNotEmpty()) {
            val path = polygon(pts)
            drawPath(
                path,
                brush = Brush.radialGradient(
                    listOf(Ink.Lime.copy(alpha = 0.38f), Ink.Lavender.copy(alpha = 0.10f)),
                    center = Offset(cx, cy),
                    radius = r,
                ),
            )
            drawPath(path, Ink.Lime.copy(alpha = 0.85f), style = Stroke(width = 1.7.dp.toPx()))
            pts.forEach { (x, y) ->
                drawCircle(Ink.Lime, 2.8.dp.toPx(), Offset(cx + x, cy + y))
            }
        }
    }
}

@Composable
private fun AxisRow(axis: Memory.Axis) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                axis.label,
                color = Ink.Text, fontSize = 12.sp, fontFamily = SoraFamily,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(8.dp))
            Text(axis.hint, color = Ink.Faint, fontSize = 10.sp, fontFamily = SoraFamily)
            Spacer(Modifier.weight(1f))
            Text(
                "${axis.value}",
                color = Ink.Text, fontSize = 12.sp, fontFamily = SoraFamily,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Ink.Field)
        ) {
            if (axis.value > 0) {
                Box(
                    Modifier.fillMaxWidth((axis.value / 100f).coerceIn(0.01f, 1f)).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(listOf(Ink.Lime, Ink.Lavender))
                        )
                )
            }
        }
    }
}

@Composable
private fun MemoryCard(m: Memory.DayMemory) {
    // Formatted from the epoch day, not from a timestamp: midnight UTC is the
    // previous evening in the Americas, and a memory dated a day early is exactly
    // the kind of wrong this screen cannot afford.
    val date = remember(m.epochDay) {
        java.time.LocalDate.ofEpochDay(m.epochDay)
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
    }
    Panel(Modifier.padding(horizontal = 20.dp, vertical = 6.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        Memory.ago(m.yearsAgo),
                        color = Ink.Text, fontSize = 16.sp, fontFamily = SerifFamily,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "$date · ${m.plays} ${if (m.plays == 1) "play" else "plays"} · " +
                            "${m.tracks.size} ${if (m.tracks.size == 1) "track" else "tracks"}",
                        color = Ink.Faint, fontSize = 10.5.sp, fontFamily = SoraFamily,
                    )
                }
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(Ink.Lime)
                        .tapable(scale = 0.94f) {
                            Haptics.tick()
                            Playback.play(m.tracks, 0)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Glyph(G.Play, Ink.OnAccent, 12.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Play",
                            color = Ink.OnAccent, fontSize = 11.5.sp,
                            fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            m.tracks.take(4).forEach { t ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .tapable(scale = 0.99f) { Playback.play(m.tracks, m.tracks.indexOf(t)) }
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(3.dp).clip(CircleShape).background(Ink.Lime.copy(alpha = 0.6f)))
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title,
                            color = Ink.Text, fontSize = 12.5.sp, fontFamily = SoraFamily,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Text(
                            t.artist,
                            color = Ink.Faint, fontSize = 10.5.sp, fontFamily = SoraFamily,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (m.tracks.size > 4) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "+ ${m.tracks.size - 4} more from that day",
                    color = Ink.Faint, fontSize = 10.5.sp, fontFamily = SoraFamily,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

private fun todayLabel(): String =
    java.time.LocalDate.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))
