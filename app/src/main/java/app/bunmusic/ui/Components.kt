package app.bunmusic.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.BunSkin
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Haptics
import app.bunmusic.core.Icons
import app.bunmusic.core.Ink
import app.bunmusic.core.LocalBunWindow
import app.bunmusic.core.SerifFamily
import app.bunmusic.core.Skin
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Collection
import app.bunmusic.data.Cache
import app.bunmusic.data.Track
import app.bunmusic.player.PlayerState
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

// ------------------------------------------------------------------ helpers

fun fmtTime(ms: Long): String {
    if (ms <= 0) return "--:--"
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "$m:" + s.toString().padStart(2, '0')
}

fun fmtDuration(ms: Long): String {
    if (ms <= 0) return "0 min"
    val minutes = ms / 60_000
    return if (minutes < 60) "$minutes min" else "${minutes / 60} hr ${minutes % 60} min"
}

@Composable
fun rememberAssetBitmap(path: String?): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(path).use { BitmapFactory.decodeStream(it).asImageBitmap() }
            }.getOrNull()
        }
    }
    return bmp
}

@Composable
fun ArtImage(
    url: String?,
    asset: String?,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
) {
    Box(modifier.clip(shape).background(Ink.Card)) {
        val local = rememberAssetBitmap(asset)
        when {
            local != null -> Image(
                bitmap = local,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            url != null -> AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            else -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Ink.Card, Ink.Line))),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(G.Disc, Ink.Faint, 22.dp)
            }
        }
    }
}

/**
 * The bun's launcher icon, drawn from its two adaptive layers.
 *
 * Do not replace this with `painterResource(R.mipmap.ic_launcher_*)`: on API 26+
 * that resolves to an AdaptiveIconDrawable, which Compose cannot paint. Loading
 * it from the Settings row was exactly what crashed 1.0.7 the moment the screen
 * opened. Composing the layers by hand works on every API level and still shows
 * the same artwork the home screen wears.
 */
@Composable
fun BunIcon(
    skin: BunSkin,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
) {
    Box(modifier.clip(shape).background(Ink.Card)) {
        Image(
            painter = painterResource(Icons.backgroundOf(skin)),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Image(
            painter = painterResource(Icons.layerOf(skin)),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

// ------------------------------------------------------------------ surfaces

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = Ink.Card,
    radius: Dp = 22.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(color)
            .border(1.dp, Ink.Hairline, RoundedCornerShape(radius))
    ) { content() }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val gutter = LocalBunWindow.current.gutter
    Row(
        Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = 26.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = Ink.Text,
            fontSize = if (Skin.light) 19.sp else 17.sp,
            fontFamily = if (Skin.light) SerifFamily else SoraFamily,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = if (Skin.light) (-0.3).sp else 0.sp,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                action,
                color = Ink.Muted,
                fontSize = 12.sp,
                fontFamily = SoraFamily,
                modifier = Modifier.clickable(enabled = onAction != null) { onAction?.invoke() },
            )
        }
    }
}

@Composable
fun Display(text: String, size: Int = 34, color: Color = Ink.Text) {
    Text(
        text,
        color = color,
        fontSize = size.sp,
        fontFamily = SerifFamily,
        fontWeight = if (Skin.light) FontWeight.Bold else FontWeight.Normal,
        letterSpacing = if (Skin.light) (-0.8).sp else 0.sp,
        lineHeight = (size * 1.06f).sp,
    )
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    val gutter = LocalBunWindow.current.gutter
    Row(
        Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Display(title, 32)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily)
            }
        }
        trailing?.invoke()
    }
}

// -------------------------------------------------------------------- pieces

/**
 * A carousel tile. The size is the phone size unless the caller overrides it —
 * on a tablet the shared [BunWindow.tile] grows it, because a 124 dp circle on a
 * 1000 dp page reads as a favicon.
 */
