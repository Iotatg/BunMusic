package app.bunmusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.Skin
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Auth
import app.bunmusic.data.Collection
import app.bunmusic.data.CollectionKind
import app.bunmusic.data.Downloads
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import app.bunmusic.player.Playback
import app.bunmusic.player.PlayerState

sealed interface Route {
    data object Home : Route
    data object Explore : Route
    data object Library : Route
    data object Search : Route
    data object Settings : Route
    data object Stats : Route
    data object Downloads : Route
    data object Profile : Route
    data object Equalizer : Route
    data object Hotlist : Route
    data object Buns : Route
    data object About : Route
    data class Detail(
        val title: String,
        val artUrl: String?,
        val artAsset: String?,
        val browseId: String?,
        val kind: CollectionKind,
    ) : Route
    data class MoodPage(val title: String, val browseId: String, val params: String) : Route
    data class PlaylistPage(val id: String) : Route
}

private val Route.depth: Int
    get() = when (this) {
        Route.Home, Route.Explore, Route.Library, Route.Search -> 0
        else -> 1
    }

@Composable
fun Root() {
    val account by Auth.account.collectAsState()
    var guest by remember { mutableStateOf(Auth.guest) }

    if (account == null && !guest) {
        AuthScreen(onGuest = {
            Auth.continueAsGuest()
            guest = true
        })
        return
    }

    Shell(guest = guest, onLeaveGuest = { guest = false })
}

