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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.BunWindow
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.LocalBunWindow
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
    data object YearReview : Route
    data object Downloads : Route
    data object Profile : Route
    data object Equalizer : Route
    data object Hotlist : Route
    data object Buns : Route
    data object About : Route
    data object Widgets : Route
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
fun Root(launch: Launch = Launch()) {
    val account by Auth.account.collectAsState()
    var guest by remember { mutableStateOf(Auth.guest) }

    if (account == null && !guest) {
        AuthScreen(onGuest = {
            Auth.continueAsGuest()
            guest = true
        })
        return
    }

    Shell(guest = guest, onLeaveGuest = { guest = false }, launch = launch)
}

@Composable
private fun Shell(guest: Boolean, onLeaveGuest: () -> Unit, launch: Launch = Launch()) {
    val state by Playback.state.collectAsState()
    val downloads by Downloads.entries.collectAsState()
    val window = LocalBunWindow.current
    // Playback ticks twice a second with positionMs. Every screen used to be
    // handed that object, so the whole visible page recomposed at 2 Hz to draw a
    // number none of them show. Screens get the tick-free copy; the mini player
    // and the player screen keep the live one, because they draw the progress.
    val chrome by remember { derivedStateOf { state.copy(positionMs = 0L) } }
    var stack by remember { mutableStateOf<List<Route>>(listOf(Route.Home)) }
    var playerOpen by remember { mutableStateOf(false) }
    var queueOnStart by remember { mutableStateOf(false) }
    var paletteOpen by remember { mutableStateOf(false) }
    var sheetTrack by remember { mutableStateOf<Track?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    // Measured, not guessed: the toast used to be lifted by a hard-coded 120 dp,
    // which cleared the bar on a phone and floated in the middle of a tablet.
    var chromePx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    val route = stack.last()

    val push: (Route) -> Unit = { stack = stack + it }
    val pop: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
    val tab: (Route) -> Unit = { stack = listOf(it) }

    // A tap on a widget, a shortcut or the tile drops the user where they asked
    // to be. `seq` makes a second tap while the app is open count as a new
    // request; before this, extras were read once in onCreate and then ignored.
    LaunchedEffect(launch.seq) {
        if (launch.player) playerOpen = true
        if (launch.queue) {
            queueOnStart = true
            playerOpen = true
        }
        if (launch.search) tab(Route.Search)
        if (launch.commands) paletteOpen = true
        if (launch.shuffle) {
            val liked = Store.likedTracks()
            if (liked.isEmpty()) toast = "Like a few songs first" else Playback.shufflePlay(liked)
        }
    }

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
                    // The top glow used to be a hard-coded Midnight purple, which
                    // tinted every future dark bun. It reads the bun's own mini
                    // player tone now.
                    Brush.verticalGradient(
                        0f to Ink.MiniBg,
                        0.35f to Ink.Void,
                        1f to Ink.Void,
                    )
                }
            )
    ) {
        Row(Modifier.fillMaxSize()) {
            // 600 dp and up: the tabs move to the edge a thumb isn't holding, and
            // the content keeps the 84 dp the bottom bar used to eat — which on a
            // tablet was the difference between seeing the transport row and not.
            if (window.wide) {
                NavRail(
                    current = route,
                    onSelect = { tab(it) },
                    onSettings = { push(Route.Settings) },
                )
            }

            Column(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                AnimatedContent(
                    modifier = Modifier.widthIn(max = window.contentMax).fillMaxSize(),
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
                            state = chrome,
                            downloadedIds = downloadedIds,
                            onOpenSearch = { tab(Route.Search) },
                            onOpenExplore = { tab(Route.Explore) },
                            onOpenCollection = openCollection,
                            onOpenMood = { m -> push(Route.MoodPage(m.title, m.browseId, m.params)) },
                            onPlay = { list, i -> Playback.play(list, i) },
                            onOpenPlayer = { if (state.hasTrack) playerOpen = true },
                            onTogglePlay = { Playback.toggle() },
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
                            state = chrome,
                            downloadedIds = downloadedIds,
                            onPlay = { list, i -> Playback.play(list, i) },
                            onOpenCollection = openCollection,
                            onOpenPlaylist = { push(Route.PlaylistPage(it)) },
                            onOpenDownloads = { push(Route.Downloads) },
                            onOpenStats = { push(Route.Stats) },
                            onMore = { sheetTrack = it },
                            onToast = { toast = it },
                        )

                        Route.Search -> SearchScreen(
                            state = chrome,
                            onPlay = { list, i -> Playback.play(list, i) },
                            onOpenCollection = openCollection,
                            onMore = { sheetTrack = it },
                        )

                        Route.Settings -> SettingsScreen(
                            onBack = pop,
                            onOpenStats = { push(Route.Stats) },
                            onOpenWrapped = { push(Route.YearReview) },
                            onOpenDownloads = { push(Route.Downloads) },
                            onOpenProfile = { push(Route.Profile) },
                            onOpenEqualizer = { push(Route.Equalizer) },
                            onOpenBuns = { push(Route.Buns) },
                            onOpenAbout = { push(Route.About) },
                            onOpenWidgets = { push(Route.Widgets) },
                            onOpenCommands = { paletteOpen = true },
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

                        Route.Widgets -> WidgetsScreen(
                            onBack = pop,
                            onToast = { toast = it },
                        )

                        Route.Hotlist -> HotlistScreen(
                            state = chrome,
                            downloadedIds = downloadedIds,
                            onPlay = { list, i -> Playback.play(list, i) },
                            onMore = { sheetTrack = it },
                        )

                        Route.Stats -> StatsScreen(onBack = pop)
                        Route.YearReview -> WrappedScreen(onBack = pop)
                        Route.Downloads -> DownloadsScreen(onBack = pop, onPlay = { list, i -> Playback.play(list, i) })

                        is Route.PlaylistPage -> PlaylistScreen(
                            playlistId = r.id,
                            state = chrome,
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
                            state = chrome,
                            downloadedIds = downloadedIds,
                            onPlay = { list, i -> Playback.play(list, i) },
                            onOpenCollection = openCollection,
                            onBack = pop,
                            onMore = { sheetTrack = it },
                        )
                    }
                }
            }

            // ---- bottom chrome
            BottomChrome(
                state = state,
                route = route,
                window = window,
                onOpen = { playerOpen = true },
                onToggle = { Playback.toggle() },
                onNext = { Playback.next() },
                onSelect = { tab(it) },
                modifier = Modifier.onSizeChanged { chromePx = it.height },
            )
            }
        }

        AnimatedVisibility(
            visible = playerOpen,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(180)),
        ) {
            PlayerScreen(
                state = state,
                onClose = {
                    playerOpen = false
                    queueOnStart = false
                },
                onMore = { sheetTrack = state.track },
                openQueueOnStart = queueOnStart,
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
            Toast(
                message = msg,
                bottom = with(density) { chromePx.toDp() } + 12.dp,
                onDone = { toast = null },
            )
        }

        if (paletteOpen) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val host = remember(context) {
                object : CommandHost {
                    override val context: android.content.Context = context
                    override fun go(route: Route) {
                        if (route.depth == 0) tab(route) else push(route)
                    }
                    override fun openPlayer() {
                        if (Playback.state.value.hasTrack) playerOpen = true
                    }
                    override fun openQueue() {
                        if (Playback.state.value.hasTrack) {
                            queueOnStart = true
                            playerOpen = true
                        }
                    }
                    override fun toast(message: String) {
                        toast = message
                    }
                    override fun close() {
                        paletteOpen = false
                    }
                }
            }
            CommandPalette(host = host, onDismiss = { paletteOpen = false })
        }
    }
}

