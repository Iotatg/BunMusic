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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.bunmusic.core.LocalBunWindow
import app.bunmusic.core.SerifFamily
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Discovery
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import app.bunmusic.player.PlayerState

/**
 * "Made for you" — the screen that answers "what should I play?" without asking.
 *
 * Six artist mixes, a taste card, the time capsule and the two shelves that come
 * out of likes and follows. Everything is computed on the device from the play
 * log; nothing here is fetched, so it opens instantly and works on a plane.
 */
@Composable
fun DiscoveryScreen(
    state: PlayerState,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onMore: (Track) -> Unit,
    onToast: (String) -> Unit,
) {
    val key = state.track?.id
    val history = remember(key) { Store.history() }
    val liked = remember(key) { Store.likedTracks() }
    val all = remember(key) { Store.allTracks() }
    val follows = remember(key) { Store.follows() }

    val taste = remember(history, liked) { Discovery.taste(history, liked) }
    val mixes = remember(history, liked, all) { Discovery.mixes(history, liked, all) }
    val because = remember(liked, all, history) { Discovery.becauseYouLiked(liked, all, history) }
    val capsule = remember(history) { Discovery.timeCapsule(history) }
    val fresh = remember(liked, all, history) { Discovery.freshFinds(liked, all, history) }
    val blend = remember(liked, all, follows) { Discovery.blend(liked, all, follows) }
    val weekArtist = remember(history) { Discovery.topArtistThisWeek(history) }
    val gutter = LocalBunWindow.current.gutter

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            BackBar("Made for you", onBack)
        }

        // ---- taste card
        item {
            Box(
                Modifier
                    .padding(horizontal = gutter, vertical = 6.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Ink.Lavender.copy(alpha = 0.30f), Ink.Card)
                        )
                    )
                    .padding(16.dp),
            ) {
                Column {
                    Text(
                        "YOUR TASTE",
                        color = Ink.Lavender,
                        fontSize = 9.5.sp,
                        fontFamily = SoraFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        taste.topArtist ?: "Not enough plays yet",
                        color = Ink.Text,
                        fontSize = 24.sp,
                        fontFamily = SerifFamily,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (taste.plays == 0) "Play a few songs and this page fills itself in."
                        else "${taste.plays} plays · ${taste.depth} artists · ${(taste.newShare * 100).toInt()}% outside your top five this month",
                        color = Ink.Muted,
                        fontSize = 11.5.sp,
                        fontFamily = SoraFamily,
                    )
                    if (taste.artists.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            taste.artists.take(3).forEachIndexed { i, (artist, _) ->
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Ink.Field)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        "${i + 1}. $artist",
                                        color = Ink.Text,
                                        fontSize = 10.sp,
                                        fontFamily = SoraFamily,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    if (taste.plays > 0) {
                        Spacer(Modifier.height(12.dp))
                        StatRow(
                            listOf(
                                "${taste.plays}" to "plays",
                                "${taste.depth}" to "artists",
                                "${(taste.newShare * 100).toInt()}%" to "new this month",
                            )
                        )
                    }
                    if (weekArtist != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "This week: ${weekArtist.first} · ${weekArtist.second} plays",
                            color = Ink.Lime,
                            fontSize = 10.5.sp,
                            fontFamily = SoraFamily,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        if (mixes.isNotEmpty()) {
            item { SectionTitle("Daily mixes", mixes.size, "Shuffle all") { onPlay(mixes.flatMap { it.tracks }.distinctBy { t -> t.id }, 0) } }
            items(mixes) { mix ->
                MixCard(mix) { onPlay(mix.tracks, 0) }
            }
        }

        if (because != null) {
            item { SectionTitle("Because you liked ${because.first}", because.second.size) }
            items(because.second.take(5)) { t ->
                TrackRow(
                    track = t,
                    playing = state.isPlaying,
                    isCurrent = state.track?.id == t.id,
                    onPlay = { onPlay(because.second, because.second.indexOf(t)) },
                    onMore = { onMore(t) },
                )
            }
        }

        if (capsule.isNotEmpty()) {
            item {
                SectionTitle("On this day", capsule.size)
            }
            item {
                Text(
                    "Songs you were playing on this date in earlier years.",
                    color = Ink.Faint,
                    fontSize = 11.sp,
                    fontFamily = SoraFamily,
                    modifier = Modifier.padding(horizontal = gutter, vertical = 2.dp),
                )
            }
            items(capsule.take(6)) { t ->
                TrackRow(
                    track = t,
                    playing = state.isPlaying,
                    isCurrent = state.track?.id == t.id,
                    onPlay = { onPlay(capsule, capsule.indexOf(t)) },
                    onMore = { onMore(t) },
                )
            }
        }

        if (fresh.isNotEmpty()) {
            item { SectionTitle("Fresh finds", fresh.size) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = gutter),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(fresh.take(12)) { t ->
                        FreshCard(t) { onPlay(fresh, fresh.indexOf(t)) }
                    }
                }
            }
        }

        if (blend.isNotEmpty()) {
            item { SectionTitle("Blend", blend.size, "Play") { onPlay(blend, 0) } }
            item {
                Text(
                    "Two of your likes, one from an artist you follow.",
                    color = Ink.Faint,
                    fontSize = 11.sp,
                    fontFamily = SoraFamily,
                    modifier = Modifier.padding(horizontal = gutter, vertical = 2.dp),
                )
            }
            items(blend.take(6)) { t ->
                TrackRow(
                    track = t,
                    playing = state.isPlaying,
                    isCurrent = state.track?.id == t.id,
                    onPlay = { onPlay(blend, blend.indexOf(t)) },
                    onMore = { onMore(t) },
                )
            }
        }

        if (mixes.isEmpty() && because == null && capsule.isEmpty()) {
            item {
                EmptyState(
                    G.Sparkle,
                    "Nothing to mix yet",
                    "Mixes are built from what you actually play. A few songs is enough.",
                    action = "Find something",
                    onAction = { onToast("Open Search from the tab bar") },
                )
            }
        }
    }
}

