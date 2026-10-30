package app.bunmusic.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SerifFamily
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Collection
import app.bunmusic.data.Track
import app.bunmusic.player.PlayerState
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(radius))
    ) { content() }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = Ink.Text,
            fontSize = 17.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.SemiBold,
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
        lineHeight = (size * 1.06f).sp,
    )
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
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

@Composable
fun CircleCard(collection: Collection, onClick: () -> Unit, width: Dp = 124.dp) {
    Column(Modifier.width(width).clickable { onClick() }) {
        ArtImage(
            url = collection.artUrl,
            asset = collection.artAsset,
            modifier = Modifier.size(width),
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
fun SquareCard(collection: Collection, onClick: () -> Unit, width: Dp = 152.dp) {
    Column(Modifier.width(width).clickable { onClick() }) {
        ArtImage(
            url = collection.artUrl,
            asset = collection.artAsset,
            modifier = Modifier.size(width),
            shape = RoundedCornerShape(20.dp),
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
                if (track.isLocal || downloaded) {
                    Text(
                        if (track.isLocal) "OFFLINE" else "SAVED",
                        color = Ink.Mint,
                        fontSize = 8.sp,
                        fontFamily = SoraFamily,
                        fontWeight = FontWeight.SemiBold,
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
                Modifier.size(34.dp).clip(CircleShape).clickable { onLike() },
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
                Modifier.size(32.dp).clip(CircleShape).clickable { onMore() },
                contentAlignment = Alignment.Center,
            ) { Glyph(G.More, Ink.Faint, 16.dp) }
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

// -------------------------------------------------------------------- inputs

@Composable
fun Chip(
    text: String,
    selected: Boolean = false,
    leading: G? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) Ink.Lime else Ink.Card)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Glyph(leading, if (selected) Ink.OnAccent else Ink.Muted, 13.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            color = if (selected) Ink.OnAccent else Ink.Muted,
            fontSize = 12.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.Medium,
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
    Row(
        Modifier
            .clip(CircleShape)
            .background(color)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 11.dp),
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
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Glyph(glyph, Ink.Text, 14.dp)
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = Ink.Text, fontSize = 12.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
    }
}

// --------------------------------------------------------------- empty states

@Composable
fun EmptyState(glyph: G, title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(58.dp).clip(CircleShape).background(Ink.Card),
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
        start = androidx.compose.ui.geometry.Offset(x, 0f),
        end = androidx.compose.ui.geometry.Offset(x + 420f, 260f),
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
    val frac = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(frac, tween(400), label = "mini")

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF211C2C), Color(0xFF191521))))
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
                    color = Ink.Text,
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
                Modifier.size(36.dp).clip(CircleShape).clickable { onToggle() },
                contentAlignment = Alignment.Center,
            ) {
                Glyph(if (state.isPlaying) G.Pause else G.Play, Ink.Text, 18.dp)
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable { onNext() },
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

@Composable
fun BackBar(title: String, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) { Glyph(G.Back, Ink.Text, 20.dp) }
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
