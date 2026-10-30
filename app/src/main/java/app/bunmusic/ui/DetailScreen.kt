package app.bunmusic.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.Ambient
import app.bunmusic.core.G
import app.bunmusic.core.Ink
import app.bunmusic.core.SerifFamily
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Catalog
import app.bunmusic.data.Collection
import app.bunmusic.data.CollectionKind
import app.bunmusic.data.Shelf
import app.bunmusic.data.Track
import app.bunmusic.data.YtMusic
import app.bunmusic.player.Playback
import app.bunmusic.player.PlayerState

@Composable
fun DetailScreen(
    title: String,
    artUrl: String?,
    artAsset: String?,
    browseId: String?,
    kind: CollectionKind,
    state: PlayerState,
    downloadedIds: Set<String>,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onBack: () -> Unit,
    onMore: (Track) -> Unit,
) {
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var shelves by remember { mutableStateOf<List<Shelf>>(emptyList()) }
    var description by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var headerArt by remember { mutableStateOf(artUrl) }

    LaunchedEffect(browseId, title) {
        loading = true
        failed = false
        if (browseId == null) {
            val local = Catalog.collectionById(title)
            tracks = Catalog.tracksFor(local?.id ?: "")
            loading = false
            return@LaunchedEffect
        }
        if (browseId.startsWith("FEmusic_")) {
            runCatching { YtMusic.browseShelves(browseId) }
                .onSuccess { shelves = it }
                .onFailure { failed = true }
            loading = false
            return@LaunchedEffect
        }
        runCatching { YtMusic.collection(browseId) }
            .onSuccess { detail ->
                tracks = detail.tracks
                shelves = detail.shelves
                description = detail.description
                if (headerArt == null) headerArt = detail.collection.artUrl
            }
            .onFailure { failed = true }
        loading = false
    }

    val tint = Ambient.colorFor(browseId ?: title)

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(tint.copy(alpha = 0.42f), Ink.Void.copy(alpha = 0f))
                            )
                        )
                )
                Column {
                    BackBar("", onBack)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        ArtImage(
                            url = headerArt,
                            asset = artAsset,
                            modifier = Modifier.size(148.dp),
                            shape = if (kind == CollectionKind.ARTIST) CircleShape else RoundedCornerShape(22.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (kind) {
                                    CollectionKind.ALBUM -> "ALBUM"
                                    CollectionKind.ARTIST -> "ARTIST"
                                    else -> "PLAYLIST"
                                },
                                color = Ink.Lime,
                                fontSize = 9.sp,
                                fontFamily = SoraFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                title,
                                color = Ink.Text,
                                fontSize = 24.sp,
                                fontFamily = SerifFamily,
                                lineHeight = 27.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                when {
                                    loading -> "Loading…"
                                    tracks.isNotEmpty() -> "${tracks.size} tracks · ${fmtDuration(tracks.sumOf { it.durationMs })}"
                                    shelves.isNotEmpty() -> "${shelves.size} collections"
                                    else -> ""
                                },
                                color = Ink.Muted,
                                fontSize = 11.5.sp,
                                fontFamily = SoraFamily,
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    if (tracks.isNotEmpty()) {
                        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilledBtn("Play all", G.Play) { onPlay(tracks, 0) }
                            Spacer(Modifier.width(10.dp))
                            GhostBtn("Shuffle", G.Shuffle) { Playback.shufflePlay(tracks) }
                        }
                    }
                }
            }
        }

        if (description.isNotBlank()) {
            item {
                Text(
                    description,
                    color = Ink.Muted,
                    fontSize = 12.sp,
                    fontFamily = SoraFamily,
                    lineHeight = 18.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }

        if (loading) {
            items(6) { ShimmerRow() }
        } else if (failed) {
            item { EmptyState(G.Offline, "Couldn't load", "This page didn't answer. Check your connection.") }
        }

        items(tracks.size) { i ->
            val t = tracks[i]
            Box(Modifier.padding(horizontal = 8.dp)) {
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
