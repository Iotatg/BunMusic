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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Catalog
import app.bunmusic.data.Collection
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import app.bunmusic.player.PlayerState

@Composable
fun LibraryScreen(
    state: PlayerState,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenCollection: (Collection) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val likedIds = remember { Store.likes() }
    val recents = remember { Store.recent() }
    val likedTracks = remember {
        (recents + Catalog.tracks).distinctBy { it.id }.filter { likedIds.contains(it.id) }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                Display("Library", 30, Ink.Text)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${Catalog.tracks.size} offline · ${likedTracks.size} liked · ${recents.size} recent",
                    color = Ink.Muted,
                    fontSize = 12.sp,
                    fontFamily = SoraFamily,
                )
            }
        }

        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Playlists", "Liked", "Recent").forEachIndexed { i, label ->
                    val active = tab == i
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (active) Ink.Lime else Ink.Card)
                            .clickable { tab = i }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text(
                            label,
                            color = if (active) Ink.OnAccent else Ink.Muted,
                            fontSize = 12.sp,
                            fontFamily = SoraFamily,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        if (tab == 0) {
            item { DownloadsCard() }
            item { SectionHeader("Your playlists") }
            items(Catalog.collections.chunked(2)) { pair ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    pair.forEach { c ->
                        Box(Modifier.weight(1f)) {
                            SquareCard(c, onClick = { onOpenCollection(c) }, width = 160.dp)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (tab == 1) {
            if (likedTracks.isEmpty()) {
                item { EmptyHint("Nothing liked yet", "Tap the heart on any track and it lands here.") }
            } else {
                items(likedTracks) { t ->
                    Box(Modifier.padding(horizontal = 8.dp)) {
                        TrackRow(
                            track = t,
                            playing = state.isPlaying,
                            isCurrent = state.track?.id == t.id,
                            onPlay = { onPlay(likedTracks, likedTracks.indexOf(t)) },
                        )
                    }
                }
            }
        }

        if (tab == 2) {
            if (recents.isEmpty()) {
                item { EmptyHint("No history yet", "Play something — it will show up here.") }
            } else {
                items(recents) { t ->
                    Box(Modifier.padding(horizontal = 8.dp)) {
                        TrackRow(
                            track = t,
                            playing = state.isPlaying,
                            isCurrent = state.track?.id == t.id,
                            onPlay = { onPlay(recents, recents.indexOf(t)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsCard() {
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1E2A22), Color(0xFF151D19))))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Ink.Mint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Glyph(G.Download, Ink.Mint, 19.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Bundled offline", color = Ink.Text, fontSize = 13.5.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
            Text("6 tracks · always available, no data", color = Ink.Muted, fontSize = 11.5.sp, fontFamily = SoraFamily)
        }
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(Ink.Mint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Glyph(G.Check, Ink.Mint, 14.dp) }
    }
}

@Composable
private fun EmptyHint(title: String, body: String) {
    Column(Modifier.padding(20.dp)) {
        Text(title, color = Ink.Text, fontSize = 14.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(body, color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily)
    }
}
