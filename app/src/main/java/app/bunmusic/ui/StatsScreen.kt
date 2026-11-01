package app.bunmusic.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Downloads
import app.bunmusic.data.Store
import app.bunmusic.data.Track

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val stats = remember { Store.stats() }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 30.dp),
    ) {
        item { BackBar("Stats", onBack) }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Display("Your listening", 29)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Counted on this device only — nothing leaves the app.",
                    color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily,
                )
            }
        }

        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Time", fmtDuration(stats.totalMs), Modifier.weight(1f))
                StatTile("Plays", "${stats.plays}", Modifier.weight(1f))
                StatTile("Days", "${stats.days}", Modifier.weight(1f))
            }
        }

        item {
            Panel(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Last 7 days", color = Ink.Text, fontSize = 13.5.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(16.dp))
                    WeekBars(stats.week)
                }
            }
        }

        if (stats.topArtists.isNotEmpty()) {
            item { SectionHeader("Top artists") }
            items(stats.topArtists) { pair ->
                val max = stats.topArtists.first().second.coerceAtLeast(1)
                BarRow(pair.first, pair.second, pair.second.toFloat() / max, Ink.Lavender)
            }
        }

        if (stats.topTracks.isNotEmpty()) {
            item { SectionHeader("Top tracks") }
            items(stats.topTracks) { pair ->
                val max = stats.topTracks.first().second.coerceAtLeast(1)
                BarRow(pair.first, pair.second, pair.second.toFloat() / max, Ink.Mint)
            }
        }

        if (stats.plays == 0) {
            item { EmptyState(G.Chart, "No listening yet", "Play a few tracks and this page fills in.") }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (Ink.isLight) Brush.linearGradient(listOf(Ink.Field, Ink.Card)) else Brush.linearGradient(listOf(Color(0xFF1F1A2A), Color(0xFF171320))))
            .padding(14.dp),
    ) {
        Text(label.uppercase(), color = Ink.Faint, fontSize = 10.sp, fontFamily = SoraFamily, letterSpacing = 0.9.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = Ink.Text, fontSize = 18.sp, fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WeekBars(week: List<Int>) {
    val max = (week.maxOrNull() ?: 0).coerceAtLeast(1)
    val labels = remember {
        val fmt = java.text.SimpleDateFormat("EEEEE", java.util.Locale.getDefault())
        val cal = java.util.Calendar.getInstance()
        (6 downTo 0).map { back ->
            val c = cal.clone() as java.util.Calendar
            c.add(java.util.Calendar.DAY_OF_YEAR, -back)
            fmt.format(c.time)
        }
    }
    Row(
        Modifier.fillMaxWidth().height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        week.forEachIndexed { i, v ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((8 + (v.toFloat() / max) * 62).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (v > 0) Ink.Lime.copy(alpha = 0.85f) else Ink.Hairline)
                )
                Spacer(Modifier.height(6.dp))
                Text(labels.getOrElse(i) { "" }, color = Ink.Faint, fontSize = 10.sp, fontFamily = SoraFamily)
            }
        }
    }
}

@Composable
private fun BarRow(label: String, count: Int, fraction: Float, tint: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Ink.Text,
            fontSize = 12.5.sp,
            fontFamily = SoraFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(130.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Ink.Hairline),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(tint.copy(alpha = 0.8f))
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("$count", color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily)
    }
}