@Composable
private fun Shell(guest: Boolean, onLeaveGuest: () -> Unit) {
    val state by Playback.state.collectAsState()
    val downloads by Downloads.entries.collectAsState()
    var stack by remember { mutableStateOf<List<Route>>(listOf(Route.Home)) }
    var playerOpen by remember { mutableStateOf(false) }
    var sheetTrack by remember { mutableStateOf<Track?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    val route = stack.last()

    val push: (Route) -> Unit = { stack = stack + it }
    val pop: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
    val tab: (Route) -> Unit = { stack = listOf(it) }

    val downloadedIds = remember(downloads) { downloads.map { it.trackId }.toSet() }

    BackHandler(enabled = stack.size > 1) { pop() }
    BackHandler(enabled = playerOpen) { playerOpen = false }

    val openCollection: (Collection) -> Unit = { c ->
        push(
            Route.Detail(
                title = c.title,
                artUrl = c.artUrl,
                artAsset = c.artAsset,
                browseId = c.browseId.ifBlank { null },
                kind = c.kind,
            )
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                if (Ink.isLight) {
                    Brush.verticalGradient(listOf(Ink.Base, Ink.Base))
                } else {
                    Brush.verticalGradient(
                        0f to Color(0xFF14101C),
                        0.35f to Ink.Void,
                        1f to Ink.Void,
                    )
                }
            )
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        val forward = targetState.depth >= initialState.depth
                        val enter = if (forward) {
                            slideInHorizontally(tween(280)) { it / 8 } + fadeIn(tween(240))
                        } else {
                            slideInHorizontally(tween(280)) { -it / 8 } + fadeIn(tween(240))
                        }
                        val exit = if (forward) {
                            slideOutHorizontally(tween(220)) { -it / 12 } + fadeOut(tween(180))
                        } else {
                            slideOutHorizontally(tween(220)) { it / 12 } + fadeOut(tween(180))
                        }
                        enter togetherWith exit
                    },
                    label = "screen",
                ) { r ->
                    when (r) {
                        Route.Home -> HomeScreen(
                            state = state,
                            downloadedIds = downloadedIds,
                            onOpenSearch = { tab(Route.Search) },
                            onOpenExplore = { tab(Route.Explore) },
                            onOpenCollection = openCollection,
                            onOpenMood = { m -> push(Route.MoodPage(m.title, m.browseId, m.params)) },
                            onPlay = { list, i -> Playback.play(list, i) },
                            onOpenPlayer = { if (state.hasTrack) playerOpen = true },
                            onMore = { sheetTrack = it },
                            onOpenSettings = { push(Route.Settings) },
                        )

                        Route.Explore -> ExploreScreen(
                            onOpenCollection = openCollection,
                            onOpenMood = { m -> push(Route.MoodPage(m.title, m.browseId, m.params)) },
                            onPlay = { list, i -> Playback.play(list, i) },
                            onMore = { sheetTrack = it },
                            onOpenCharts = { push(Route.Detail("Charts", null, null, "FEmusic_charts", CollectionKind.PLAYLIST)) },
                        )

                        Route.Library -> LibraryScreen(
                            state = state,
                            downloadedIds = downloadedIds,
                            onPlay = { list, i -> Playback.play(list, i) },
                            onOpenCollection = openCollection,
                            onOpenPlaylist = { push(Route.PlaylistPage(it)) },
                            onOpenDownloads = { push(Route.Downloads) },
                            onOpenStats = { push(Route.Stats) },
                            onMore = { sheetTrack = it },
                        )

                        Route.Search -> SearchScreen(
                            state = state,
                            onPlay = { list, i -> Playback.play(list, i) },
                            onOpenCollection = openCollection,
                            onMore = { sheetTrack = it },
                        )

                        Route.Settings -> SettingsScreen(
                            onBack = pop,
                            onOpenStats = { push(Route.Stats) },
                            onOpenDownloads = { push(Route.Downloads) },
                            onOpenProfile = { push(Route.Profile) },
                            onOpenEqualizer = { push(Route.Equalizer) },
                            onOpenBuns = { push(Route.Buns) },
                            onOpenAbout = { push(Route.About) },
                            onToast = { toast = it },
                        )

                        Route.Profile -> ProfileScreen(
                            onBack = pop,
                            onSignIn = {
                                stack = listOf(Route.Home)
                                onLeaveGuest()
                            },
                            onToast = { toast = it },
                        )

                        Route.Equalizer -> EqualizerScreen(onBack = pop)

                        Route.Buns -> BunsScreen(onBack = pop)

                        Route.About -> AboutScreen(onBack = pop)

                        Route.Hotlist -> HotlistScreen(
                            state = state,
                            downloadedIds = downloadedIds,
                            onPlay = { list, i -> Playback.play(list, i) },
                            onMore = { sheetTrack = it },
                        )

                        Route.Stats -> StatsScreen(onBack = pop)
                        Route.Downloads -> DownloadsScreen(onBack = pop, onPlay = { list, i -> Playback.play(list, i) })

                        is Route.PlaylistPage -> PlaylistScreen(
                            playlistId = r.id,
                            state = state,
                            onBack = pop,
                            onPlay = { list, i -> Playback.play(list, i) },
                            onMore = { sheetTrack = it },
                        )

                        is Route.MoodPage -> MoodScreen(
                            title = r.title,
                            browseId = r.browseId,
                            params = r.params,
                            onBack = pop,
                            onOpenCollection = openCollection,
                        )

                        is Route.Detail -> DetailScreen(
                            title = r.title,
                            artUrl = r.artUrl,
                            artAsset = r.artAsset,
                            browseId = r.browseId,
                            kind = r.kind,
                            state = state,
                            downloadedIds = downloadedIds,
                            onPlay = { list, i -> Playback.play(list, i) },
                            onOpenCollection = openCollection,
                            onBack = pop,
                            onMore = { sheetTrack = it },
                        )
                    }
                }
            }

            if (Ink.isLight) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    Box(Modifier.fillMaxWidth().height(if (state.hasTrack) 122.dp else 84.dp)) {
                        if (state.hasTrack) {
                            Box(Modifier.align(Alignment.TopCenter)) {
                                MiniPlayer(
                                    state = state,
                                    onOpen = { playerOpen = true },
                                    onToggle = { Playback.toggle() },
                                    onNext = { Playback.next() },
                                )
                            }
                        }
                        BottomNav(
                            current = route,
                            onSelect = { tab(it) },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            } else {
                if (state.hasTrack) {
                    MiniPlayer(
                        state = state,
                        onOpen = { playerOpen = true },
                        onToggle = { Playback.toggle() },
                        onNext = { Playback.next() },
                    )
                }

                BottomNav(route, { tab(it) })
            }
        }

        AnimatedVisibility(
            visible = playerOpen,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(180)),
        ) {
            PlayerScreen(
                state = state,
                onClose = { playerOpen = false },
                onMore = { sheetTrack = state.track },
            )
        }

        sheetTrack?.let { track ->
            TrackSheet(
                track = track,
                isLiked = Store.isLiked(track.id),
                isDownloaded = Downloads.isDownloaded(track.id),
                onDismiss = { sheetTrack = null },
                onToast = { toast = it },
            )
        }

        toast?.let { msg ->
            Toast(msg, onDone = { toast = null })
        }
    }
}

