package app.bunmusic.ui

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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Downloads
import app.bunmusic.data.Prefs
import app.bunmusic.data.Store

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val settings by Prefs.settings.collectAsState()
    val downloads by Downloads.entries.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 30.dp),
    ) {
        item { BackBar("Settings", onBack) }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Display("Settings", 30)
                Spacer(Modifier.height(4.dp))
                Text(
                    "BunMusic 1.0.1 · Iota Coder",
                    color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily,
                )
            }
        }

        item { GroupLabel("Playback") }
        item {
            SettingsCard {
                ChoiceRow(
                    title = "Audio quality",
                    subtitle = settings.quality.note,
                ) {
                    Prefs.Quality.entries.forEach { q ->
                        Chip(q.label, selected = settings.quality == q) {
                            Prefs.update { it.copy(quality = q) }
                        }
                    }
                }
                Divider()
                ToggleRow("Volume normalisation", "Evens out loud and quiet tracks", settings.normalize) { v ->
                    Prefs.update { it.copy(normalize = v) }
                }
                Divider()
                ToggleRow("Gapless playback", "No silence between tracks", settings.gapless) { v ->
                    Prefs.update { it.copy(gapless = v) }
                }
                Divider()
                ChoiceRow("Crossfade", if (settings.crossfadeSec == 0) "Off" else "${settings.crossfadeSec}s") {
                    listOf(0, 3, 6, 12).forEach { sec ->
                        Chip(if (sec == 0) "Off" else "${sec}s", selected = settings.crossfadeSec == sec) {
                            Prefs.update { it.copy(crossfadeSec = sec) }
                        }
                    }
                }
            }
        }

        item { GroupLabel("Appearance") }
        item {
            SettingsCard {
                ChoiceRow("Player style", settings.playerStyle.label) {
                    Prefs.PlayerStyle.entries.forEach { s ->
                        Chip(s.label, selected = settings.playerStyle == s) {
                            Prefs.update { it.copy(playerStyle = s) }
                        }
                    }
                }
                Divider()
                ChoiceRow("Ambient glow", settings.glow.label) {
                    Prefs.Glow.entries.forEach { g ->
                        Chip(g.label, selected = settings.glow == g) {
                            Prefs.update { it.copy(glow = g) }
                        }
                    }
                }
                Divider()
                ToggleRow("Lyrics on player", "Show the current line under the artwork", settings.lyricsOnPlayer) { v ->
                    Prefs.update { it.copy(lyricsOnPlayer = v) }
                }
            }
        }

        item { GroupLabel("Storage") }
        item {
            SettingsCard {
                NavRow(
                    "Downloads",
                    "${downloads.size} tracks · ${fmtBytes(downloads.sumOf { it.bytes })}",
                    G.Download,
                ) { onOpenDownloads() }
                Divider()
                NavRow("Listening stats", "Time, top artists, weekly bars", G.Chart) { onOpenStats() }
                Divider()
                NavRow("Clear search history", "Removes saved queries", G.Search) { Store.clearQueries() }
                Divider()
                NavRow("Clear play history", "Keeps your playlists and likes", G.Clock) { Store.clearHistory() }
                Divider()
                NavRow(
                    "Remove all downloads",
                    if (downloads.isEmpty()) "Nothing saved" else fmtBytes(downloads.sumOf { it.bytes }),
                    G.Trash,
                ) { Downloads.clearAll() }
            }
        }

        item { GroupLabel("About") }
        item {
            SettingsCard {
                NavRow("Version", "1.0.1 (build 2)", G.Sparkle) { }
                Divider()
                NavRow("Built by", "Iota Coder · iota14934@gmail.com", G.User) { }
                Divider()
                NavRow("Source", "github.com/Iotatg/BunMusic", G.Youtube) { }
            }
        }

        item {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "BunMusic streams from YouTube Music with its own client. " +
                        "No ads, no tracking, no account required. Bundled tracks are original recordings.",
                    color = Ink.Faint,
                    fontSize = 11.sp,
                    fontFamily = SoraFamily,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

fun fmtBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 MB"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

@Composable
private fun GroupLabel(text: String) {
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
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Ink.Card)
            .padding(vertical = 4.dp),
    ) { content() }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.Text, fontSize = 13.5.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink.OnAccent,
                checkedTrackColor = Ink.Lime,
                uncheckedThumbColor = Ink.Muted,
                uncheckedTrackColor = Ink.Line,
            ),
        )
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, chips: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, color = Ink.Text, fontSize = 13.5.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
        Text(subtitle, color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, glyph: G, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center,
        ) { Glyph(glyph, Ink.Muted, 15.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.Text, fontSize = 13.5.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily)
        }
        Glyph(G.Forward, Ink.Faint, 14.dp)
    }
}
