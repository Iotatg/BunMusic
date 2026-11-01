package app.bunmusic.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import app.bunmusic.core.LocalBunWindow
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Collection
import app.bunmusic.data.SearchResults
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import app.bunmusic.data.YtMusic
import app.bunmusic.player.PlayerState
import kotlinx.coroutines.delay
import java.util.Locale

private val hints = listOf("lofi beats", "arijit singh", "the weeknd", "jazz café", "karan aujla", "ambient focus")
private val filters = listOf("Top", "Songs", "Albums", "Artists", "Playlists")

@Composable
fun SearchScreen(
    state: PlayerState,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onMore: (Track) -> Unit,
    initialQuery: String? = null,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResults()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var history by remember { mutableStateOf(Store.queries()) }
    var filter by remember { mutableStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    val gutter = LocalBunWindow.current.gutter

    fun runSearch(q: String) {
        if (q.isBlank()) return
        query = q
        keyboard?.hide()
        Store.pushQuery(q)
        history = Store.queries()
        loading = true
        searched = true
    }

    // Opened from a credit, an album line or a mix card: search immediately,
    // instead of dropping the listener on an empty field holding the word they
    // just tapped.
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) runSearch(initialQuery)
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!heard.isNullOrBlank()) runSearch(heard)
        }
    }

    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a song, artist or mood")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { voice.launch(intent) }
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = SearchResults()
            searched = false
            return@LaunchedEffect
        }
        delay(220)
        runCatching { YtMusic.search(query) }
            .onSuccess { results = it }
            .onFailure { results = SearchResults() }
        loading = false
    }

    LaunchedEffect(query) {
        if (query.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(320)
        suggestions = runCatching { YtMusic.suggestions(query) }.getOrDefault(emptyList()).take(6)
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { ScreenTitle("Search", "YouTube Music, ad-free") }

        item {
            Row(
                Modifier
                    .padding(horizontal = gutter, vertical = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ink.Card)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(G.Search, Ink.Faint, 17.dp)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Ink.Text, fontSize = 14.sp, fontFamily = SoraFamily),
                    cursorBrush = SolidColor(Ink.Lime),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch(query) }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text("What do you want to hear?", color = Ink.Faint, fontSize = 14.sp, fontFamily = SoraFamily)
                        }
                        inner()
                    },
                )
                if (query.isNotEmpty()) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).clickable { query = "" },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(G.Close, Ink.Faint, 13.dp) }
                    Spacer(Modifier.width(6.dp))
                }
                Box(
                    Modifier.size(26.dp).clip(CircleShape).clickable { listen() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(G.Mic, Ink.Muted, 15.dp) }
            }
        }

        if (query.isBlank()) {
            if (history.isNotEmpty()) {
                item {
                    SectionHeader("Recent searches", "Clear", {
                        Store.clearQueries()
                        history = emptyList()
                    })
                }
                item {
                    Column(Modifier.padding(horizontal = gutter)) {
                        history.forEach { q ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { runSearch(q) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Glyph(G.Clock, Ink.Faint, 15.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(q, color = Ink.Text, fontSize = 13.sp, fontFamily = SoraFamily)
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Try these") }
            item {
                Column(Modifier.padding(horizontal = gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    hints.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { h -> Chip(h, leading = G.Sparkle) { runSearch(h) } }
                        }
                    }
                }
            }
        } else if (suggestions.isNotEmpty() && !searched) {
            item {
                Column(Modifier.padding(horizontal = gutter, vertical = 6.dp)) {
                    suggestions.forEach { s ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { runSearch(s) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Glyph(G.Search, Ink.Faint, 15.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(s, color = Ink.Text, fontSize = 13.sp, fontFamily = SoraFamily)
                        }
                    }
                }
            }
        }

        if (searched) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = gutter, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filters.size) { i ->
                        Chip(filters[i], selected = filter == i) { filter = i }
                    }
                }
            }
        }

        if (loading) {
            items(5) { ShimmerRow() }
        }

        if (!loading && searched && results.isEmpty) {
            item { EmptyState(G.Search, "Nothing found", "Try a different spelling, or search the artist instead.") }
        }

        if (filter == 0 || filter == 1) {
            itemsIndexed(results.songs, key = { i, t -> t.id + "#" + i }) { i, t ->
                Box(Modifier.padding(horizontal = 8.dp)) {
                    TrackRow(
                        track = t,
                        playing = state.isPlaying,
                        isCurrent = state.track?.id == t.id,
                        onPlay = { onPlay(results.songs, i) },
                        onMore = { onMore(t) },
                    )
                }
            }
        }

        if ((filter == 0 || filter == 2) && results.albums.isNotEmpty()) {
            item { SectionHeader("Albums") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = gutter),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(results.albums, key = { i, c -> c.id + "#" + i }) { _, c ->
                        SquareCard(c, onClick = { onOpenCollection(c) })
                    }
                }
            }
        }

        if ((filter == 0 || filter == 3) && results.artists.isNotEmpty()) {
            item { SectionHeader("Artists") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = gutter),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(results.artists, key = { i, c -> c.id + "#" + i }) { _, c ->
                        CircleCard(c, onClick = { onOpenCollection(c) })
                    }
                }
            }
        }

        if ((filter == 0 || filter == 4) && results.playlists.isNotEmpty()) {
            item { SectionHeader("Playlists") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = gutter),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(results.playlists, key = { i, c -> c.id + "#" + i }) { _, c ->
                        SquareCard(c, onClick = { onOpenCollection(c) })
                    }
                }
            }
        }
    }
}