@Composable
fun CircleCard(collection: Collection, onClick: () -> Unit, width: Dp? = null) {
    val w = width ?: LocalBunWindow.current.tile(124.dp)
    Column(Modifier.width(w).clickable { onClick() }) {
        ArtImage(
            url = collection.artUrl,
            asset = collection.artAsset,
            modifier = Modifier.size(w),
            shape = CircleShape,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            collection.title,
            color = Ink.Text,
            fontSize = 13.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (collection.subtitle.isNotBlank()) {
            Text(
                collection.subtitle,
                color = Ink.Faint,
                fontSize = 11.sp,
                fontFamily = SoraFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SquareCard(collection: Collection, onClick: () -> Unit, width: Dp? = null) {
    val w = width ?: LocalBunWindow.current.tile(152.dp)
    Column(Modifier.width(w).clickable { onClick() }) {
        ArtImage(
            url = collection.artUrl,
            asset = collection.artAsset,
            modifier = Modifier.size(w),
            shape = RoundedCornerShape(if (Skin.light) 18.dp else 20.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            collection.title,
            color = Ink.Text,
            fontSize = 13.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (collection.subtitle.isNotBlank()) {
            Text(
                collection.subtitle,
                color = Ink.Faint,
                fontSize = 11.sp,
                fontFamily = SoraFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A track in a list. The two buns disagree about what a row *is*: Midnight is
 * artwork-led, Charcoal is typographic — number, title, artist · time, and no
 * thumbnail at all, exactly as the reference layout does it.
 */
@Composable
fun TrackRow(
    track: Track,
    index: Int = -1,
    playing: Boolean = false,
    isCurrent: Boolean = false,
    liked: Boolean = false,
    downloaded: Boolean = false,
    onPlay: () -> Unit,
    onLike: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    if (Ink.isLight) {
        CharcoalTrackRow(
            track, index, playing, isCurrent, liked, downloaded, onPlay, onLike, onMore,
        )
    } else {
        MidnightTrackRow(
            track, index, playing, isCurrent, liked, downloaded, onPlay, onLike, onMore,
        )
    }
}

@Composable
private fun MidnightTrackRow(
    track: Track,
    index: Int,
    playing: Boolean,
    isCurrent: Boolean,
    liked: Boolean,
    downloaded: Boolean,
    onPlay: () -> Unit,
    onLike: (() -> Unit)?,
    onMore: (() -> Unit)?,
) {
    val cached = Cache.hasOffline(track.videoId)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onPlay() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index >= 0) {
            Text(
                "$index",
                color = if (isCurrent) Ink.Lime else Ink.Faint,
                fontSize = 11.sp,
                fontFamily = SoraFamily,
                modifier = Modifier.width(18.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Box(contentAlignment = Alignment.Center) {
            ArtImage(
                url = track.artUrl,
                asset = track.artAsset,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(13.dp),
            )
            if (isCurrent && playing) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(Ink.Void.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) { EqualizerBars(Ink.Lime) }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (isCurrent) Ink.Lime else Ink.Text,
                fontSize = 14.sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isLocal || downloaded || cached) {
                    Text(
                        when {
                            track.isLocal -> "OFFLINE"
                            downloaded -> "SAVED"
                            else -> "CACHED"
                        },
                        color = Ink.Mint,
                        fontSize = 10.sp,
                        fontFamily = SoraFamily,
                        fontWeight = FontWeight.SemiBold,
                        // ALL CAPS always carries positive tracking; without it the
                        // badge reads as a cramped smear at this size.
                        letterSpacing = 0.7.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Ink.Mint.copy(alpha = 0.14f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    track.artist,
                    color = Ink.Muted,
                    fontSize = 12.sp,
                    fontFamily = SoraFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (track.durationMs > 0) {
            Text(fmtTime(track.durationMs), color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily)
        }
        if (onLike != null) {
            Box(
                Modifier.tapTarget().size(34.dp).clip(CircleShape).clickable { onLike() },
                contentAlignment = Alignment.Center,
            ) {
                Glyph(
                    if (liked) G.HeartFill else G.Heart,
                    if (liked) Ink.Rose else Ink.Faint,
                    16.dp,
                )
            }
        }
        if (onMore != null) {
            Box(
                Modifier.tapTarget().size(32.dp).clip(CircleShape).clickable { onMore() },
                contentAlignment = Alignment.Center,
            ) { Glyph(G.More, Ink.Faint, 16.dp) }
        }
    }
}

@Composable
private fun CharcoalTrackRow(
    track: Track,
    index: Int,
    playing: Boolean,
    isCurrent: Boolean,
    liked: Boolean,
    downloaded: Boolean,
    onPlay: () -> Unit,
    onLike: (() -> Unit)?,
    onMore: (() -> Unit)?,
) {
    val cached = Cache.hasOffline(track.videoId)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) Ink.Field else Color.Transparent)
            .clickable { onPlay() }
            .padding(start = 10.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.CenterStart) {
            if (isCurrent) MiniWave(Ink.Text, playing) else Text(
                if (index >= 0) index.toString().padStart(2, '0') else "•",
                color = Ink.Faint,
                fontSize = 11.5.sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = Ink.Text,
                fontSize = 15.sp,
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.25).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(track.artist.ifBlank { "Unknown artist" })
                    if (track.durationMs > 0) append("  ·  ${fmtTime(track.durationMs)}")
                    if (track.isLocal || downloaded) append("  ·  offline") else if (cached) append("  ·  cached")
                },
                color = Ink.Muted,
                fontSize = 12.sp,
                fontFamily = SoraFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onLike != null) {
            Box(
                Modifier.tapTarget().size(30.dp).clip(CircleShape).clickable { onLike() },
                contentAlignment = Alignment.Center,
            ) {
                Glyph(if (liked) G.HeartFill else G.Heart, if (liked) Ink.Rose else Ink.Faint, 15.dp)
            }
        }
        if (onMore != null) {
            Box(
                Modifier.tapTarget().size(30.dp).clip(CircleShape).clickable { onMore() },
                contentAlignment = Alignment.Center,
            ) { Glyph(G.More, Ink.Faint, 15.dp) }
        }
    }
}

@Composable
fun EqualizerBars(color: Color, barHeight: Dp = 18.dp) {
    val t = rememberInfiniteTransition(label = "eq")
    val heights = (0..2).map { i ->
        val v by t.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(420 + i * 130),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "h$i",
        )
        v
    }
    Row(
        Modifier.height(barHeight),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEach { h ->
            Box(
                Modifier
                    .width(2.5.dp)
                    .height(barHeight * h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

/** The little four-bar mark that stands in for "this one is playing". */
@Composable
fun MiniWave(color: Color, animate: Boolean = true, height: Dp = 14.dp) {
    val t = rememberInfiniteTransition(label = "miniwave")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(Modifier.size(width = 15.dp, height = height)) {
        val bars = 4
        val gap = size.width / (bars * 2f - 1f)
        repeat(bars) { i ->
            val wobble = if (animate) abs(sin((phase * 2 * Math.PI + i * 1.1).toFloat())) else 0.5f
            val h = size.height * (0.35f + 0.62f * wobble)
            drawRect(
                color = color,
                topLeft = Offset(i * gap * 2f, size.height - h),
                size = Size(gap, h),
            )
        }
    }
}

// -------------------------------------------------------------------- inputs

@Composable
fun Chip(
    text: String,
    selected: Boolean = false,
    leading: G? = null,
    onClick: () -> Unit,
) {
    val light = Ink.isLight
    Row(
        Modifier
            .clip(RoundedCornerShape(if (light) 10.dp else 11.dp))
            .background(
                when {
                    // A picked chip wears the bun's selected fill: lime on
                    // Midnight, ink on Charcoal, electric blue on Cloud.
                    selected -> Ink.NavActiveBg
                    light -> Ink.Field
                    else -> Ink.Card
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Glyph(leading, if (selected) Ink.NavActiveInk else Ink.Muted, 13.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            color = if (selected) Ink.NavActiveInk else Ink.Muted,
            fontSize = 12.sp,
            fontFamily = SoraFamily,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
fun IconBtn(glyph: G, tint: Color = Ink.Text, size: Dp = 40.dp, glyphSize: Dp = 18.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, tint, glyphSize) }
}

@Composable
fun FilledBtn(
    text: String,
    glyph: G? = null,
    color: Color = Ink.Lime,
    contentColor: Color = Ink.OnAccent,
    onClick: () -> Unit,
) {
    val shape = if (Ink.isLight) RoundedCornerShape(13.dp) else CircleShape
    Row(
        Modifier
            .clip(shape)
            .background(color)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = if (Ink.isLight) 13.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Glyph(glyph, contentColor, 14.dp)
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = contentColor, fontSize = 12.5.sp, fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GhostBtn(text: String, glyph: G? = null, onClick: () -> Unit) {
    val light = Ink.isLight
    val shape = if (light) RoundedCornerShape(13.dp) else CircleShape
    Row(
        Modifier
            .clip(shape)
            .background(if (light) Ink.Field else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (light) Ink.Hairline else Color.White.copy(alpha = 0.08f), shape)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = if (light) 13.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Glyph(glyph, Ink.Text, 14.dp)
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = Ink.Text, fontSize = 12.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
    }
}

/**
 * Material's 48 dp minimum touch target, for icon buttons drawn smaller.
 *
 * Every icon button here draws at 30–40 dp, which is what the reference layouts
 * look like — but the *hit* area was 30–40 dp too, and a 30 dp target fails
 * WCAG 2.5.8 and is genuinely hard to hit one-handed. The glyph keeps its size;
 * the box around it reserves 48 dp for the finger.
 *
 * Must be the OUTERMOST modifier in the chain. `Modifier.size(30.dp)` fixes the
 * constraints, so a `tapTarget()` applied after it has nothing left to expand.
 */
fun Modifier.tapTarget(): Modifier = this.minimumInteractiveComponentSize()

// --------------------------------------------------------------- empty states

@Composable
fun EmptyState(glyph: G, title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(58.dp).clip(CircleShape).background(if (Ink.isLight) Ink.Field else Ink.Card),
            contentAlignment = Alignment.Center,
        ) { Glyph(glyph, Ink.Faint, 24.dp) }
        Spacer(Modifier.height(14.dp))
        Text(title, color = Ink.Text, fontSize = 15.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Text(
            body,
            color = Ink.Muted,
            fontSize = 12.sp,
            fontFamily = SoraFamily,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 18.sp,
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            FilledBtn(action, G.Plus, onClick = onAction)
        }
    }
}

// ----------------------------------------------------------------- shimmer

@Composable
fun shimmerBrush(): Brush {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -420f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Restart),
        label = "x",
    )
    return Brush.linearGradient(
        colors = listOf(Ink.Card, Ink.Raised, Ink.Card),
        start = Offset(x, 0f),
        end = Offset(x + 420f, 260f),
    )
}

@Composable
fun ShimmerBox(modifier: Modifier, shape: Shape = RoundedCornerShape(14.dp)) {
    Box(modifier.clip(shape).background(shimmerBrush()))
}

@Composable
fun ShimmerRow() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerBox(Modifier.size(48.dp), RoundedCornerShape(13.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            ShimmerBox(Modifier.fillMaxWidth(0.62f).height(12.dp), RoundedCornerShape(6.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.38f).height(10.dp), RoundedCornerShape(5.dp))
        }
    }
}

// ---------------------------------------------------------------- miniplayer

@Composable
fun MiniPlayer(
    state: PlayerState,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.track ?: return
    if (Ink.isLight) {
        CharcoalMiniPlayer(state, track, onOpen, onToggle, modifier)
    } else {
        MidnightMiniPlayer(state, track, onOpen, onToggle, onNext, modifier)
    }
}

@Composable
private fun CharcoalMiniPlayer(
    state: PlayerState,
    track: Track,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Ink.MiniBg)
            .clickable { onOpen() }
            .padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(
            url = track.artUrl,
            asset = track.artAsset,
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = Ink.MiniText,
                fontSize = 14.5.sp,
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                if (state.buffering) "Loading…" else track.artist,
                color = Ink.MiniText.copy(alpha = 0.62f),
                fontSize = 11.5.sp,
                fontFamily = SoraFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.tapTarget().size(34.dp).clip(CircleShape).clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) { Glyph(if (state.isPlaying) G.HeartFill else G.Heart, Ink.MiniText.copy(alpha = 0.9f), 18.dp) }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color.White)
                .clickable { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Glyph(
                if (state.buffering && !state.isPlaying) G.Wave else if (state.isPlaying) G.Pause else G.Play,
                Color(0xFF171C26),
                18.dp,
                2.2.dp,
            )
        }
    }
}

@Composable
private fun MidnightMiniPlayer(
    state: PlayerState,
    track: Track,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier,
) {
    val frac = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(frac, tween(400), label = "mini")

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Ink.MiniBg, Ink.Void)))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
            .clickable { onOpen() }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtImage(
                url = track.artUrl,
                asset = track.artAsset,
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = Ink.MiniText,
                    fontSize = 13.sp,
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (state.buffering) "Loading…" else track.artist,
                    color = Ink.Muted,
                    fontSize = 11.sp,
                    fontFamily = SoraFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier.tapTarget().size(36.dp).clip(CircleShape).clickable { onToggle() },
                contentAlignment = Alignment.Center,
            ) {
                Glyph(if (state.isPlaying) G.Pause else G.Play, Ink.Text, 18.dp)
            }
            Box(
                Modifier.tapTarget().size(36.dp).clip(CircleShape).clickable { onNext() },
                contentAlignment = Alignment.Center,
            ) { Glyph(G.Next, Ink.Text, 18.dp) }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(2.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Ink.Lime)
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ------------------------------------------------------------------- seekbar

@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Ink.Lime,
) {
    if (Skin.isCloud) {
        LineSeekBar(positionMs, durationMs, onSeek, modifier, activeColor)
        return
    }
    if (Ink.isLight) {
        WaveSeekBar(positionMs, durationMs, onSeek, modifier, activeColor)
        return
    }
    val value = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) local else value

    Slider(
        value = shown,
        onValueChange = { dragging = true; local = it },
        onValueChangeFinished = {
            dragging = false
            onSeek((local * durationMs).toLong())
        },
        modifier = modifier.fillMaxWidth().height(28.dp),
        colors = SliderDefaults.colors(
            thumbColor = activeColor,
            activeTrackColor = activeColor,
            inactiveTrackColor = Color.White.copy(alpha = 0.12f),
        ),
    )
}

/**
 * Charcoal's seek bar is a waveform, not a line. The bars are a fixed pseudo
 * random field seeded by the track, so the same song always draws the same
 * shape — it reads as the track's own waveform rather than noise.
 */
@Composable
fun WaveSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Ink.Text,
    seed: Int = 7,
) {
    val value = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) local else value
    var widthPx by remember { mutableStateOf(1f) }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        local = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragging = false
                        onSeek((local * durationMs).toLong())
                        Haptics.tick()
                    },
                ) { change, _ ->
                    local = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                }
            }
            // Drag alone meant a tap on the waveform did nothing at all — the
            // line seek bar always supported both, so the two buns disagreed on
            // how the same control behaves.
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    onSeek(((offset.x / size.width.toFloat()).coerceIn(0f, 1f) * durationMs).toLong())
                    Haptics.tick()
                }
            }
    ) {
        widthPx = size.width
        val bars = 56
        val slot = size.width / bars
        val barW = (slot * 0.45f).coerceAtLeast(1.4f)
        val played = shown * size.width
        for (i in 0 until bars) {
            val n = ((i * 37 + seed * 11) % 23) / 22f
            val n2 = ((i * 17 + seed * 5) % 13) / 12f
            val h = size.height * (0.16f + 0.84f * (0.35f * n + 0.65f * n2) * (0.55f + 0.45f * sin(i * 0.55f)))
            val x = i * slot + (slot - barW) / 2f
            val color = if (x + barW / 2f <= played) activeColor else activeColor.copy(alpha = 0.22f)
            drawRect(
                color = color,
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barW, h),
            )
        }
        // playhead
        if (durationMs > 0) {
            drawRect(
                color = activeColor,
                topLeft = Offset((played - 0.75f).coerceIn(0f, size.width - 1.5f), 0f),
                size = Size(1.5f, size.height),
            )
        }
    }
}

