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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.LocalBunWindow
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Collection
import app.bunmusic.data.Downloads
import app.bunmusic.data.Library
import app.bunmusic.data.Playlist
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import app.bunmusic.player.Playback
import app.bunmusic.player.PlayerState
import kotlinx.coroutines.launch

/** How the liked / recent lists are ordered. */
private enum class Sort(val label: String) {
    ADDED("Added"), AZ("A–Z"), PLAYS("Most played")
}

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
    onToast: (String) -> Unit = {},
) {
    var tab by remember { mutableIntStateOf(0) }
    var playlists by remember { mutableStateOf(Store.playlists()) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(Sort.ADDED) }
    var openAuto by remember { mutableStateOf<String?>(null) }
    // Selection mode: a long press is the usual gesture, but it has to be added to
    // three different row implementations; a Select button in the header works
    // identically everywhere and is discoverable without knowing the gesture.
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    val liked = remember(state.track?.id) { Store.likedTracks() }
    val recents = remember(state.track?.id) { Store.recent(50) }
    val downloads by Downloads.entries.collectAsState()
    val autos = remember(state.track?.id, openAuto) { Library.autoLists() }
    val gutter = LocalBunWindow.current.gutter
    val likedIds = remember(liked) { liked.map { it.id }.toSet() }
    val scope = rememberCoroutineScope()
    val plays = remember(state.track?.id) { Store.playCounts() }

    /** One filter + sort pipeline for every list on this screen. */
    fun shape(list: List<Track>): List<Track> {
        val q = query.trim()
        val filtered = if (q.isEmpty()) list else list.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }
        return when (sort) {
            Sort.ADDED -> filtered
            Sort.AZ -> filtered.sortedBy { it.title.lowercase() }
            Sort.PLAYS -> filtered.sortedByDescending { plays[it.id] ?: 0 }
        }
    }

    val shown = when (tab) {
        1 -> shape(liked)
        2 -> shape(recents)
        else -> emptyList()
    }

    fun toggle(t: Track) {
        selected = if (selected.contains(t.id)) selected - t.id else selected + t.id
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = if (selecting) 96.dp else 24.dp),
    ) {
        item {
            ScreenTitle(
                "Library",
                "${downloads.size} saved · ${liked.size} liked · ${playlists.size} playlists",
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (tab == 1 || tab == 2) {
                            IconBtn(
                                if (selecting) G.Check else G.List,
                                if (selecting) Ink.Lime else Ink.Muted,
                                38.dp, 18.dp,
                            ) {
                                selecting = !selecting
                                if (!selecting) selected = emptySet()
                            }
                        }
                        IconBtn(G.Chart, Ink.Muted, 38.dp, 18.dp, onOpenStats)
                    }
                },
            )
        }

        item {
            Row(Modifier.padding(horizontal = gutter, vertical = 10.dp)) {
                ShortcutCard(
                    "Downloads", "${downloads.size} saved", G.Download, Ink.Mint,
                    Modifier.weight(1f), onOpenDownloads,
                )
                Spacer(Modifier.width(10.dp))
                ShortcutCard(
                    "Stats", "Time, top artists", G.Chart, Ink.Lavender,
                    Modifier.weight(1f), onOpenStats,
                )
            }
        }

        // ---- search: the library had no way to find anything in it
        item {
            Row(
                Modifier
                    .padding(horizontal = gutter, vertical = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ink.Field)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(G.Search, Ink.Faint, 15.dp)
                Spacer(Modifier.width(9.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Find in library", color = Ink.Faint, fontSize = 12.5.sp, fontFamily = SoraFamily)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Ink.Text, fontSize = 12.5.sp, fontFamily = SoraFamily),
                        cursorBrush = SolidColor(Ink.Lime),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Box(Modifier.clickable { query = "" }.padding(2.dp)) {
                        Glyph(G.Close, Ink.Faint, 14.dp)
                    }
                }
            }
        }

        item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = gutter, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Playlists", "Liked", "Recent", "Downloaded").forEachIndexed { i, label ->
                    Chip(label, selected = tab == i) {
                        tab = i
                        selecting = false
                        selected = emptySet()
                    }
                }
            }
        }

        if (tab == 1 || tab == 2) {
            item {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = gutter),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Sort.entries.forEach { s ->
                        Chip(s.label, selected = sort == s) { sort = s }
                    }
                }
            }
            // Play all / shuffle: every other list in the app had these and the
            // two biggest ones did not.
            if (shown.isNotEmpty()) {
                item {
                    Row(
                        Modifier.padding(horizontal = gutter, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledBtn("Play all", G.Play) { onPlay(shown, 0) }
                        GhostBtn("Shuffle", G.Shuffle) { Playback.shufflePlay(shown) }
                    }
                }
            }
        }

        if (tab == 0) {
            if (autos.isNotEmpty()) {
                item { SectionHeader("Made for you") }
                items(autos, key = { it.id }) { a ->
                    AutoRow(
                        auto = a,
                        expanded = openAuto == a.id,
                        onClick = { openAuto = if (openAuto == a.id) null else a.id },
                        onPlay = { onPlay(a.tracks, 0) },
                        onShuffle = { Playback.shufflePlay(a.tracks) },
                    )
                    if (openAuto == a.id) {
                        // Capped at 30 rows and rendered inside one item: a nested
                        // LazyColumn is not allowed, and 30 rows is the most this
                        // list will ever hold.
                        Column {
                            a.tracks.take(30).forEachIndexed { i, t ->
                                Box(Modifier.padding(horizontal = 8.dp)) {
                                    TrackRow(
                                        track = t,
                                        index = i + 1,
                                        playing = state.isPlaying,
                                        isCurrent = state.track?.id == t.id,
                                        liked = likedIds.contains(t.id),
                                        downloaded = downloadedIds.contains(t.id),
                                        onPlay = { onPlay(a.tracks, i) },
                                        onLike = { Store.toggleLike(t.id) },
                                        onMore = { onMore(t) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = gutter, vertical = 10.dp)
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
                items(playlists, key = { it.id }) { p ->
                    PlaylistRow(p, onClick = { onOpenPlaylist(p.id) })
                }
            }
        }

        if (tab == 1) {
            if (liked.isEmpty()) {
                item { EmptyState(G.Heart, "Nothing liked yet", "Tap the heart on any track and it lands here.") }
            } else if (shown.isEmpty()) {
                item { EmptyState(G.Search, "No match", "Nothing liked matches \u201c$query\u201d.") }
            } else {
                itemsIndexed(shown, key = { i, t -> t.id + "#" + i }) { i, t ->
                    Box(Modifier.padding(horizontal = 8.dp)) {
                        TrackRow(
                            track = t,
                            playing = state.isPlaying,
                            isCurrent = state.track?.id == t.id,
                            liked = true,
                            downloaded = downloadedIds.contains(t.id),
                            onPlay = { if (selecting) toggle(t) else onPlay(shown, i) },
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
                itemsIndexed(shown, key = { i, t -> t.id + "#" + i }) { i, t ->
                    Box(Modifier.padding(horizontal = 8.dp)) {
                        TrackRow(
                            track = t,
                            playing = state.isPlaying,
                            isCurrent = state.track?.id == t.id,
                            liked = likedIds.contains(t.id),
                            downloaded = downloadedIds.contains(t.id),
                            onPlay = { if (selecting) toggle(t) else onPlay(shown, i) },
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
                itemsIndexed(downloads, key = { _, e -> e.trackId }) { i, e ->
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

    if (selecting) {
        val picks = shown.filter { selected.contains(it.id) }
        SelectionBar(
            count = picks.size,
            onLike = {
                Store.likeMany(picks.map { it.id }, true)
                onToast("Liked ${picks.size}")
                selecting = false
                selected = emptySet()
            },
            onQueue = {
                picks.forEach { Playback.addToQueue(it) }
                onToast("${picks.size} added to the queue")
                selecting = false
                selected = emptySet()
            },
            onDownload = {
                val added = Downloads.enqueueAll(picks)
                onToast(
                    when {
                        added == 0 -> "Already downloaded or queued"
                        added < picks.size -> "Queued $added — the rest were already there"
                        else -> "Queued $added for download"
                    }
                )
                selecting = false
                selected = emptySet()
            },
            onClear = {
                selected = emptySet()
                selecting = false
            },
        )
    }
}

/**
 * The bar that appears while a selection is live. It floats over the list rather
 * than pushing it, so the rows do not jump when a selection starts.
 */
@Composable
private fun SelectionBar(
    count: Int,
    onLike: () -> Unit,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onClear: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 90.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Ink.Sheet)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "$count",
                color = Ink.Lime,
                fontSize = 14.sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            IconBtn(G.Heart, if (count > 0) Ink.Text else Ink.Faint, 40.dp, 18.dp) { if (count > 0) onLike() }
            IconBtn(G.Queue, if (count > 0) Ink.Text else Ink.Faint, 40.dp, 18.dp) { if (count > 0) onQueue() }
            IconBtn(G.Download, if (count > 0) Ink.Text else Ink.Faint, 40.dp, 18.dp) { if (count > 0) onDownload() }
            IconBtn(G.Close, Ink.Faint, 40.dp, 17.dp) { onClear() }
        }
    }
}

/** An auto playlist: collapsed it is one row, expanded it is the tracks themselves. */
@Composable
private fun AutoRow(
    auto: Library.Auto,
    expanded: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    val gutter = LocalBunWindow.current.gutter
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(15.dp)).background(Ink.Lime.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            val first = auto.tracks.firstOrNull()
            if (first != null) {
                ArtImage(first.artUrl, first.artAsset, Modifier.fillMaxSize(), RoundedCornerShape(15.dp))
            } else {
                Glyph(G.Sparkle, Ink.Lime, 20.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                auto.title,
                color = Ink.Text,
                fontSize = 14.sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(auto.subtitle, color = Ink.Faint, fontSize = 11.5.sp, fontFamily = SoraFamily)
        }
        IconBtn(G.Play, Ink.Muted, 36.dp, 15.dp, onPlay)
        IconBtn(G.Shuffle, Ink.Muted, 36.dp, 15.dp, onShuffle)
        Glyph(if (expanded) G.Back else G.Forward, Ink.Faint, 15.dp)
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
            .background(if (Ink.isLight) Brush.linearGradient(listOf(Ink.Field, Ink.Card)) else Brush.linearGradient(listOf(Color(0xFF1E1A28), Color(0xFF171320))))
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
            Text(subtitle, color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily)
        }
    }
}

@Composable
private fun PlaylistRow(p: Playlist, onClick: () -> Unit) {
    val gutter = LocalBunWindow.current.gutter
    val first = Store.playlistTracks(p.id).firstOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = gutter, vertical = 8.dp),
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
