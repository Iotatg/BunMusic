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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Collection
import app.bunmusic.data.SearchResults
import app.bunmusic.data.Track
import app.bunmusic.data.YtMusic
import app.bunmusic.player.PlayerState
import kotlinx.coroutines.delay

private val hints = listOf("lofi beats", "arijit singh", "the weeknd", "jazz café", "karan aujla", "ambient focus")

@Composable
fun SearchScreen(
    state: PlayerState,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenCollection: (Collection) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = null
            error = null
            return@LaunchedEffect
        }
        delay(430)
        busy = true
        error = null
        runCatching { YtMusic.search(query) }
            .onSuccess { results = it }
            .onFailure { error = it.message ?: "Search failed" }
        busy = false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Display("Search", 30, Ink.Text)
        }
        Spacer(Modifier.height(14.dp))

        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Ink.Card)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(G.Search, Ink.Lime, 17.dp)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = Ink.Text, fontSize = 14.sp, fontFamily = SoraFamily),
                cursorBrush = SolidColor(Ink.Lime),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text("Search YouTube Music", color = Ink.Faint, fontSize = 14.sp, fontFamily = SoraFamily)
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).clickable { query = "" },
                    contentAlignment = Alignment.Center,
                ) { Glyph(G.Close, Ink.Faint, 13.dp) }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        ) {
            if (query.isBlank()) {
                item {
                    Column(Modifier.padding(20.dp)) {
                        Text("Try one of these", color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily)
                        Spacer(Modifier.height(12.dp))
                        hints.chunked(2).forEach { pair ->
                            Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { h ->
                                    Row(
                                        Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Ink.Raised)
                                            .clickable { query = h }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Glyph(G.Search, Ink.Faint, 12.dp)
                                        Spacer(Modifier.width(7.dp))
                                        Text(h, color = Ink.Text, fontSize = 12.sp, fontFamily = SoraFamily)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (busy) {
                item {
                    Text(
                        "Searching…",
                        color = Ink.Muted,
                        fontSize = 12.sp,
                        fontFamily = SoraFamily,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }

            error?.let { msg ->
                item {
                    Panel(Modifier.padding(20.dp).fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("No luck reaching YouTube Music", color = Ink.Text, fontSize = 13.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text(msg, color = Ink.Muted, fontSize = 11.5.sp, fontFamily = SoraFamily)
                        }
                    }
                }
            }

            val r = results
            if (r != null && !busy) {
                if (r.songs.isNotEmpty()) {
                    item { SectionHeader("Songs") }
                    itemsIndexed(r.songs) { i, t ->
                        Box(Modifier.padding(horizontal = 8.dp)) {
                            TrackRow(
                                track = t,
                                playing = state.isPlaying,
                                isCurrent = state.track?.id == t.id,
                                onPlay = { onPlay(r.songs, i) },
                            )
                        }
                    }
                }
                if (r.albums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(r.albums) { c -> SquareCard(c, onClick = { onOpenCollection(c) }) }
                        }
                    }
                }
                if (r.artists.isNotEmpty()) {
                    item { SectionHeader("Artists") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(r.artists) { c -> CircleCard(c, onClick = { onOpenCollection(c) }) }
                        }
                    }
                }
                if (r.playlists.isNotEmpty()) {
                    item { SectionHeader("Playlists") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(r.playlists) { c -> SquareCard(c, onClick = { onOpenCollection(c) }) }
                        }
                    }
                }
                if (r.isEmpty) {
                    item {
                        Text(
                            "Nothing matched “$query”.",
                            color = Ink.Muted,
                            fontSize = 12.5.sp,
                            fontFamily = SoraFamily,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            }
        }
    }
}
