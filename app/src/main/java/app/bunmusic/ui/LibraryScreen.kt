package app.bunmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.bunmusic.data.Catalog
import app.bunmusic.data.Collection
import app.bunmusic.data.Downloads
import app.bunmusic.data.Playlist
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import app.bunmusic.player.PlayerState

@Composable
fun LibraryScreen(
    state: PlayerState,
    downloadedIds: Set<String>,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenStats: () -> Unit,
    onMore: (Track) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var playlists by remember { mutableStateOf(Store.playlists()) }
    val liked = remember(state.track?.id) { Store.likedTracks() }
    val recents = remember(state.track?.id) { Store.recent(50) }
    val downloads by Downloads.entries.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            ScreenTitle(
                "Library",
                "${Catalog.tracks.size} offline · ${liked.size} liked · ${playlists.size} playlists",
                trailing = {
                    IconBtn(G.Chart, Ink.Muted, 38.dp, 18.dp, onOpenStats)
                },
            )
        }

        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                ShortcutCard(
                    "Downloads", "${downloads.size} saved", G.Download, Ink.Mint,
                    Modifier.weight(1f), onOpenDownloads,
                )
                Spacer(Modifier.width(10.dp))
                ShortcutCard(
                    "Offline", "${Catalog.tracks.size} bundled", G.Offline, Ink.Lavender,
                    Modifier.weight(1f),
                ) {
                    onOpenCollection(Catalog.collections.first())
                }
            }
        }

        item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Playlists", "Liked", "Recent", "Downloaded").forEachIndexed { i, label ->
                    Chip(label, selected = tab == i) { tab = i }
                }
            }
        }

        if (tab == 0) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Ink.Card)
                        .clickable {
                            val p = Store.createPlaylist("My playlist ${playlists.size + 1}")
                            playlists = Store.playlists()
                            onOpenPlaylist(p.id)
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Ink.Lime.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) { Glyph(G.Plus, Ink.Lime, 17.dp) }
                    Spacer(Modifier.width(12.dp))
                    Text("New playlist", color = Ink.Text, fontSize = 13.5.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
                }
            }

            if (playlists.isEmpty()) {
                item {
                    EmptyState(
                        G.List, "No playlists yet",
                        "Build one from any track's menu — it stays on this device.",
                    )
                }
            } else {
                items(playlists) { p ->
                    PlaylistRow(p, onClick = { onOpenPlaylist(p.id) })
                }
            }

            item { SectionHeader("Bundled collections") }
            items(Catalog.collections.chunked(2)) { pair ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    pair.forEach { c ->
                        Box(Modifier.weight(1f)) { SquareCard(c, onClick = { onOpenCollection(c) }, width = 160.dp) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (tab == 1) {
            if (liked.isEmpty()) {
                item { EmptyState(G.Heart, "Nothing liked yet", "Tap the heart on any track and it lands here.") }
            } else {
                items(liked.size) { i ->
                    val t = liked[i]
                    Box(Modifier.padding(horizontal = 8.dp)) {
                        TrackRow(
                            track = t,
                            playing = state.isPlaying,
                            isCurrent = state.track?.id == t.id,
                            liked = true,
                            downloaded = downloadedIds.contains(t.id),
                            onPlay = { onPlay(liked, i) },
                            onLike = { Store.toggleLike(t.id) },
                            onMore = { onMore(t) },
                        )
                    }
                }
            }
        }

        if (tab == 2) {
            if (recents.isEmpty()) {
                item { EmptyState(G.Clock, "No history yet", "Play something — it shows up here.") }
            } else {
                items(recents.size) { i ->
                    val t = recents[i]
                    Box(Modifier.padding(horizontal = 8.dp)) {
                        TrackRow(
                            track = t,
                            playing = state.isPlaying,
                            isCurrent = state.track?.id == t.id,
                            liked = Store.isLiked(t.id),
                            downloaded = downloadedIds.contains(t.id),
                            onPlay = { onPlay(recents, i) },
                            onLike = { Store.toggleLike(t.id) },
                            onMore = { onMore(t) },
                        )
                    }
                }
            }
        }

        if (tab == 3) {
            if (downloads.isEmpty()) {
                item {
                    EmptyState(
                        G.Download, "Nothing downloaded",
                        "Open a track's menu and choose Download offline.",
                    )
                }
            } else {
                items(downloads.size) { i ->
                    val e = downloads[i]
                    val t = Store.track(e.trackId)
                    if (t != null) {
                        Box(Modifier.padding(horizontal = 8.dp)) {
                            TrackRow(
                                track = t,
                                playing = state.isPlaying,
                                isCurrent = state.track?.id == t.id,
                                downloaded = true,
                                onPlay = {
                                    val list = downloads.mapNotNull { Store.track(it.trackId) }
                                    onPlay(list, list.indexOf(t).coerceAtLeast(0))
                                },
                                onMore = { onMore(t) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutCard(
    title: String,
    subtitle: String,
    glyph: G,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1E1A28), Color(0xFF171320))))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Glyph(glyph, tint, 17.dp) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = Ink.Text, fontSize = 12.5.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Ink.Faint, fontSize = 10.5.sp, fontFamily = SoraFamily)
        }
    }
}

@Composable
private fun PlaylistRow(p: Playlist, onClick: () -> Unit) {
    val first = Store.playlistTracks(p.id).firstOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Ink.Card),
            contentAlignment = Alignment.Center,
        ) {
            if (first != null) {
                ArtImage(first.artUrl, first.artAsset, Modifier.fillMaxSize(), RoundedCornerShape(15.dp))
            } else {
                Glyph(G.List, Ink.Faint, 20.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                p.name,
                color = Ink.Text,
                fontSize = 14.sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${p.size} tracks",
                color = Ink.Faint,
                fontSize = 11.5.sp,
                fontFamily = SoraFamily,
            )
        }
        Glyph(G.Forward, Ink.Faint, 15.dp)
    }
}