@Composable
fun DownloadsScreen(onBack: () -> Unit, onPlay: (List<Track>, Int) -> Unit) {
    val entries by Downloads.entries.collectAsState()
    val queue by Downloads.queue.collectAsState()
    val speed by Downloads.speed.collectAsState()
    val paused by Downloads.paused.collectAsState()

    val tracks = remember(entries) { entries.mapNotNull { Store.track(it.trackId) } }
    val active = queue.filter { it.state != Downloads.State.FAILED }
    val failed = queue.filter { it.state == Downloads.State.FAILED }
    val waiting = active.count { it.state == Downloads.State.WAITING }
    val running = active.count { it.state == Downloads.State.RUNNING }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 30.dp),
    ) {
        item { BackBar("Downloads", onBack) }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Display("Offline", 30)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${entries.size} tracks · ${fmtBytes(entries.sumOf { it.bytes })} on this device",
                    color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily,
                )
            }
        }

        // ------------------------------------------------------------ the queue
        if (active.isNotEmpty() || failed.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                paused -> "Paused"
                                running > 0 && speed > 0 -> "Downloading $running · ${fmtSpeed(speed)}"
                                running > 0 -> "Downloading $running"
                                waiting > 0 -> "Waiting for Wi-Fi"
                                else -> "${active.size} in the queue"
                            },
                            color = Ink.Text, fontSize = 13.sp, fontFamily = SoraFamily,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "${active.size} queued · ${failed.size} failed",
                            color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily,
                        )
                    }
                    if (active.isNotEmpty()) {
                        GhostBtn(if (paused) "Resume" else "Pause", if (paused) G.Play else G.Pause) {
                            if (paused) Downloads.resumeAll() else Downloads.pauseAll()
                        }
                    }
                }
            }

            if (waiting > 0) {
                item {
                    // The switch that used to be decoration. Saying why a download
                    // is sitting still is the whole difference between a queue and
                    // a bug.
                    Text(
                        "Wi-Fi only is on — ${if (waiting == 1) "one download is" else "$waiting downloads are"} held back until this phone is on Wi-Fi.",
                        color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                }
            }

            items(active, key = { "q" + it.track.id }) { job -> JobRow(job) }

            items(failed, key = { "f" + it.track.id }) { job -> JobRow(job) }

            if (failed.isNotEmpty()) {
                item {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        FilledBtn("Retry failed", G.Refresh) { Downloads.retryAllFailed() }
                        Spacer(Modifier.width(10.dp))
                        GhostBtn("Clear", G.Trash) { Downloads.clearFailed() }
                    }
                }
            }
        }

        // --------------------------------------------------------- what is here
        if (entries.isEmpty() && active.isEmpty() && failed.isEmpty()) {
            item {
                EmptyState(
                    G.Download, "Nothing downloaded yet",
                    "Open a track's menu and tap Download offline.",
                )
            }
        } else if (entries.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Downloaded",
                        color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily,
                        modifier = Modifier.weight(1f),
                    )
                    FilledBtn("Play all", G.Play) { onPlay(tracks, 0) }
                    Spacer(Modifier.width(10.dp))
                    GhostBtn("Remove all", G.Trash) { Downloads.clearAll() }
                }
            }
            items(entries, key = { "e" + it.trackId }) { e ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clickable {
                            val i = tracks.indexOfFirst { it.id == e.trackId }
                            if (i >= 0) onPlay(tracks, i)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtImage(
                        url = e.artUrl,
                        asset = null,
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            e.title,
                            color = Ink.Text, fontSize = 13.5.sp, fontFamily = SoraFamily,
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${e.artist} · ${fmtBytes(e.bytes)}",
                            color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).clickable { Downloads.remove(e.trackId) },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(G.Trash, Ink.Faint, 15.dp) }
                }
            }
        }
    }
}

/** One queued, running, held-back or failed download. */
@Composable
private fun JobRow(job: Downloads.Job) {
    val failed = job.state == Downloads.State.FAILED
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(
            url = job.track.artUrl,
            asset = job.track.artAsset,
            modifier = Modifier.size(46.dp),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                job.track.title,
                color = if (failed) Ink.Muted else Ink.Text,
                fontSize = 13.5.sp, fontFamily = SoraFamily,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                when (job.state) {
                    Downloads.State.QUEUED -> "Queued"
                    Downloads.State.WAITING -> "Waiting for Wi-Fi"
                    Downloads.State.FAILED -> job.error ?: "Failed"
                    Downloads.State.RUNNING ->
                        if (job.totalBytes > 0) {
                            "${(job.fraction * 100).toInt()}% · ${fmtBytes(job.doneBytes)} of ${fmtBytes(job.totalBytes)}"
                        } else {
                            "Starting… ${fmtBytes(job.doneBytes)}"
                        }
                },
                color = if (failed) Ink.Faint else Ink.Muted,
                fontSize = 11.sp, fontFamily = SoraFamily,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!failed) {
                Spacer(Modifier.height(6.dp))
                ThinBar(
                    fraction = job.fraction,
                    indeterminate = job.indeterminate,
                    tint = if (job.state == Downloads.State.RUNNING) Ink.Lime else Ink.Faint,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(32.dp).clip(CircleShape).clickable {
                if (failed) Downloads.retry(job.track.id) else Downloads.cancel(job.track.id)
            },
            contentAlignment = Alignment.Center,
        ) {
            Glyph(if (failed) G.Refresh else G.Close, Ink.Faint, 14.dp)
        }
    }
}

/**
 * A five-pixel bar. Indeterminate means the server did not send a length, and a
 * bar that sits at 0 % in that case reads as "stuck", so a block slides across.
 */
@Composable
private fun ThinBar(fraction: Float, indeterminate: Boolean, tint: Color) {
    val transition = rememberInfiniteTransition(label = "download")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_300, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val track = Ink.Line
    Canvas(Modifier.fillMaxWidth().height(5.dp)) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = track, cornerRadius = radius)
        if (indeterminate) {
            val w = size.width * 0.28f
            drawRoundRect(
                color = tint,
                topLeft = Offset((size.width - w) * phase, 0f),
                size = Size(w, size.height),
                cornerRadius = radius,
            )
        } else if (fraction > 0f) {
            drawRoundRect(
                color = tint,
                size = Size((size.width * fraction).coerceAtLeast(size.height), size.height),
                cornerRadius = radius,
            )
        }
    }
}

/** "1.4 MB/s" — one decimal below ten, none above, because the extra digit is noise. */
internal fun fmtSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
    bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
    bytesPerSecond < 10 * 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSecond / 1048576.0)
    else -> "${bytesPerSecond / (1024 * 1024)} MB/s"
}
