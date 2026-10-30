package app.bunmusic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.BunPalette
import app.bunmusic.core.BunSkin
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Haptics
import app.bunmusic.core.Icons
import app.bunmusic.core.Ink
import app.bunmusic.core.Skin
import app.bunmusic.core.SoraFamily
import kotlin.math.roundToInt

/**
 * Buns — the skin picker.
 *
 * Each card is a real preview drawn in that bun's own colours, not a screenshot:
 * if a bun changes, its preview changes with it. Picking one restarts the app,
 * which is the only honest way to guarantee nothing from the old bun lingers.
 */
@Composable
fun BunsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<BunSkin?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item { BackBar("Buns", onBack) }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Display("Buns", 32)
                Spacer(Modifier.height(6.dp))
                Text(
                    "A bun is the whole surface: colour, type, corners, how a list " +
                        "is drawn, and the icon on your home screen. Switching one " +
                        "restarts BunMusic.",
                    color = Ink.Muted,
                    fontSize = 12.5.sp,
                    fontFamily = SoraFamily,
                    lineHeight = 19.sp,
                )
            }
        }

        items(BunSkin.entries.size) { i ->
            val bun = BunSkin.entries[i]
            BunCard(
                bun = bun,
                active = Skin.current == bun,
                onPick = { if (Skin.current != bun) pending = bun },
            )
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    "More buns land in later versions. A bun has to restyle every " +
                        "screen — palette, typeface, list rhythm — not just tint the " +
                        "buttons, so they take a while to draw properly.",
                    color = Ink.Faint,
                    fontSize = 11.sp,
                    fontFamily = SoraFamily,
                    lineHeight = 17.sp,
                )
            }
        }
    }

    pending?.let { bun ->
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = Ink.Sheet,
            titleContentColor = Ink.Text,
            textContentColor = Ink.Muted,
            title = {
                Text(
                    "Wear ${bun.title}?",
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            },
            text = {
                Text(
                    "BunMusic will close and reopen so the new bun starts clean, " +
                        "and the launcher icon changes with it. Your library, " +
                        "downloads and account stay exactly as they are.",
                    fontFamily = SoraFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Haptics.thud()
                    pending = null
                    Skin.apply(context, bun)
                }) {
                    Text("Apply and restart", color = Ink.Text, fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text("Not now", color = Ink.Muted, fontFamily = SoraFamily)
                }
            },
        )
    }
}

@Composable
private fun BunCard(bun: BunSkin, active: Boolean, onPick: () -> Unit) {
    val p = Skin.paletteOf(bun)
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(p.card)
            .border(
                1.5.dp,
                if (active) p.accent else p.hairline,
                RoundedCornerShape(24.dp),
            )
            .clickable { onPick() }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The bun's own launcher icon. Switching buns really does put this on
            // the home screen, so showing it here is not decoration.
            Image(
                painter = painterResource(Icons.launcherOf(bun)),
                contentDescription = null,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    bun.title,
                    color = p.text,
                    fontSize = 16.sp,
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(bun.tagline, color = p.muted, fontSize = 11.5.sp, fontFamily = SoraFamily, lineHeight = 16.sp)
            }
            if (active) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(p.accent)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text("WEARING", color = p.onAccent, fontSize = 8.5.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        BunPreview(p, Modifier.fillMaxWidth().height(188.dp))
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Swatch(p.void, hex(p.void))
            Swatch(p.text, hex(p.text))
            Swatch(p.muted, hex(p.muted))
            Swatch(p.accent, hex(p.accent))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            bun.typeLabel,
            color = p.faint,
            fontSize = 10.sp,
            fontFamily = SoraFamily,
        )
    }
}

private fun hex(c: Color): String = "%06X".format(c.toArgb() and 0xFFFFFF)

@Composable
private fun Swatch(color: Color, hex: String) {
    Column(Modifier.width(52.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .border(1.dp, Color.Gray.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.height(4.dp))
        Text(hex, color = Ink.Faint, fontSize = 8.sp, fontFamily = SoraFamily)
    }
}

/**
 * A miniature of the now-playing screen, painted in the bun's own colours. It is
 * drawn rather than captured so it can never drift from the real thing.
 */
@Composable
private fun BunPreview(p: BunPalette, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(p.base)
            .border(1.dp, p.hairline, RoundedCornerShape(18.dp)),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(p.text.copy(alpha = 0.5f)))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .width(26.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(p.text.copy(alpha = 0.75f))
                )
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(5.dp).clip(CircleShape).background(p.text.copy(alpha = 0.5f)))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(if (p.light) 14.dp else 14.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(p.card, p.muted.copy(alpha = 0.45f))
                            )
                        ),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.72f)
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(p.text)
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.46f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(p.muted.copy(alpha = 0.7f))
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(p.accent)
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .width(34.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(p.field)
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // waveform strip
            Row(
                Modifier.fillMaxWidth().height(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                repeat(22) { i ->
                    val h = (18 * (0.25f + 0.75f * ((i * 37 % 11) / 10f))).roundToInt().dp
                    Box(
                        Modifier
                            .weight(1f)
                            .height(h)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (i < 13) p.accent else p.accent.copy(alpha = 0.22f))
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            // mini player + nav
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.miniBg)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(p.muted.copy(alpha = 0.6f))
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.5f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.miniText))
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.fillMaxWidth(0.3f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(p.miniText.copy(alpha = 0.5f)))
                }
                Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(Color.White))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .size(if (i == 2) 14.dp else 11.dp)
                            .clip(if (i == 2) RoundedCornerShape(4.dp) else CircleShape)
                            .background(if (i == 2) p.navActiveBg else p.faint.copy(alpha = 0.45f))
                    )
                }
            }
        }
    }
}
