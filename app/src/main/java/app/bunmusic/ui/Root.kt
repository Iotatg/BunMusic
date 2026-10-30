package app.bunmusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Catalog
import app.bunmusic.data.CollectionKind
import app.bunmusic.player.Playback
import app.bunmusic.player.PlayerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.collectAsState

sealed interface Route {
    data object Home : Route
    data object Search : Route
    data object Library : Route
    data object Player : Route
    data class Detail(
        val title: String,
        val artUrl: String?,
        val artAsset: String?,
        val browseId: String?,
        val kind: CollectionKind,
    ) : Route
}

@Composable
fun Root() {
    val state by Playback.state.collectAsState()
    var stack by remember { mutableStateOf<List<Route>>(listOf(Route.Home)) }
    val route = stack.last()

    val push: (Route) -> Unit = { stack = stack + it }
    val pop: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }

    BackHandler(enabled = stack.size > 1) { pop() }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF14101C),
                    0.35f to Ink.Void,
                    1f to Ink.Void,
                )
            )
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (val r = route) {
                    Route.Home -> HomeScreen(
                        state = state,
                        onOpenSearch = { push(Route.Search) },
                        onOpenCollection = { c ->
                            push(
                                Route.Detail(
                                    title = c.title,
                                    artUrl = c.artUrl,
                                    artAsset = c.artAsset,
                                    browseId = c.browseId.ifBlank { null },
                                    kind = c.kind,
                                )
                            )
                        },
                        onPlay = { list, i -> Playback.play(list, i) },
                        onOpenPlayer = {
                            if (!state.hasTrack) Playback.play(Catalog.tracks, 0)
                            push(Route.Player)
                        },
                    )

                    Route.Search -> SearchScreen(
                        state = state,
                        onPlay = { list, i -> Playback.play(list, i) },
                        onOpenCollection = { c ->
                            push(
                                Route.Detail(
                                    title = c.title,
                                    artUrl = c.artUrl,
                                    artAsset = c.artAsset,
                                    browseId = c.browseId.ifBlank { null },
                                    kind = c.kind,
                                )
                            )
                        },
                    )

                    Route.Library -> LibraryScreen(
                        state = state,
                        onPlay = { list, i -> Playback.play(list, i) },
                        onOpenCollection = { c ->
                            push(
                                Route.Detail(
                                    title = c.title,
                                    artUrl = c.artUrl,
                                    artAsset = c.artAsset,
                                    browseId = c.browseId.ifBlank { null },
                                    kind = c.kind,
                                )
                            )
                        },
                    )

                    is Route.Detail -> DetailScreen(
                        title = r.title,
                        artUrl = r.artUrl,
                        artAsset = r.artAsset,
                        browseId = r.browseId,
                        kind = r.kind,
                        state = state,
                        onPlay = { list, i -> Playback.play(list, i) },
                        onOpenCollection = { c ->
                            push(
                                Route.Detail(
                                    title = c.title,
                                    artUrl = c.artUrl,
                                    artAsset = c.artAsset,
                                    browseId = c.browseId.ifBlank { null },
                                    kind = c.kind,
                                )
                            )
                        },
                        onBack = pop,
                    )

                    Route.Player -> Box(Modifier.fillMaxSize())
                }
            }

            if (state.hasTrack && route != Route.Player) {
                MiniPlayer(
                    state = state,
                    onOpen = { push(Route.Player) },
                    onToggle = { Playback.toggle() },
                    onNext = { Playback.next() },
                )
            }

            if (route != Route.Player) {
                BottomBar(route) { selected ->
                    stack = listOf(selected)
                }
            }
        }

        AnimatedVisibility(
            visible = route == Route.Player,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            PlayerScreen(state = state, onClose = pop)
        }
    }
}

@Composable
private fun BottomBar(current: Route, onSelect: (Route) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ink.Void.copy(alpha = 0.94f))
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem("Home", G.Home, current is Route.Home) { onSelect(Route.Home) }
        NavItem("Search", G.Search, current is Route.Search) { onSelect(Route.Search) }
        NavItem("Library", G.Library, current is Route.Library) { onSelect(Route.Library) }
    }
}

@Composable
private fun NavItem(label: String, glyph: G, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(glyph, if (selected) Ink.Lime else Ink.Faint, 21.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (selected) Ink.Lime else Ink.Faint,
            fontSize = 9.5.sp,
            fontFamily = SoraFamily,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .size(if (selected) 3.dp else 0.dp)
                .clip(CircleShape)
                .background(Ink.Lime)
        )
    }
}
