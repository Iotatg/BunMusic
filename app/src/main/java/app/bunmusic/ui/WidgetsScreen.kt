package app.bunmusic.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Prefs
import app.bunmusic.widget.WidgetArt
import app.bunmusic.widget.WidgetKind
import app.bunmusic.widget.WidgetLook
import app.bunmusic.widget.WidgetPreview
import app.bunmusic.widget.WidgetShape
import app.bunmusic.widget.WidgetSkin
import app.bunmusic.widget.WidgetStore
import app.bunmusic.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings → Widgets.
 *
 * Four sizes, one set of defaults, and an honest count of what is already on the
 * home screen. Pinning is a request to the launcher, not a command — Android
 * lets the user confirm placement, and some launchers say no, which is why the
 * button reports back instead of assuming it worked.
 */
@Composable
fun WidgetsScreen(onBack: () -> Unit, onToast: (String) -> Unit) {
    val context = LocalContext.current
    var look by remember { mutableStateOf(WidgetStore.defaults(context)) }
    var tick by remember { mutableIntStateOf(0) }
    val placed = remember(tick) { Widgets.placed(context) }
    val artBytes by produceState(0L, tick) {
        value = withContext(Dispatchers.IO) { WidgetArt.bytes(context) }
    }

    fun save(next: WidgetLook) {
        look = next
        WidgetStore.setDefaults(context, next)
        Widgets.refresh(context)
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item { BackBar("Widgets", onBack) }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Display("Home screen", 30)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Four widgets, one style. Tap one to ask your launcher to place it.",
                    color = Ink.Muted,
                    fontSize = 12.sp,
                    fontFamily = SoraFamily,
                )
            }
        }

        item {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                WidgetPreview(look)
            }
        }

        item { WidgetGroup("Add a widget") }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                WidgetKind.entries.forEach { kind ->
                    WidgetAddRow(
                        kind = kind,
                        placed = placed[kind] ?: 0,
                        onAdd = {
                            if (Widgets.pin(context, kind)) {
                                onToast("Confirm it on your home screen")
                            } else {
                                onToast("This launcher cannot pin widgets — add it from the widget list")
                            }
                            tick++
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        item { WidgetGroup("Default style") }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "New widgets start here. Each one can still be changed afterwards by long-pressing it.",
                    color = Ink.Faint,
                    fontSize = 11.sp,
                    fontFamily = SoraFamily,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetSkin.entries.take(3).forEach { skin ->
                        Chip(skin.label, selected = look.skin == skin) { save(look.copy(skin = skin)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetSkin.entries.drop(3).forEach { skin ->
                        Chip(skin.label, selected = look.skin == skin) { save(look.copy(skin = skin)) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "CORNERS",
                    color = Ink.Faint,
                    fontSize = 9.5.sp,
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetShape.entries.forEach { shape ->
                        Chip(shape.label, selected = look.shape == shape) { save(look.copy(shape = shape)) }
                    }
                }
            }
        }

        item { WidgetGroup("What the card shows") }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Progress", selected = look.progress) { save(look.copy(progress = !look.progress)) }
                    Chip("Times", selected = look.times) { save(look.copy(times = !look.times)) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Like", selected = look.like) { save(look.copy(like = !look.like)) }
                    Chip("Queue", selected = look.queue) { save(look.copy(queue = !look.queue)) }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "A short cell drops the time row before it drops the bar, so nothing is ever clipped.",
                    color = Ink.Faint,
                    fontSize = 11.sp,
                    fontFamily = SoraFamily,
                )
            }
        }

        item { WidgetGroup("Maintenance") }

        item {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Ink.Card),
            ) {
                ActionRow(
                    title = "Redraw every widget",
                    subtitle = "After a launcher update or a bun change",
                    glyph = G.Refresh,
                ) {
                    Widgets.refresh(context)
                    tick++
                    onToast("Widgets redrawn")
                }
                Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Ink.Hairline))
                ActionRow(
                    title = "Clear widget art",
                    subtitle = if (artBytes == 0L) "Empty" else fmtBytes(artBytes),
                    glyph = G.Trash,
                ) {
                    WidgetArt.clear(context)
                    Widgets.refresh(context)
                    tick++
                    onToast("Widget art cleared")
                }
            }
        }

        item {
            Text(
                "Widgets read the same player as the app: nothing is duplicated, and a tap on a cover " +
                    "opens the player at the track that is actually playing.",
                color = Ink.Faint,
                fontSize = 11.sp,
                fontFamily = SoraFamily,
                lineHeight = 17.sp,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Composable
private fun WidgetGroup(text: String) {
    Text(
        text.uppercase(),
        color = Ink.Faint,
        fontSize = 10.sp,
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.9.sp,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun WidgetAddRow(kind: WidgetKind, placed: Int, onAdd: () -> Unit) {
    val (name, size, note) = when (kind) {
        WidgetKind.MEDIA -> Triple("Now playing", "4 × 2", "Cover, progress, like, queue")
        WidgetKind.COMPACT -> Triple("Cover", "2 × 2", "Artwork and one button")
        WidgetKind.BAR -> Triple("Strip", "4 × 1", "One row, for a crowded page")
        WidgetKind.QUEUE -> Triple("Queue", "4 × 4", "What plays next, tappable")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.Card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Ink.Field),
            contentAlignment = Alignment.Center,
        ) { Glyph(G.Grid, Ink.Muted, 16.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = Ink.Text, fontSize = 13.5.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text(size, color = Ink.Faint, fontSize = 10.sp, fontFamily = SoraFamily)
            }
            Text(
                if (placed > 0) "$note · $placed placed" else note,
                color = Ink.Faint,
                fontSize = 11.sp,
                fontFamily = SoraFamily,
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(Ink.Lime)
                .clickable { onAdd() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text("Add", color = Ink.OnAccent, fontSize = 11.5.sp, fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, glyph: G, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(Ink.Hairline),
            contentAlignment = Alignment.Center,
        ) { Glyph(glyph, Ink.Muted, 14.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.Text, fontSize = 13.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily)
        }
        Glyph(G.Forward, Ink.Faint, 13.dp)
    }
}
