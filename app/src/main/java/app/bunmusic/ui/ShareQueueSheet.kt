package app.bunmusic.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.BunEase
import app.bunmusic.core.BunSpring
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Haptics
import app.bunmusic.core.Ink
import app.bunmusic.core.Qr
import app.bunmusic.core.QrCode
import app.bunmusic.core.SerifFamily
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.QueueCode
import app.bunmusic.data.Track

/**
 * A queue, handed to somebody else.
 *
 * There is no server behind this and no account to log into, so the queue has to
 * travel inside the picture. That is the whole design: a code you can point a
 * camera at, and a field you can paste the same code into when the camera is the
 * wrong tool.
 *
 * The receiving side never needs this app to *read* the code — any camera app
 * will do — which is what makes the no-server version honest rather than a
 * compromise. BunMusic is only needed to turn the text back into a queue.
 */
@Composable
fun ShareQueueSheet(
    tracks: List<Track>,
    onClose: () -> Unit,
    onPlay: (List<Track>) -> Unit,
    onEnqueue: (List<Track>) -> Unit,
    onToast: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val code = remember(tracks) { QueueCode.encode(tracks) }
    val sent = remember(code) { QueueCode.count(code) }
    val symbol = remember(code) {
        if (code.isEmpty()) null else runCatching { Qr.encode(code, Qr.Ecc.M) }.getOrNull()
    }

    var pasted by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Void.copy(alpha = 0.98f))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Share queue", color = Ink.Text, fontSize = 18.sp, fontFamily = SerifFamily)
                Text(
                    if (symbol == null) "nothing to send" else buildString {
                        append("$sent tracks")
                        val shareable = tracks.count { it.videoId.isNotBlank() && !it.isLocal }
                        if (sent < shareable) append(" of $shareable")
                        append(" · ${code.length} characters · no server")
                    },
                    color = Ink.Faint,
                    fontSize = 11.sp,
                    fontFamily = SoraFamily,
                )
            }
            IconBtn(G.Close, Ink.Text, 38.dp, 16.dp, onClick = onClose)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (symbol == null) {
                EmptyCard(
                    "Nothing here can travel",
                    "A queue code carries a video id per track. Local files and empty rows have none, " +
                        "so they are left out rather than sent as broken entries.",
                )
            } else {
                QrPanel(symbol, code)

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WideButton("Copy code", G.Copy, Modifier.weight(1f)) {
                        copy(ctx, code)
                        onToast("Queue code copied")
                    }
                    WideButton("Send", G.Share, Modifier.weight(1f)) {
                        send(ctx, code, sent)
                    }
                }
                val shareable = tracks.count { it.videoId.isNotBlank() && !it.isLocal }
                if (sent < shareable) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Long names do not compress well. Only the first $sent tracks fit in a " +
                            "QR code a camera can read, so the rest were left out of this code.",
                        color = Ink.Muted,
                        fontSize = 11.sp,
                        fontFamily = SoraFamily,
                        lineHeight = 16.sp,
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.height(1.dp).weight(1f).background(Ink.Hairline))
                Text(
                    "  or  ",
                    color = Ink.Faint,
                    fontSize = 10.sp,
                    fontFamily = SoraFamily,
                    letterSpacing = 1.sp,
                )
                Box(Modifier.height(1.dp).weight(1f).background(Ink.Hairline))
            }
            Spacer(Modifier.height(22.dp))

            Text(
                "OPEN A QUEUE",
                color = Ink.Faint,
                fontSize = 10.sp,
                fontFamily = SoraFamily,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Paste a code somebody sent you. It is long, and that is the price of having no server.",
                color = Ink.Muted,
                fontSize = 12.sp,
                fontFamily = SoraFamily,
            )
            Spacer(Modifier.height(12.dp))

            val fieldShape = RoundedCornerShape(if (Ink.isLight) 14.dp else 18.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(fieldShape)
                    .background(Ink.Field)
                    .border(1.dp, if (problem != null) Ink.Rose.copy(alpha = 0.6f) else Ink.Hairline, fieldShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (pasted.isEmpty()) {
                    Text(
                        "BQ1…",
                        color = Ink.Faint,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                BasicTextField(
                    value = pasted,
                    onValueChange = {
                        pasted = it
                        problem = null
                    },
                    textStyle = TextStyle(
                        color = Ink.Text,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    cursorBrush = SolidColor(Ink.Lime),
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (problem != null) {
                Spacer(Modifier.height(8.dp))
                Text(problem!!, color = Ink.Rose, fontSize = 11.5.sp, fontFamily = SoraFamily)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WideButton("Play it", G.Play, Modifier.weight(1f), enabled = pasted.isNotBlank()) {
                    val list = read(pasted, onProblem = { problem = it })
                    if (list != null) {
                        Haptics.tick()
                        onPlay(list)
                        onToast("Playing ${list.size} tracks")
                        onClose()
                    }
                }
                WideButton("Add to queue", G.Plus, Modifier.weight(1f), enabled = pasted.isNotBlank()) {
                    val list = read(pasted, onProblem = { problem = it })
                    if (list != null) {
                        Haptics.tick()
                        onEnqueue(list)
                        onToast("${list.size} tracks added")
                        onClose()
                    }
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

// ------------------------------------------------------------------ pieces

/**
 * The code itself, on white, with the four-module quiet zone the standard asks
 * for. Modules are snapped to whole pixels and drawn as horizontal runs — at
 * version 20 that is a couple of thousand rectangles saved per frame.
 */
@Composable
private fun QrPanel(symbol: QrCode, code: String) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val grow by animateFloatAsState(
        targetValue = if (shown) 1f else 0.94f,
        animationSpec = BunSpring.snappy(),
        label = "qr-grow",
    )
    val fade by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(320, easing = BunEase.Standard),
        label = "qr-fade",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .scale(grow)
                .alpha(fade)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White)
                .padding(18.dp),
        ) {
            Canvas(Modifier.fillMaxWidth().height(280.dp)) {
                val n = symbol.size + 8
                val m = (size.minDimension / n).toInt().coerceAtLeast(1).toFloat()
                val side = m * n
                val left = (size.width - side) / 2f
                val top = (size.height - side) / 2f
                val ink = Color(0xFF07070A)

                for (y in 0 until symbol.size) {
                    var x = 0
                    while (x < symbol.size) {
                        if (!symbol.at(x, y)) {
                            x++
                            continue
                        }
                        var run = 1
                        while (x + run < symbol.size && symbol.at(x + run, y)) run++
                        drawRect(
                            color = ink,
                            topLeft = Offset(left + (x + 4) * m, top + (y + 4) * m),
                            size = Size(run * m, m),
                        )
                        x += run
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Point any camera at it — no BunMusic needed to read it.",
            color = Ink.Faint,
            fontSize = 11.sp,
            fontFamily = SoraFamily,
        )
    }
}

@Composable
private fun EmptyCard(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.Card)
            .border(1.dp, Ink.Hairline, RoundedCornerShape(18.dp))
            .padding(20.dp),
    ) {
        Glyph(G.Queue, Ink.Faint, 22.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, color = Ink.Text, fontSize = 15.sp, fontFamily = SerifFamily)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily, lineHeight = 18.sp)
    }
}

@Composable
private fun WideButton(
    label: String,
    glyph: G,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = if (Ink.isLight) RoundedCornerShape(14.dp) else CircleShape
    Row(
        modifier
            .clip(shape)
            .background(if (enabled) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.10f else 0.04f), shape)
            .tapable(enabled) {
                Haptics.tick()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(glyph, if (enabled) Ink.Text else Ink.Faint, 15.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (enabled) Ink.Text else Ink.Faint,
            fontSize = 12.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ------------------------------------------------------------------ wiring

private fun read(code: String, onProblem: (String) -> Unit): List<Track>? {
    if (!QueueCode.looks(code)) {
        onProblem("That does not start with BQ1 — it is not a queue code.")
        return null
    }
    val items = QueueCode.decode(code)
    if (items == null) {
        onProblem("The code is damaged or was cut short. Ask for it again.")
        return null
    }
    return QueueCode.toTracks(items)
}

private fun copy(ctx: Context, code: String) {
    val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clip.setPrimaryClip(android.content.ClipData.newPlainText("BunMusic queue", code))
}

private fun send(ctx: Context, code: String, count: Int) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "A queue of $count tracks")
        putExtra(Intent.EXTRA_TEXT, code)
    }
    ctx.startActivity(Intent.createChooser(intent, "Send queue").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