@Composable
private fun Toast(message: String, onDone: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(message) {
        kotlinx.coroutines.delay(1900)
        onDone()
    }
    Box(
        Modifier.fillMaxSize().padding(bottom = 120.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Ink.Sheet)
                .border(1.dp, Ink.Hairline, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(message, color = Ink.Text, fontSize = 12.sp, fontFamily = SoraFamily)
        }
    }
}

// --------------------------------------------------------------- bottom nav

private data class NavTab(val label: String, val glyph: G, val route: Route)

/** The two buns disagree about the fourth tab: Explore, or a ranked Hotlist. */
private fun tabsFor(light: Boolean): List<NavTab> = if (light) {
    listOf(
        NavTab("Home", G.Home, Route.Home),
        NavTab("Search", G.Search, Route.Search),
        NavTab("Library", G.Library, Route.Library),
        NavTab("Hotlist", G.Flame, Route.Hotlist),
    )
} else {
    listOf(
        NavTab("Home", G.Home, Route.Home),
        NavTab("Explore", G.Compass, Route.Explore),
        NavTab("Library", G.Library, Route.Library),
        NavTab("Search", G.Search, Route.Search),
    )
}

@Composable
private fun BottomNav(
    current: Route,
    onSelect: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (Ink.isLight) {
        LightNav(current, onSelect, modifier)
    } else {
        DarkNav(current, onSelect, modifier)
    }
}

@Composable
private fun DarkNav(current: Route, onSelect: (Route) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Ink.NavBg.copy(alpha = 0.96f))
                .border(1.dp, Ink.Hairline, RoundedCornerShape(26.dp))
                .padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabsFor(false).forEach { t ->
                val selected = current == t.route
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelect(t.route) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Ink.Lime.copy(alpha = 0.14f) else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Glyph(t.glyph, if (selected) Ink.Lime else Ink.Faint, 19.dp)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        t.label,
                        color = if (selected) Ink.Text else Ink.Faint,
                        fontSize = 9.5.sp,
                        fontFamily = SoraFamily,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun LightNav(current: Route, onSelect: (Route) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .shadow(16.dp, shape, clip = false, ambientColor = Ink.Shadow, spotColor = Ink.Shadow)
            .clip(shape)
            .background(Ink.NavBg)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabsFor(true).forEach { t ->
            val selected = current == t.route
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSelect(t.route) }
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Ink.NavActiveBg else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(
                        t.glyph,
                        if (selected) Ink.NavActiveInk else Ink.Faint,
                        19.dp,
                        if (selected) 2.1.dp else 1.7.dp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    t.label,
                    color = if (selected) Ink.Text else Ink.Faint,
                    fontSize = 10.sp,
                    fontFamily = SoraFamily,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun CountBadge(count: Int) {
    if (count <= 0) return
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(Ink.Lime),
        contentAlignment = Alignment.Center,
    ) {
        Text("$count", color = Ink.OnAccent, fontSize = 9.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SpacerH(width: Int) = Spacer(Modifier.width(width.dp))
