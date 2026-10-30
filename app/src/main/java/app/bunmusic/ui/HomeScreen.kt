package app.bunmusic.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.bunmusic.core.SerifFamily
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Catalog
import app.bunmusic.data.Collection
import app.bunmusic.data.Mood
import app.bunmusic.data.Shelf
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import app.bunmusic.data.YtMusic
import app.bunmusic.player.PlayerState

@Composable
fun HomeScreen(
    state: PlayerState,
    downloadedIds: Set<String>,
    onOpenSearch: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onOpenMood: (Mood) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenPlayer: () -> Unit,
    onMore: (Track) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var shelves by remember { mutableStateOf<List<Shelf>>(emptyList()) }
    var moods by remember { mutableStateOf<List<Mood>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val recents = remember(state.track?.id) { Store.recent(6) }

    LaunchedEffect(attempt) {
        loading = true
        failed = false
        runCatching { YtMusic.home() }
            .onSuccess { shelves = it }
            .onFailure { failed = true }
        runCatching { YtMusic.explore() }.onSuccess { moods = it.moods.take(8) }
        loading = false
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "BUNMUSIC",
                    color = Ink.Text,
                    fontSize = 13.sp,
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.weight(1f))
                IconBtn(G.Gear, Ink.Muted, 38.dp, 18.dp, onOpenSettings)
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Display(greeting(), 33)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Six originals offline, plus everything on YouTube Music.",
                    color = Ink.Muted,
                    fontSize = 12.5.sp,
                    fontFamily = SoraFamily,
                )
            }
        }

        item { SearchPill(onClick = onOpenSearch, modifier = Modifier.padding(horizontal = 20.dp)) }

        item { Spacer(Modifier.height(18.dp)) }

        item {
            HeroCard(
                track = state.track ?: Catalog.hero,
                playing = state.isPlaying,
                onPlay = { onOpenPlayer() },
            )
        }

        if (moods.isNotEmpty()) {
            item { SectionHeader("Moods", "Explore", onOpenExplore) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(moods) { m ->
                        Chip(m.title, leading = G.Sparkle) { onOpenMood(m) }
                    }
                }
            }
        }

        if (recents.isNotEmpty()) {
            item { SectionHeader("Recently played") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(recents) { t ->
                        RecentTile(t, downloaded = downloadedIds.contains(t.id)) {
                            onPlay(recents, recents.indexOf(t))
                        }
                    }
                }
            }
        }

        item { SectionHeader("Made for you") }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(Catalog.collections) { c -> SquareCard(c, onClick = { onOpenCollection(c) }) }
            }
        }

        item { SectionHeader("Offline, always") }
        item {
            Column(Modifier.padding(horizontal = 8.dp)) {
                Catalog.tracks.take(3).forEachIndexed { i, t ->
                    TrackRow(
                        track = t,
                        playing = state.isPlaying,
                        isCurrent = state.track?.id == t.id,
                        downloaded = true,
                        onPlay = { onPlay(Catalog.tracks, i) },
                        onMore = { onMore(t) },
                    )
                }
            }
        }

        if (loading) {
            item {
                Column(Modifier.padding(top = 20.dp)) {
                    repeat(3) { ShimmerRow() }
                }
            }
        } else if (failed) {
            item {
                Panel(Modifier.padding(20.dp).fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            "YouTube Music unreachable",
                            color = Ink.Text, fontSize = 14.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Check your connection — the offline tracks above still play.",
                            color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily,
                        )
                        Spacer(Modifier.height(12.dp))
                        GhostBtn("Try again", G.Refresh) { attempt += 1 }
                    }
                }
            }
        }

        shelves.forEach { shelf ->
            item { SectionHeader(shelf.title) }
            if (shelf.tracks.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 8.dp)) {
                        shelf.tracks.take(5).forEachIndexed { i, t ->
                            TrackRow(
                                track = t,
                                playing = state.isPlaying,
                                isCurrent = state.track?.id == t.id,
                                downloaded = downloadedIds.contains(t.id),
                                onPlay = { onPlay(shelf.tracks, i) },
                                onMore = { onMore(t) },
                            )
                        }
                    }
                }
            } else {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(shelf.items) { c ->
                            if (c.round) CircleCard(c, onClick = { onOpenCollection(c) })
                            else SquareCard(c, onClick = { onOpenCollection(c) })
                        }
                    }
                }
            }
        }
    }
}

private fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        h < 5 -> "Late night, again"
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        h < 22 -> "Good evening"
        else -> "Wind down"
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.Card)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(G.Search, Ink.Faint, 17.dp)
        Spacer(Modifier.width(10.dp))
        Text("Songs, artists, albums…", color = Ink.Faint, fontSize = 13.sp, fontFamily = SoraFamily)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(Ink.Lime.copy(alpha = 0.14f))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text("YT", color = Ink.Lime, fontSize = 9.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HeroCard(track: Track, playing: Boolean, onPlay: () -> Unit) {
    val pulse by animateFloatAsState(if (playing) 1f else 0.92f, label = "pulse")
    val light = Ink.isLight
    val heroColors = if (light) listOf(Ink.Text, Ink.Text) else listOf(Color(0xFFC6BEF2), Color(0xFF8F86D6), Color(0xFF6D63B8))
    val heroInk = if (light) Ink.OnAccent else Color(0xFF15112A)
    val heroSub = if (light) Ink.OnAccent.copy(alpha = 0.72f) else Color(0xFF2E2650)
    val btnBg = if (light) Color.White else Color(0xFF15112A)
    val btnInk = if (light) Ink.Text else Ink.Lime
    Box(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(heroColors)
            )
            .clickable { onPlay() }
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(Ink.Void.copy(alpha = 0.18f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        if (playing) "PLAYING NOW" else "PICK UP WHERE YOU LEFT",
                        color = heroInk,
                        fontSize = 8.5.sp,
                        fontFamily = SoraFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        color = heroInk,
                        fontSize = 24.sp,
                        fontFamily = SerifFamily,
                        lineHeight = 26.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        track.artist + " · " + track.album,
                        color = heroSub,
                        fontSize = 11.5.sp,
                        fontFamily = SoraFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(btnBg)
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(if (playing) G.Pause else G.Play, btnInk, 13.dp)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (playing) "Pause" else "Play",
                            color = if (light) Ink.Text else Color(0xFFF4F1E9),
                            fontSize = 11.5.sp,
                            fontFamily = SoraFamily,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Box(Modifier.size(104.dp * pulse)) {
                    ArtImage(track.artUrl, track.artAsset, Modifier.fillMaxSize(), CircleShape)
                }
            }
        }
    }
}

@Composable
private fun RecentTile(track: Track, downloaded: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(96.dp).clickable { onClick() }) {
        Box {
            ArtImage(
                url = track.artUrl,
                asset = track.artAsset,
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(18.dp),
            )
            if (downloaded) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Ink.Mint),
                    contentAlignment = Alignment.Center,
                ) { Glyph(G.Check, Ink.Void, 10.dp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            track.title,
            color = Ink.Text,
            fontSize = 12.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            track.artist,
            color = Ink.Faint,
            fontSize = 10.5.sp,
            fontFamily = SoraFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
