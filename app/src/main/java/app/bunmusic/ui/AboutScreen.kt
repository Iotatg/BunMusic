package app.bunmusic.ui

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.Skin
import app.bunmusic.core.SoraFamily

/** Version, build, the device it is running on, and what changed. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 30.dp),
    ) {
        item { BackBar("About", onBack) }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Display("BunMusic", 32)
                Spacer(Modifier.height(4.dp))
                Text(
                    "1.0.3 · build 4 · ${Skin.current.title} bun",
                    color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily,
                )
            }
        }

        item { GroupTitle("What's new") }
        item {
            AboutCard {
                Change("Buns", "A whole-app skin system, switched from Settings and applied by restart")
                Change("Charcoal", "A second bun: paper white, charcoal ink, Inter, waveform seek bar")
                Change("Hotlist", "A ranked chart tab, straight from YouTube's top songs")
                Change("Queue editing", "Drag to reorder, and save the queue as a playlist")
                Change("Lyrics offset", "Nudge synced lyrics ±2 s when a track is off")
                Change("Haptics", "A tick on like and play, switchable off")
                Change("About", "This page — version, build, device")
            }
        }

        item { GroupTitle("Build") }
        item {
            AboutCard {
                Fact("Version", "1.0.3 (4)")
                Fact("Package", "app.bunmusic")
                Fact("Min SDK", "26 · Android 8.0")
                Fact("Target SDK", "37")
                Fact("Device", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
                Fact("Bun", Skin.current.title)
            }
        }

        item { GroupTitle("Credits") }
        item {
            AboutCard {
                Fact("Made by", "Iota Coder")
                Fact("Contact", "iota14934@gmail.com")
                Fact("Source", "github.com/Iotatg/BunMusic")
                Fact("Licence", "Apache 2.0")
                Fact("Streams", "YouTube Music, via a hand-written client")
                Fact("Artists' rights", "Streams are never downloaded for redistribution")
            }
        }

        item {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "BunMusic is an independent player. It is not affiliated with, " +
                        "endorsed by, or connected to YouTube or Google. Bundled demo " +
                        "tracks are original recordings made for this app.",
                    color = Ink.Faint,
                    fontSize = 11.sp,
                    fontFamily = SoraFamily,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text.uppercase(),
        color = Ink.Faint,
        fontSize = 9.5.sp,
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 24.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun AboutCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Ink.Card)
            .padding(vertical = 6.dp),
    ) { content() }
}

@Composable
private fun Change(title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.padding(top = 4.dp).size(5.dp).clip(CircleShape).background(Ink.Lime)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = Ink.Text, fontSize = 13.sp, fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold)
            Text(body, color = Ink.Muted, fontSize = 11.5.sp, fontFamily = SoraFamily, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ink.Muted, fontSize = 12.5.sp, fontFamily = SoraFamily, modifier = Modifier.width(96.dp))
        Text(value, color = Ink.Text, fontSize = 12.5.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
    }
}