private val mixTints = listOf(
    Color(0xFFC6BEF2),
    Color(0xFFD8F65C),
    Color(0xFFFF8CA4),
    Color(0xFF7FE3C0),
    Color(0xFFFFC46B),
    Color(0xFF8FB8FF),
)

@Composable
private fun MixCard(mix: Discovery.Mix, onPlay: () -> Unit) {
    val tint = mixTints[mix.accent % mixTints.size]
    val gutter = LocalBunWindow.current.gutter
    Row(
        Modifier
            .padding(horizontal = gutter, vertical = 5.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = 0.22f), Ink.Card)
                )
            )
            .clickable { onPlay() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A 2x2 collage built from the first four covers: cheaper than one big
        // image and it reads as "several songs", which is what a mix is.
        Box(Modifier.size(66.dp).clip(RoundedCornerShape(12.dp)).background(Ink.Field)) {
            val arts = mix.tracks.take(4)
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    arts.getOrNull(0)?.let { ArtImage(it.artUrl, it.artAsset, Modifier.weight(1f).fillMaxSize()) }
                    arts.getOrNull(1)?.let { ArtImage(it.artUrl, it.artAsset, Modifier.weight(1f).fillMaxSize()) }
                }
                Row(Modifier.weight(1f)) {
                    arts.getOrNull(2)?.let { ArtImage(it.artUrl, it.artAsset, Modifier.weight(1f).fillMaxSize()) }
                    arts.getOrNull(3)?.let { ArtImage(it.artUrl, it.artAsset, Modifier.weight(1f).fillMaxSize()) }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                mix.title,
                color = Ink.Text,
                fontSize = 14.sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                mix.subtitle,
                color = Ink.Muted,
                fontSize = 10.5.sp,
                fontFamily = SoraFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                mix.tracks.take(3).joinToString(" · ") { it.title },
                color = Ink.Faint,
                fontSize = 9.5.sp,
                fontFamily = SoraFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(tint),
            contentAlignment = Alignment.Center,
        ) { Glyph(G.Play, Ink.OnAccent, 14.dp) }
    }
}

@Composable
private fun FreshCard(track: Track, onPlay: () -> Unit) {
    Column(
        Modifier.width(118.dp).clickable { onPlay() },
    ) {
        ArtImage(
            url = track.artUrl,
            asset = track.artAsset,
            modifier = Modifier.size(118.dp),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            track.title,
            color = Ink.Text,
            fontSize = 11.5.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            track.artist,
            color = Ink.Faint,
            fontSize = 10.sp,
            fontFamily = SoraFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