@Composable
private fun Toast(message: String, bottom: androidx.compose.ui.unit.Dp, onDone: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(message) {
        kotlinx.coroutines.delay(1900)
        onDone()
    }
    Box(
        Modifier.fillMaxSize().padding(bottom = bottom),
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

/**
 * Everything pinned under the content: the now-playing strip, then the tab bar.
 *
 * This used to be two hand-tuned branches — one for the light buns, one for the
 * dark — and the light one sized itself with a hard-coded `height(122.dp)` box
 * holding both the strip (62 dp) and the bar (87 dp). They don't fit in 122 dp,
 * so the bar was drawn straight over the bottom half of the strip: the playing
 * bar looked cut off at the screen edge, and the artwork with it.
 *
 * There is no arithmetic here now. A Column is as tall as its children, the
 * window inset is applied once at the container, and the content above gets
 * exactly the space that is left — which is what "the bar is at the bottom" was
 * supposed to mean in the first place.
 */
@Composable
private fun BottomChrome(
    state: PlayerState,
    route: Route,
    window: BunWindow,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onSelect: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = if (window.wide) 10.dp else 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.hasTrack) {
            // Capped: on a tablet the strip was a full-width 1400 dp bar with a
            // 44 dp thumbnail at one end and a play button a metre away.
            Box(Modifier.widthIn(max = window.miniMax).fillMaxWidth()) {
                MiniPlayer(
                    state = state,
                    onOpen = onOpen,
                    onToggle = onToggle,
                    onNext = onNext,
                )
            }
        }
        if (!window.wide) {
            // Same reason: a floating pill that spans the bezel stops reading as
            // a pill. 560 dp is the widest four labels need.
            Box(Modifier.widthIn(max = window.navMax).fillMaxWidth()) {
                BottomNav(current = route, onSelect = onSelect)
            }
        }
    }
}

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
                        fontSize = 10.sp,
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
                    fontSize = 11.sp,
                    fontFamily = SoraFamily,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * The tab bar, stood on its side.
 *
 * Same four destinations, same glyphs, same selected fill as [DarkNav] /
 * [LightNav] — only the axis changes, so switching between a phone and a tablet
 * doesn't feel like a different app. Labels are kept: an icon-only rail makes the
 * listener guess what a compass means.
 */
