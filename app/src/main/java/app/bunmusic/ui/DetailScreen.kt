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
import androidx.compose.foundation.shape.CircleShape
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
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Catalog
import app.bunmusic.data.Collection
import app.bunmusic.data.CollectionKind
import app.bunmusic.data.Track
import app.bunmusic.data.YtMusic
import app.bunmusic.player.PlayerState

@Composable
fun DetailScreen(
    title: String,
    artUrl: String?,
    artAsset: String?,
    browseId: String?,
    kind: CollectionKind,
    state: PlayerState,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onBack: () -> Unit,
) {
    var loading by remember { mutableStateOf(browseId != null) }
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var shelves by remember { mutableStateOf(emptyList<app.bunmusic.data.Shelf>()) }
    var headerArt by remember { mutableStateOf(artUrl) }
    var headerTitle by remember { mutableStateOf(title) }
    var subtitle by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(browseId) {
        if (browseId == null) {
            val local = Catalog.collections.firstOrNull { it.title == title }
            tracks = Catalog.tracksFor(local?.id ?: "")
            subtitle = local?.subtitle ?: ""
            loading = false
            return@LaunchedEffect
        }
        runCatching { YtMusic.collection(browseId) }
            .onSuccess { d ->
                tracks = d.tracks
                shelves = d.shelves
                headerArt = d.collection.artUrl ?: headerArt
                headerTitle = d.collection.title
                subtitle = d.collection.subtitle
                description = d.description
            }
            .onFailure { failed = true }
        loading = false
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 30.dp),
    ) {
        item { BackBar(kind.name.lowercase().replaceFirstChar { it.uppercase() }, onBack) }

        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(6.dp))
                ArtImage(
                    url = headerArt,
                    asset = artAsset,
                    modifier = Modifier.size(196.dp),
                    shape = if (kind == CollectionKind.ARTIST) CircleShape else RoundedCornerShape(26.dp),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    headerTitle,
                    color = Ink.Text,
                    fontSize = 26.sp,
                    fontFamily = app.bunmusic.core.SerifFamily,
                    lineHeight = 30.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        subtitle,
                        color = Ink.Muted,
                        fontSize = 12.sp,
                        fontFamily = SoraFamily,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(Ink.Lime)
                            .clickable { if (tracks.isNotEmpty()) onPlay(tracks, 0) }
                            .padding(horizontal = 20.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(G.Play, Ink.OnAccent, 13.dp)
                        Spacer(Modifier.width(7.dp))
                        Text("Play", color = Ink.OnAccent, fontSize = 12.5.sp, fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(Ink.Card)
                            .clickable {
                                if (tracks.isNotEmpty()) onPlay(tracks.shuffled(), 0)
                            }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(G.Shuffle, Ink.Text, 14.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle", color = Ink.Text, fontSize = 12.5.sp, fontFamily = SoraFamily)
                    }
                }
                if (description.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        description.take(220),
                        color = Ink.Faint,
                        fontSize = 11.5.sp,
                        fontFamily = SoraFamily,
                        lineHeight = 17.sp,
                    )
                }
            }
        }

        if (loading) {
            item {
                Text("Loading…", color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily, modifier = Modifier.padding(20.dp))
            }
        }
        if (failed) {
            item {
                Text(
                    "Couldn't load this one. Check your connection.",
                    color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }

        if (tracks.isNotEmpty()) {
            item { SectionHeader("Tracks") }
            itemsIndexed(tracks) { i, t ->
                Box(Modifier.padding(horizontal = 8.dp)) {
                    TrackRow(
                        track = t,
                        playing = state.isPlaying,
                        isCurrent = state.track?.id == t.id,
                        onPlay = { onPlay(tracks, i) },
                    )
                }
            }
        }

        shelves.forEach { shelf ->
            item { SectionHeader(shelf.title) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(shelf.items) { c ->
                        if (c.round) CircleCard(c, onClick = { onOpenCollection(c) })
                        else SquareCard(c, onClick = { onOpenCollection(c) })
                    }
                }
            }
        }
    }
}
