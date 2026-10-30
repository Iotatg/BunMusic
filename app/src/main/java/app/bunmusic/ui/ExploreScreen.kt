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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import app.bunmusic.core.Ambient
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Collection
import app.bunmusic.data.ExplorePage
import app.bunmusic.data.Mood
import app.bunmusic.data.Shelf
import app.bunmusic.data.Track
import app.bunmusic.data.YtMusic

@Composable
fun ExploreScreen(
    onOpenCollection: (Collection) -> Unit,
    onOpenMood: (Mood) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onMore: (Track) -> Unit,
    onOpenCharts: () -> Unit,
) {
    var page by remember { mutableStateOf(ExplorePage()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatching { YtMusic.explore() }.onSuccess { page = it }
        loading = false
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { ScreenTitle("Explore", "Moods, new releases and what's hot") }

        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                ChartsCard(onClick = onOpenCharts, modifier = Modifier.weight(1f))
            }
        }

        if (loading) {
            item {
                Column(Modifier.padding(top = 8.dp)) { repeat(4) { ShimmerRow() } }
            }
        }

        if (page.moods.isNotEmpty()) {
            item { SectionHeader("Moods & genres") }
            items(page.moods.chunked(2)) { pair ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pair.forEach { m ->
                        MoodTile(m, Modifier.weight(1f)) { onOpenMood(m) }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (page.newReleases.isNotEmpty()) {
            item { SectionHeader("New albums & singles") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(page.newReleases) { c -> SquareCard(c, onClick = { onOpenCollection(c) }) }
                }
            }
        }

        if (page.trending.isNotEmpty()) {
            item { SectionHeader("Trending now") }
            item {
                Column(Modifier.padding(horizontal = 8.dp)) {
                    page.trending.take(10).forEachIndexed { i, t ->
                        TrackRow(
                            track = t,
                            index = i + 1,
                            onPlay = { onPlay(page.trending, i) },
                            onMore = { onMore(t) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodTile(mood: Mood, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val tint = if (mood.accent != 0L) Color(mood.accent.toInt()) else Ambient.colorFor(mood.title)
    Box(
        modifier
            .height(74.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.30f), tint.copy(alpha = 0.08f))))
            .clickable { onClick() }
            .padding(14.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(3.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tint.copy(alpha = 0.9f))
        )
        Text(
            mood.title,
            color = Ink.Text,
            fontSize = 14.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp),
        )
        Box(
            Modifier.align(Alignment.TopEnd).size(22.dp),
            contentAlignment = Alignment.Center,
        ) { Glyph(G.Sparkle, Ink.Text.copy(alpha = 0.7f), 14.dp) }
    }
}

@Composable
private fun ChartsCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (Ink.isLight) Brush.linearGradient(listOf(Ink.Field, Ink.Card)) else Brush.linearGradient(listOf(Color(0xFF2A2135), Color(0xFF1A1524))))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Ink.Rose.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Glyph(G.Chart, Ink.Rose, 19.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Charts", color = Ink.Text, fontSize = 14.sp, fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold)
            Text("Top artists and videos near you", color = Ink.Muted, fontSize = 11.5.sp, fontFamily = SoraFamily)
        }
        Glyph(G.Forward, Ink.Faint, 16.dp)
    }
}

@Composable
fun MoodScreen(
    title: String,
    browseId: String,
    params: String,
    onBack: () -> Unit,
    onOpenCollection: (Collection) -> Unit,
) {
    var shelves by remember { mutableStateOf<List<Shelf>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(browseId, params) {
        loading = true
        runCatching { YtMusic.browseShelves(browseId, params) }
            .onSuccess { shelves = it }
            .onFailure { failed = true }
        loading = false
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { BackBar(title, onBack) }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Display(title, 30)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${shelves.sumOf { it.items.size }} collections",
                    color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily,
                )
            }
        }

        if (loading) {
            items(4) { ShimmerRow() }
        } else if (failed) {
            item { EmptyState(G.Offline, "Couldn't load", "This mood didn't answer. Try another one.") }
        }

        shelves.forEach { shelf ->
            item { SectionHeader(shelf.title) }
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