/**
 * Cloud's seek bar: a hairline track with a single blue knob, no waveform. It is
 * the one place the blue bun is deliberately plainer than the others — the study
 * it is drawn from uses a line, and a line is easier to hit on a light surface.
 */
@Composable
fun LineSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Ink.Lime,
) {
    val value = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) local else value
    val animated by animateFloatAsState(shown, tween(180), label = "line")

    Canvas(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        local = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragging = false
                        onSeek((local * durationMs).toLong())
                        Haptics.tick()
                    },
                ) { change, _ ->
                    local = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                }
            }
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    val f = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeek((f * durationMs).toLong())
                    Haptics.tick()
                }
            },
    ) {
        val cy = size.height / 2f
        val track = 4.dp.toPx()
        val knob = 7.dp.toPx()
        val x = (animated * size.width).coerceIn(knob, size.width - knob)

        drawRoundRect(
            color = Ink.Line,
            topLeft = Offset(0f, cy - track / 2f),
            size = Size(size.width, track),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(track / 2f),
        )
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(0f, cy - track / 2f),
            size = Size(x.coerceAtLeast(0f), track),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(track / 2f),
        )
        // A white ring keeps the knob readable where the track is pale.
        drawCircle(color = Color.White, radius = knob, center = Offset(x, cy))
        drawCircle(color = activeColor, radius = knob - 2.dp.toPx(), center = Offset(x, cy))
    }
}

@Composable
fun BackBar(title: String, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.tapTarget().size(40.dp).clip(CircleShape).clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) { Glyph(G.Back, Ink.Text, 20.dp, if (Ink.isLight) 2.2.dp else 1.6.dp) }
        Text(
            title,
            color = Ink.Text,
            fontSize = 14.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        trailing?.invoke()
    }
}
