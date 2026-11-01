package app.bunmusic.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.BunTheme
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SoraFamily
import app.bunmusic.ui.Chip

/**
 * The sheet the launcher opens when a widget is dropped, and again when it is
 * long-pressed and reconfigured.
 *
 * The preview is drawn with Compose from the same [WidgetLook] the RemoteViews
 * will be built from, so what is chosen here is what appears on the home screen
 * — the two renderers share the palette, the radius and the element flags, not
 * just a screenshot.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // If the user backs out, the launcher removes the placeholder widget.
        setResult(RESULT_CANCELED)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val initial = WidgetStore.look(this, widgetId)
        setContent {
            BunTheme {
                ConfigSheet(
                    initial = initial,
                    onCancel = { finish() },
                    onSave = { look ->
                        WidgetStore.save(this, widgetId, look)
                        Widgets.pushAll(this, force = true)
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                        )
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun ConfigSheet(
    initial: WidgetLook,
    onCancel: () -> Unit,
    onSave: (WidgetLook) -> Unit,
) {
    var look by remember { mutableStateOf(initial) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onCancel),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Ink.Sheet)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Widget",
                        color = Ink.Text,
                        fontSize = 20.sp,
                        fontFamily = SoraFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Changes apply the moment you save",
                        color = Ink.Faint,
                        fontSize = 11.5.sp,
                        fontFamily = SoraFamily,
                    )
                }
                Text(
                    "Cancel",
                    color = Ink.Muted,
                    fontSize = 13.sp,
                    fontFamily = SoraFamily,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onCancel() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            WidgetPreview(look)

            Spacer(Modifier.height(18.dp))

            Label("Style")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetSkin.entries.take(3).forEach { s ->
                    Chip(s.label, selected = look.skin == s) { look = look.copy(skin = s) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetSkin.entries.drop(3).forEach { s ->
                    Chip(s.label, selected = look.skin == s) { look = look.copy(skin = s) }
                }
            }

            Spacer(Modifier.height(16.dp))

            Label("Corners")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetShape.entries.forEach { s ->
                    Chip(s.label, selected = look.shape == s) { look = look.copy(shape = s) }
                }
            }

            Spacer(Modifier.height(16.dp))

            Label("Show on the card")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Progress", selected = look.progress) { look = look.copy(progress = !look.progress) }
                Chip("Times", selected = look.times) { look = look.copy(times = !look.times) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Like", selected = look.like) { look = look.copy(like = !look.like) }
                Chip("Queue", selected = look.queue) { look = look.copy(queue = !look.queue) }
            }

            Spacer(Modifier.height(22.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ink.Lime)
                    .clickable { onSave(look) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Save widget",
                    color = Ink.OnAccent,
                    fontSize = 14.sp,
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        color = Ink.Faint,
        fontSize = 10.sp,
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.9.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/**
 * A to-scale stand-in for the 4x2 card, drawn from the same palette and radius
 * the RemoteViews uses. Glass sits over a wallpaper-ish gradient because a
 * translucent card on a flat background is not what the home screen looks like.
 */
@Composable
fun WidgetPreview(look: WidgetLook, modifier: Modifier = Modifier) {
    val p = look.palette
    val shape = RoundedCornerShape(look.shape.radiusDp.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF2A2440), Color(0xFF5C3B2E), Color(0xFF1B2A33))
                )
            )
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(shape)
                .background(p.card)
                .border(1.dp, p.hairline, shape)
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(look.shape.artRadiusDp.dp))
                        .background(p.text.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(p.text.copy(alpha = 0.22f)))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "The Last Letter",
                        color = p.text,
                        fontSize = 13.sp,
                        fontFamily = SoraFamily,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Maan Panu",
                        color = p.muted,
                        fontSize = 11.sp,
                        fontFamily = SoraFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (look.progress) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(p.text.copy(alpha = 0.16f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.38f)
                            .fillMaxHeight()
                            .background(p.accent),
                    )
                }
            }
            if (look.times) {
                Spacer(Modifier.height(3.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("0:52", color = p.muted, fontSize = 9.sp, fontFamily = SoraFamily)
                    Spacer(Modifier.weight(1f))
                    Text("2:48", color = p.muted, fontSize = 9.sp, fontFamily = SoraFamily)
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (look.like) {
                    Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                        Glyph(G.Heart, p.muted, 13.dp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                    Glyph(G.Prev, p.muted, 13.dp)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(p.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(G.Play, p.onAccent, 11.dp)
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                    Glyph(G.Next, p.muted, 13.dp)
                }
                Spacer(Modifier.weight(1f))
                if (look.queue) {
                    Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                        Glyph(G.Queue, p.muted, 13.dp)
                    }
                }
            }
        }
    }
}
