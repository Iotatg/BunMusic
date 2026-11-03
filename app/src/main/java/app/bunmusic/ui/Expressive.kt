package app.bunmusic.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.BunShape
import app.bunmusic.core.Contrast
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Haptics
import app.bunmusic.core.Ink
import app.bunmusic.core.LocalReducedMotion
import app.bunmusic.core.SoraFamily
import app.bunmusic.core.Shapes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The parts of Material 3 Expressive that are actually components.
 *
 * The research is blunt about why this matters: expressive screens are read up
 * to four times faster, and the mechanisms are specific — shape variety, a
 * contained group with one clear action, motion that behaves like an object, and
 * one hero moment per screen. Each composable below implements one of those and
 * nothing else, so a screen can adopt them without a redesign.
 */

// --------------------------------------------------------------------- shapes

/**
 * The shape preference, read once at the shell instead of once per row.
 *
 * A `collectAsState` inside a list row would open one subscription per row; a
 * composition local is one value the whole tree reads.
 */
val LocalCoverShape = androidx.compose.runtime.compositionLocalOf { "AUTO" }

/**
 * The silhouette for one cover.
 *
 * `AUTO` lets the id choose, so an album always looks like itself and a shelf of
 * albums looks like a shelf rather than a spreadsheet. Any other value is the
 * user's choice and applies to every cover.
 */
@Composable
fun coverShape(seed: String?): BunShape {
    val pref = LocalCoverShape.current
    return if (pref.equals("AUTO", ignoreCase = true)) Shapes.pick(seed) else BunShape.byId(pref)
}

/**
 * A [BunShape] at a real size.
 *
 * Corner radii are fractions of the short side, so a 48 dp row thumbnail and a
 * 320 dp cover get the same silhouette rather than the same number of pixels.
 */
@Composable
fun bunShape(shape: BunShape, side: Dp): Shape {
    val px = with(LocalDensity.current) { side.toPx() }
    val r = Shapes.radii(shape, px)
    return RoundedCornerShape(r.topStart, r.topEnd, r.bottomEnd, r.bottomStart)
}

// ---------------------------------------------------------------- split button

/**
 * One action, plus the variants of it.
 *
 * "Play all" and "Shuffle" used to be two pills of equal weight, which made the
 * screen ask a question it should have answered. A split button leads with the
 * action people want 90% of the time and hides the rest one tap away — the shape
 * Material 3 Expressive added for exactly this.
 */
