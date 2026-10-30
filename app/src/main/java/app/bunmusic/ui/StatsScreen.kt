package app.bunmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    val tracks = remember(entries) { entries.mapNotNull { Store.track(it.trackId) } }

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

        if (entries.isEmpty()) {
            item {
                EmptyState(
                    G.Download, "Nothing downloaded yet",
                    "Open a track's ⋮ menu and tap Download offline.",
                )
            }
        } else {
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    FilledBtn("Play all", G.Play) { onPlay(tracks, 0) }
                    Spacer(Modifier.width(10.dp))
                    GhostBtn("Remove all", G.Trash) { Downloads.clearAll() }
                }
            }
            items(entries) { e ->
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
