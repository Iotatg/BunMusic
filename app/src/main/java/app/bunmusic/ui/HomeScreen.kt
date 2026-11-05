package app.bunmusic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.BunSpring
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.LocalBunWindow
import app.bunmusic.core.LocalReducedMotion
import app.bunmusic.core.SerifFamily
import app.bunmusic.core.Skin
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Collection
import app.bunmusic.data.Discovery
import app.bunmusic.data.Mood
import app.bunmusic.data.Prefs
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
    onTogglePlay: () -> Unit,
    onMore: (Track) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiscovery: () -> Unit = {},
) {
    var shelves by remember { mutableStateOf<List<Shelf>>(emptyList()) }
    var moods by remember { mutableStateOf<List<Mood>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val recents = remember(state.track?.id) { Store.recent(6) }
    val window = LocalBunWindow.current
    val gutter = window.gutter

    LaunchedEffect(attempt) {
        loading = true
        failed = false
        runCatching { YtMusic.home() }
            .onSuccess { shelves = it }
            .onFailure { failed = true }
        runCatching { YtMusic.explore() }.onSuccess { moods = it.moods.take(8) }
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
    // The page has its own weather. Nebula gets a sky — a violet well, two slow
    // clouds and sixty stars whose positions come from the playing track's id, so
    // a song always looks the same. The other buns keep the aurora, which is the
    // same idea in three hues and no stars.
    val seed = state.track?.id ?: recents.firstOrNull()?.id
    if (Skin.isNebula) {
        NebulaBackdrop(seed = seed, modifier = Modifier.fillMaxSize())
    } else {
        AuroraBackdrop(
            seed = seed,
            modifier = Modifier.fillMaxSize(),
            intensity = if (Ink.isLight) 0.75f else 0.9f,
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .reveal(0)
                    .padding(start = gutter, end = gutter - 6.dp, top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (Skin.isNebula) {
                    // The mascot, at 34 dp, as the app's face. The drawing is the
                    // same one the player and the empty state use, so the character
                    // is recognisable before the user has read a word.
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Ink.Lime.copy(alpha = 0.34f), Color.Transparent)
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        BunMascot(Modifier.fillMaxSize(), playing = state.isPlaying)
                    }
                    Spacer(Modifier.width(9.dp))
                }
                Text(
                    "BUNMUSIC",
                    color = Ink.Text,
                    fontSize = 13.sp,
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp,
                )
                Spacer(Modifier.weight(1f))
                IconBtn(G.Gear, Ink.Muted, 38.dp, 18.dp, onClick = onOpenSettings)
            }
        }

        item {
            Column(Modifier.reveal(1).padding(horizontal = gutter, vertical = 10.dp)) {
                Display(greeting(), 33)
                Spacer(Modifier.height(4.dp))
                Text(
                    // The line under the greeting changes with the hour. It is a
                    // small thing, but a home screen that says "good evening" and
                    // then "start your morning" is two clocks disagreeing.
                    Discovery.hourMood(
                        java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    ).line,
                    color = Ink.Muted,
                    fontSize = 12.5.sp,
                    fontFamily = SoraFamily,
                )
            }
        }

        item {
            SearchPill(
                onClick = onOpenSearch,
                modifier = Modifier.reveal(2).padding(horizontal = gutter),
            )
        }

        // ---- made for you: computed on the device, so it is here before the
        // network shelves have answered
        if (Prefs.settings.value.discoveryHome) {
            item {
                DiscoveryRow(
                    onPlay = onPlay,
                    onSeeAll = onOpenDiscovery,
                    modifier = Modifier.reveal(3).padding(top = 6.dp),
                )
            }
        }

        item { Spacer(Modifier.height(18.dp)) }

        item {
            val hero = state.track ?: recents.firstOrNull()
            Box(Modifier.reveal(4)) {
                if (hero != null) {
                    HeroCard(
                        track = hero,
                        playing = state.isPlaying,
                        onOpen = { onOpenPlayer() },
                        onToggle = onTogglePlay,
                    )
                } else if (Skin.isNebula) {
                    NebulaStartCard(onOpenSearch = onOpenSearch)
                } else {
                    StartCard(onOpenSearch = onOpenSearch)
                }
            }
        }

        if (moods.isNotEmpty()) {
            item { SectionHeader("Moods", "Explore", onOpenExplore) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = gutter),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Indexed: two moods can share a browse id and a title (the API
                    // repeats "Chill" across regions), and a duplicated LazyList key
                    // is an IllegalArgumentException, not a cosmetic glitch.
                    itemsIndexed(moods, key = { i, m -> "${m.browseId}#$i" }) { _, m ->
                        Chip(m.title, leading = G.Sparkle) { onOpenMood(m) }
                    }
                }
            }
        }

        if (recents.isNotEmpty()) {
            item { SectionHeader("Recently played") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = gutter),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Index comes from the list, not from `indexOf` — that was an
                    // O(n²) scan per row and it picked the wrong copy of a repeat.
                    itemsIndexed(recents, key = { i, t -> t.id + "#" + i }) { i, t ->
                        RecentTile(
                            track = t,
                            downloaded = downloadedIds.contains(t.id),
                            size = window.tile(96.dp),
                        ) { onPlay(recents, i) }
                    }
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
                Panel(Modifier.padding(horizontal = gutter, vertical = 20.dp).fillMaxWidth()) {
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
                    // Rows inset by (gutter - their own 10 dp) so the titles line up
                    // with the section heading above them instead of drifting 2 dp.
                    Column(Modifier.padding(horizontal = (gutter - 10.dp).coerceAtLeast(0.dp))) {
                        shelf.tracks.take(5).forEachIndexed { i, t ->
                            TrackRow(
                                track = t,
                                // Numbered: the row used to print a lone "•" in the
                                // gutter, which read as a stray character rather than
                                // as a list position.
                                index = i + 1,
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
                        contentPadding = PaddingValues(horizontal = gutter),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(shelf.items, key = { i, c -> c.id + "#" + i }) { _, c ->
                            if (c.round) CircleCard(c, onClick = { onOpenCollection(c) })
                            else SquareCard(c, onClick = { onOpenCollection(c) })
                        }
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
            .then(
                if (Skin.isNebula) {
                    // Glass rather than a solid card: on a star field a flat
                    // rectangle reads as a hole punched in the sky.
                    Modifier.glass(radius = 16.dp)
                } else {
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Ink.Card)
                        // On a chalk page a white pill needs an edge, or it dissolves.
                        .then(if (Ink.isLight) Modifier.border(1.dp, Ink.Hairline, RoundedCornerShape(16.dp)) else Modifier)
                }
            )
            .tapable(scale = 0.985f) { onClick() }
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
            Text("YT", color = Ink.Lime, fontSize = 10.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * What the home screen shows before anything has been played: no fake shelf, no
 * bundled demo track — just the way in.
 */
@Composable
private fun StartCard(onOpenSearch: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = LocalBunWindow.current.gutter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Ink.Card)
            .tapable(scale = 0.985f) { onOpenSearch() }
            .padding(22.dp),
    ) {
        Text(
            "Nothing playing yet",
            color = Ink.Text,
            fontSize = 22.sp,
            fontFamily = SerifFamily,
            lineHeight = 26.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Search for a song, an artist or a mood. Anything you play is kept here, and " +
                "what you play twice is already cached.",
            color = Ink.Muted,
            fontSize = 12.sp,
            fontFamily = SoraFamily,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(16.dp))
        FilledBtn("Find something", G.Search, onClick = onOpenSearch)
    }
}

/**
 * The empty home, Nebula edition.
 *
 * The reference does the one thing most music apps are afraid of: with nothing
 * playing it shows a character instead of a grey box. So this card is the bun,
 * asleep on its cloud, a lit orb behind it, and one sentence that says what to
 * do. No demo track, no fake shelf — the illustration carries the page.
 */
@Composable
private fun NebulaStartCard(onOpenSearch: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = LocalBunWindow.current.gutter)
            .fillMaxWidth()
            .neon(Ink.Lime, radius = 30.dp, elevation = 26.dp)
            .glass(radius = 30.dp),
    ) {
        AuroraOrb(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .size(196.dp),
            alpha = 0.55f,
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BunMascot(Modifier.size(168.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                "Feel the silence",
                color = Ink.Text,
                fontSize = 27.sp,
                fontFamily = SerifFamily,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Hear the infinity. Search for a song, an artist or a mood — " +
                    "anything you play is kept here, and what you play twice is already cached.",
                color = Ink.Muted,
                fontSize = 12.sp,
                fontFamily = SoraFamily,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            GlowPill("Find something", G.Search, onClick = onOpenSearch)
        }
    }
}

@Composable
private fun HeroCard(track: Track, playing: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    val reduced = LocalReducedMotion.current
    // The disc holds its angle when paused. Resetting to zero — which is what
    // `if (playing) rotation else 0f` does — reads as the record being yanked off
    // the platter and put back a second later.
    val spin = remember { Animatable(0f) }
    LaunchedEffect(playing, track.id) {
        if (playing && !reduced) {
            while (true) spin.animateTo(spin.value + 360f, tween(38_000, easing = LinearEasing))
        }
    }
    val settle by animateFloatAsState(
        targetValue = if (playing) 1f else 0.94f,
        animationSpec = BunSpring.smooth(),
        label = "settle",
    )
    Box(
        Modifier
            .padding(horizontal = LocalBunWindow.current.gutter)
            .fillMaxWidth()
            .then(if (Skin.isNebula) Modifier.neon(Ink.Lime, radius = 28.dp, elevation = 26.dp) else Modifier)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(Ink.Hero))
            .tapable(scale = 0.985f) { onOpen() }
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(Ink.HeroInk.copy(alpha = 0.16f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        if (playing) "PLAYING NOW" else "PICK UP WHERE YOU LEFT",
                        color = Ink.HeroInk,
                        fontSize = 10.sp,
                        fontFamily = SoraFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.9.sp,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        color = Ink.HeroInk,
                        fontSize = 24.sp,
                        fontFamily = SerifFamily,
                        lineHeight = 26.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        track.creditLine(),
                        color = Ink.HeroSub,
                        fontSize = 11.5.sp,
                        fontFamily = SoraFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                    // The pill says "Pause", so it pauses. It used to be pure
                    // decoration inside a card that opened the player: the most
                    // obvious button on the home screen did the wrong thing.
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(Ink.HeroBtn)
                            .tapable(scale = 0.94f) { onToggle() }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnimatedContent(
                            targetState = playing,
                            transitionSpec = {
                                (
                                    fadeIn(tween(if (reduced) 0 else 130)) +
                                        scaleIn(initialScale = 0.5f, animationSpec = tween(if (reduced) 0 else 180))
                                    ) togetherWith (
                                    fadeOut(tween(if (reduced) 0 else 90)) +
                                        scaleOut(targetScale = 0.5f, animationSpec = tween(if (reduced) 0 else 90))
                                    )
                            },
                            label = "heroGlyph",
                        ) { on ->
                            Glyph(if (on) G.Pause else G.Play, Ink.HeroBtnInk, 13.dp)
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (playing) "Pause" else "Play",
                            color = Ink.HeroBtnInk,
                            fontSize = 11.5.sp,
                            fontFamily = SoraFamily,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Box(
                    Modifier
                        .size(104.dp)
                        .graphicsLayer {
                            scaleX = settle
                            scaleY = settle
                        },
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = spin.value }
                    ) {
                        ArtImage(track.artUrl, track.artAsset, Modifier.fillMaxSize(), CircleShape)
                    }
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Ink.HeroInk.copy(alpha = 0.18f))
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .border(1.dp, Ink.HeroInk.copy(alpha = 0.16f), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentTile(track: Track, downloaded: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(Modifier.width(size).tapable { onClick() }) {
        Box {
            ArtImage(
                url = track.artUrl,
                asset = track.artAsset,
                modifier = Modifier.size(size),
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
            fontSize = 11.sp,
            fontFamily = SoraFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The first row on Home that does not need a network.
 *
 * Six artist mixes, built from the play log while the YouTube shelves are still
 * loading. If the connection is dead this row is still there, which is the whole
 * point of computing it on the device.
 */
@Composable
private fun DiscoveryRow(
    onPlay: (List<Track>, Int) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val history = remember { Store.history() }
    val liked = remember { Store.likedTracks() }
    val all = remember { Store.allTracks() }
    val mixes = remember(history, liked, all) {
        Discovery.mixes(history, liked, all, limit = 5, size = 20)
    }
    if (mixes.isEmpty()) return

    Column(modifier) {
        SectionTitle("Made for you", mixes.size, "See all", onSeeAll)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(mixes, key = { it.id }) { mix ->
                MiniMixCard(mix) { onPlay(mix.tracks, 0) }
            }
        }
    }
}

@Composable
private fun MiniMixCard(mix: Discovery.Mix, onPlay: () -> Unit) {
    val tint = when (mix.accent % 4) {
        0 -> Ink.Lavender
        1 -> Ink.Lime
        2 -> Ink.Rose
        else -> Ink.Mint
    }
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.34f), Ink.Card)))
            .tapable(scale = 0.96f) { onPlay() }
            .padding(12.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(tint),
            contentAlignment = Alignment.Center,
        ) { Glyph(G.Play, Ink.OnAccent, 12.dp) }
        Spacer(Modifier.height(12.dp))
        Text(
            mix.title,
            color = Ink.Text,
            fontSize = 13.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "${mix.tracks.size} tracks",
            color = Ink.Muted,
            fontSize = 10.sp,
            fontFamily = SoraFamily,
        )
    }
}
