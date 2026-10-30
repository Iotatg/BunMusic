package app.bunmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Ink
import app.bunmusic.core.SerifFamily
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Track
import app.bunmusic.data.YtMusic
import app.bunmusic.player.Playback
import app.bunmusic.player.PlayerState

/**
 * Hotlist — the ranked chart, as a plain numbered list.
 *
 * Charcoal's fourth tab. YouTube Music's charts page is a set of shelves; the
 * first one that actually holds a playable list is the top-songs chart, so that
 * is what we open and rank.
 */
@Composable
fun HotlistScreen(
    state: PlayerState,
    downloadedIds: Set<String>,
    onPlay: (List<Track>, Int) -> Unit,
    onMore: (Track) -> Unit,
) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var chartName by remember { mutableStateOf("Top songs") }

    LaunchedEffect(Unit) {
        val outcome = runCatching {
            val shelves = YtMusic.charts()
            val shelf = shelves.firstOrNull { it.items.isNotEmpty() }
            val target = shelf?.items?.firstOrNull()
            val id = target?.browseId.orEmpty()
            if (id.isBlank()) emptyList() else {
                chartName = shelf?.title?.ifBlank { "Top songs" } ?: "Top songs"
                YtMusic.collection(id).tracks
            }
        }
        tracks = outcome.getOrNull().orEmpty()
        failed = outcome.isFailure
        loading = false
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(
                    "HOTLIST",
                    color = Ink.Faint,
                    fontSize = 10.sp,
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.9.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    chartName,
                    color = Ink.Text,
                    fontSize = 34.sp,
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.2).sp,
                    lineHeight = 38.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (loading) "Reading the chart…" else "${tracks.size} songs · updated hourly",
                    color = Ink.Muted,
                    fontSize = 12.sp,
                    fontFamily = SoraFamily,
                )
                if (tracks.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledBtn("Play all", G.Play) { onPlay(tracks, 0) }
                        GhostBtn("Shuffle", G.Shuffle) { Playback.shufflePlay(tracks) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (loading) {
            items(8) { ShimmerRow() }
        } else if (failed || tracks.isEmpty()) {
            item {
                EmptyState(
                    G.Offline,
                    "Chart unavailable",
                    "YouTube didn't answer. Bundled tracks still play offline.",
                )
            }
        } else {
            items(tracks.size) { i ->
                val t = tracks[i]
                TrackRow(
                    track = t,
                    index = i + 1,
                    playing = state.isPlaying,
                    isCurrent = state.track?.id == t.id,
                    downloaded = downloadedIds.contains(t.id),
                    onPlay = { onPlay(tracks, i) },
                    onMore = { onMore(t) },
                )
            }
        }
    }
}