@Composable
private fun NavRail(current: Route, onSelect: (Route) -> Unit, onSettings: () -> Unit) {
    val window = LocalBunWindow.current
    val light = Ink.isLight
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxHeight()
            .width(window.railWidth)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp)
            .then(
                if (light) {
                    Modifier.shadow(14.dp, shape, clip = false, ambientColor = Ink.Shadow, spotColor = Ink.Shadow)
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(Ink.NavBg)
            .then(if (light) Modifier.border(1.dp, Ink.Hairline, shape) else Modifier)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Ink.Lime.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Glyph(G.Disc, Ink.Lime, 17.dp) }

        Spacer(Modifier.height(20.dp))

        tabsFor(light).forEach { t ->
            RailItem(t.label, t.glyph, current == t.route) { onSelect(t.route) }
        }

        Spacer(Modifier.weight(1f))

        RailItem("Settings", G.Gear, current == Route.Settings, onSettings)
    }
}

@Composable
private fun RailItem(label: String, glyph: G, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Ink.NavActiveBg else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(
            glyph,
            if (selected) Ink.NavActiveInk else Ink.Faint,
            20.dp,
            if (Ink.isLight) 2.0.dp else 1.7.dp,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            color = if (selected) Ink.Text else Ink.Faint,
            fontSize = 10.sp,
            fontFamily = SoraFamily,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
fun CountBadge(count: Int) {
    if (count <= 0) return
    // A fixed 18 dp circle clipped anything past two digits, and even "10" was
    // pressed against the edge. It grows with its label now and caps the number.
    Box(
        Modifier
            .heightIn(min = 18.dp)
            .defaultMinSize(minWidth = 18.dp)
            .clip(CircleShape)
            .background(Ink.Lime)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else "$count",
            color = Ink.OnAccent,
            fontSize = 10.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun SpacerH(width: Int) = Spacer(Modifier.width(width.dp))