@Composable
fun SplitButton(
    label: String,
    glyph: G = G.Play,
    onClick: () -> Unit,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(42.dp)
            .clip(shape)
            .background(Ink.Lime),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .clickable { onClick() }
                .padding(start = 16.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(glyph, Ink.OnAccent, 15.dp, 2.2.dp)
            Spacer(Modifier.width(7.dp))
            Text(
                label,
                color = Ink.OnAccent,
                fontSize = 12.5.sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (actions.isNotEmpty()) {
            Box(
                Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(Ink.OnAccent.copy(alpha = 0.22f)),
            )
            Box {
                Box(
                    Modifier
                        .size(40.dp)
                        .clickable {
                            Haptics.tick()
                            open = true
                        },
                    contentAlignment = Alignment.Center,
                ) { Glyph(G.Back, Ink.OnAccent, 13.dp, 2.2.dp) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    actions.forEach { (title, run) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    title,
                                    color = Ink.Text,
                                    fontSize = 12.5.sp,
                                    fontFamily = SoraFamily,
                                )
                            },
                            onClick = {
                                open = false
                                run()
                            },
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------- segmented group

/**
 * Two to four mutually exclusive choices, in one container.
 *
 * The library's four tabs were chips that scrolled sideways, which is right for
 * tags and wrong for a switch: at a glance you could not tell how many options
 * existed. A segmented group draws its own track and slides an indicator, so the
 * control says "pick one of these three" without a label.
 */
@Composable
fun SegmentedGroup(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val index = selected.coerceIn(0, items.lastIndex)
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Field)
            .padding(3.dp),
    ) {
        val segment = maxWidth / items.size
        val reduced = LocalReducedMotion.current
        val offset by animateDpAsState(
            targetValue = segment * index,
            animationSpec = if (reduced) tween(0) else spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
            label = "segment",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .height(30.dp)
                .clip(shape)
                .background(Ink.Lime),
        )
        Row(Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .width(segment)
                        .height(30.dp)
                        .clickable {
                            if (i != index) {
                                Haptics.tick()
                                onSelect(i)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (i == index) Ink.OnAccent else Ink.Muted,
                        fontSize = 11.5.sp,
                        fontFamily = SoraFamily,
                        fontWeight = if (i == index) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------- loading

/**
 * A loading indicator that moves like a thing rather than a spinner.
 *
 * Three dots that breathe out of phase. Under reduced motion they sit still at
 * equal weight — the information ("something is happening") survives, the
 * movement does not.
 */
@Composable
fun BunLoader(
    color: Color = Ink.Lime,
    dot: Dp = 7.dp,
    modifier: Modifier = Modifier,
) {
    val gap = dot / 2
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (LocalReducedMotion.current) {
            repeat(3) {
                Box(Modifier.size(dot).clip(CircleShape).background(color.copy(alpha = 0.65f)))
            }
        } else {
            val transition = rememberInfiniteTransition(label = "loader")
            repeat(3) { i ->
                val scale by transition.animateFloat(
                    initialValue = 0.55f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(520, delayMillis = i * 130),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                    ),
                    label = "dot$i",
                )
                Box(
                    Modifier
                        .size(dot)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.45f + 0.55f * scale)),
                )
            }
        }
    }
}

// -------------------------------------------------------------- swipe actions

/**
 * A row you can throw sideways: right queues it next, left queues it last.
 *
 * This is the gesture every list in every music app has and this one did not.
 * The row follows the finger, a labelled panel is revealed underneath, and the
 * action fires only past a threshold — so a horizontal flick while scrolling
 * costs nothing.
 */
@Composable
fun SwipeActionRow(
    onPlayNext: (() -> Unit)? = null,
    onQueue: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (onPlayNext == null && onQueue == null) {
        content()
        return
    }
    val density = LocalDensity.current
    val threshold = with(density) { 86.dp.toPx() }
    val limit = with(density) { 128.dp.toPx() }
    var dx by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    fun settle() {
        val start = dx
        if (start == 0f) return
        scope.launch {
            animate(
                0f,
                start,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) { value, _ -> dx = value }
        }
    }

    Box(modifier.fillMaxWidth()) {
        // Revealed panels. Drawn under the row, so they only appear once the row
        // has actually moved.
        Row(
            Modifier.matchParentSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dx > 0f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(G.Next, Ink.Mint, 15.dp, 2.1.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Play next",
                        color = Ink.Mint,
                        fontSize = 11.sp,
                        fontFamily = SoraFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (dx < 0f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Queue last",
                        color = Ink.Lavender,
                        fontSize = 11.sp,
                        fontFamily = SoraFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Glyph(G.Queue, Ink.Lavender, 15.dp, 2.1.dp)
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(dx.roundToInt(), 0) }
                .draggable(
                    state = rememberDraggableState { delta ->
                        dx = (dx + delta).coerceIn(-limit, limit)
                    },
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        when {
                            dx >= threshold && onPlayNext != null -> {
                                Haptics.tick()
                                onPlayNext.invoke()
                            }
                            dx <= -threshold && onQueue != null -> {
                                Haptics.tick()
                                onQueue.invoke()
                            }
                        }
                        settle()
                    },
                ),
        ) { content() }
    }
}

// ------------------------------------------------------------------ contrast

/**
 * The accent, lifted until it is readable on [background].
 *
 * Covers hand us every colour there is, including ones that vanish against the
 * player's own backdrop. Rather than hope, the tint is measured and mixed toward
 * black or white until it clears 4.5:1 — the icon stays the cover's colour when
 * that colour works, and becomes legible when it does not.
 */
@Composable
fun readableOn(tint: Color, background: Color): Color {
    val safe = Contrast.readable(tint.toArgb(), background.toArgb())
    return Color(safe)
}

/** Black or white, whichever reads on [background]. For text on a filled button. */
@Composable
fun inkOn(background: Color): Color = Color(Contrast.bestInk(background.toArgb()))

/** A scrim heavy enough that white text clears AA on [base]. 0 means none needed. */
fun scrimFor(base: Color, ink: Color = Color.White, target: Double = Contrast.AA): Float =
    Contrast.scrimAlpha(base.toArgb(), ink.toArgb(), target)
