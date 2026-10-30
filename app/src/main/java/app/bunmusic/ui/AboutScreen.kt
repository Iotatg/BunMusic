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
                    "1.0.10 · build 11 · ${Skin.current.title} bun",
                    color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily,
                )
            }
        }

        item { GroupTitle("What's new") }
        item {
            AboutCard {
                Change("Captions you can actually read", "The caption grey sat at 2.0–2.9:1 in all four buns — under the 4.5:1 floor. Every bun's three text tones were recomputed and now pass, on the page and on a card")
                Change("No pure-white page", "Charcoal painted #FFFFFF behind #FFFFFF cards. The page is a soft grey now, so a card has an edge")
                Change("Blue and rose you can read on", "Cloud's blue and Pink's rose were 4.2:1 as text. Both are one step deeper; the fills and hero gradients keep the brighter originals")
                Change("Fingers, not needles", "Row actions were 30–40 dp tap targets. The drawn size is unchanged; the hit area is 48 dp")
                Change("Tap the waveform", "Charcoal and Pink could only scrub by dragging. A tap now seeks, like Cloud's line")
                Change("Explore admits failure", "A failed fetch used to leave a near-empty screen. It says what happened and offers a retry")
                Change("One weight for each job", "Medium was mapped onto the semibold file, so Medium and SemiBold were the same weight. Regular / SemiBold / Bold now")
                Change("Small type, lifted", "Nothing renders below 10 sp, and every all-caps label carries real tracking")
                Change("Settings no longer crashes", "1.0.7 handed the adaptive-icon XML to Compose, which cannot paint it. The Buns row now composes the icon's two layers, so Settings opens on every launcher and every Android version")
                Change("A fourth bun: Pink Bun", "Blush paper, rose ink, Sora throughout — the bun with the music note, from the pink study")
                Change("Rose where it counts", "Hero card, play disc, nav pill, chips and the home-screen widget all follow the bun's rose")
                Change("Same bones, again", "Every screen, row, setting, animation and shortcut sits exactly where it did; only the weather changes")
                Change("A third bun: Cloud Blue", "Chalk white and electric blue, drawn from a study of a blue-on-white music app")
                Change("The bun in headphones", "Cloud's icon is the artwork itself — blue disc, closed eyes, a transport pill under it")
                Change("Blue where it counts", "Hero card, play disc, nav pill, chips — the same places, in blue")
                Change("A line seek bar", "Cloud's player uses a hairline track and a knob; Charcoal keeps its waveform")
                Change("Same bones", "Every screen, row, setting and shortcut sits exactly where it did")
                Change("An icon per bun", "Midnight wears the violet disc, Charcoal the black one, Cloud the blue bun, Pink the blush tile — and the home screen swaps with them")
                Change("A real icon", "The bun in headphones — adaptive, themed, and on the status bar too")
                Change("Caching actually works now", "The cache is keyed on the song, not the URL — YouTube signs a new URL every time, so the old key never matched")
                Change("Plays with the internet off", "A finished song is read straight off disk; the signed URL is never opened again")
                Change("Whole songs, not half", "Each track is pulled to the end after it starts, and the next two are warmed up behind it")
                Change("No more stuttering", "Bigger buffer, earlier start, quick resume after a stall, and network held awake while playing")
                Change("CACHED badge", "Every row tells you whether it will play on a plane")
                Change("Home-screen widget", "A now-playing bar with artwork, progress, and prev/play/next")
                Change("High-resolution artwork", "Covers are asked for at 1080 px instead of the 60 px the API hands out")
                Change("Sleep timer fades", "The last ten seconds ease out instead of cutting mid-note")
                Change("Smarter shuffle", "Never two songs by the same artist back to back")
                Change("Skip silent gaps", "Jumps over dead air inside a track")
            }
        }

        item { GroupTitle("Build") }
        item {
            AboutCard {
                Fact("Version", "1.0.10 (11)")
                Fact("Package", "app.bunmusic")
                Fact("Min SDK", "26 · Android 8.0")
                Fact("Target SDK", "37")
                Fact("Device", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
                Fact("Bun", Skin.current.title)
                Fact(
                    "Launcher icon",
                    when (Skin.current) {
                        app.bunmusic.core.BunSkin.MIDNIGHT -> "Midnight · violet disc on ink"
                        app.bunmusic.core.BunSkin.CHARCOAL -> "Charcoal · black disc on paper"
                        app.bunmusic.core.BunSkin.CLOUD -> "Cloud · blue bun on chalk"
                        app.bunmusic.core.BunSkin.PINK -> "Pink · blush tile, bun and note"
                    },
                )
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
        fontSize = 10.sp,
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.9.sp,
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
