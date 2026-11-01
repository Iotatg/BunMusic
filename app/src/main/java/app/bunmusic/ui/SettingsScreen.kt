package app.bunmusic.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.AppVersion
import app.bunmusic.core.BunSkin
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.Skin
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Auth
import app.bunmusic.data.Backup
import app.bunmusic.data.Cache
import app.bunmusic.data.Downloads
import app.bunmusic.data.Eq
import app.bunmusic.data.Prefs
import app.bunmusic.data.Scrobble
import app.bunmusic.data.Session
import app.bunmusic.data.Store
import app.bunmusic.player.BunNotificationProvider
import app.bunmusic.player.Playback
import app.bunmusic.system.PlaybackControls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenWrapped: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenBuns: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenWidgets: () -> Unit,
    onOpenCommands: () -> Unit,
    onToast: (String) -> Unit,
) {
    val settings by Prefs.settings.collectAsState()
    val downloads by Downloads.entries.collectAsState()
    val downloadQueue by Downloads.queue.collectAsState()
    val account by Auth.account.collectAsState()
    val eq by Eq.state.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The four checks behind the lock screen, re-read whenever the screen comes
    // back to the front — the usual way to fix one is to leave for the system
    // settings and come back.
    var controlTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controlTick++ }
    val controls = remember(controlTick) { PlaybackControls.read(context) }
    var googleSheet by remember { mutableStateOf(false) }
    var tokenSheet by remember { mutableStateOf(false) }
    var cacheTick by remember { mutableIntStateOf(0) }
    val streamBytes by produceState(0L, cacheTick) {
        value = withContext(Dispatchers.IO) { Cache.streamBytes() }
    }
    val imageBytes by produceState(0L, cacheTick) {
        value = withContext(Dispatchers.IO) { Cache.imageBytes() }
    }
    val offlineCount by produceState(0, cacheTick) {
        value = withContext(Dispatchers.IO) {
            Cache.refreshIndex()
            Cache.offlineCount()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val body = Backup.export(account?.handle ?: "guest")
                    context.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
                        ?: error("Could not open the file")
                }
            }
            onToast(
                outcome.fold(
                    onSuccess = { "Backup saved" },
                    onFailure = { it.message ?: "Backup failed" },
                )
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Could not read the file")
                    Backup.import(text).getOrThrow()
                }
            }
            onToast(
                outcome.fold(
                    onSuccess = { "Restored · $it" },
                    onFailure = { it.message ?: "Restore failed" },
                )
            )
        }
    }

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
                    "BunMusic ${AppVersion.VERSION} · Iota Coder",
                    color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily,
                )
            }
        }

        // ---- account
        item {
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Ink.Card)
                    .clickable { onOpenProfile() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val acc = account
                if (acc != null) {
                    Avatar(acc, 44.dp)
                } else {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Ink.Hairline),
                        contentAlignment = Alignment.Center,
                    ) { Glyph(G.User, Ink.Muted, 18.dp) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        acc?.name ?: "Browsing as guest",
                        color = Ink.Text, fontSize = 14.sp, fontFamily = SoraFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        acc?.handle ?: "Sign in to sync your library",
                        color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily,
                    )
                }
                Glyph(G.Forward, Ink.Faint, 15.dp)
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
                // This used to be a switch wired to nothing. It is now the thing
                // that decides whether the next track is pre-resolved, which is
                // exactly what removes the gap between two separate streams.
                ToggleRow(
                    "Gapless playback",
                    "Pre-loads the next track so songs run into each other",
                    settings.gapless,
                ) { v -> Prefs.update { it.copy(gapless = v) } }
                Divider()
                ChoiceRow(
                    "Crossfade",
                    if (settings.crossfadeSec == 0) {
                        "Off — songs start and stop at full volume"
                    } else {
                        "Fades the last ${settings.crossfadeSec}s out and the first ${settings.crossfadeSec}s in"
                    },
                ) {
                    listOf(0, 3, 6, 12).forEach { sec ->
                        Chip(if (sec == 0) "Off" else "${sec}s", selected = settings.crossfadeSec == sec) {
                            Prefs.update { it.copy(crossfadeSec = sec) }
                        }
                    }
                }
                Divider()
                ToggleRow(
                    "Autoplay radio",
                    "Keep going with similar tracks when the queue ends",
                    settings.autoplay,
                ) { v -> Prefs.update { it.copy(autoplay = v) } }
                Divider()
                ToggleRow(
                    "Offline only",
                    "Never stream — play downloads and bundled tracks",
                    settings.offlineOnly,
                ) { v -> Prefs.update { it.copy(offlineOnly = v) } }
                Divider()
                ToggleRow(
                    "Resume where you left off",
                    "Brings the last queue back, paused at the same second",
                    settings.resumeOnLaunch,
                ) { v ->
                    Prefs.update { it.copy(resumeOnLaunch = v) }
                    if (!v) Session.clear()
                }
            }
        }

        item { GroupLabel("Sound") }
        item {
            SettingsCard {
                NavRow(
                    "Equaliser",
                    if (eq.available) {
                        if (settings.eqEnabled) "${settings.eqPreset} · ${eq.bands.size} bands" else "Off"
                    } else {
                        "Not available on this output"
                    },
                    G.Sliders,
                ) { onOpenEqualizer() }
                Divider()
                ToggleRow("Volume normalisation", "Evens out loud and quiet tracks", settings.normalize) { v ->
                    Prefs.update { it.copy(normalize = v) }
                    Eq.setNormalisation(v)
                }
            }
        }

        item { GroupLabel("Appearance") }
        item {
            SettingsCard {
                NavRow(
                    "Buns",
                    "${Skin.current.title} · ${Skin.current.tagline}",
                    G.Grid,
                    bun = Skin.current,
                ) { onOpenBuns() }
                Divider()
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
                Divider()
                ChoiceRow(
                    "Lyrics offset",
                    if (settings.lyricsOffsetMs == 0) "In step" else "${settings.lyricsOffsetMs / 1000f}s shift",
                ) {
                    listOf(-1000, 0, 1000, 2000).forEach { ms ->
                        Chip(
                            when {
                                ms == 0 -> "0"
                                ms > 0 -> "+${ms / 1000}s"
                                else -> "${ms / 1000}s"
                            },
                            selected = settings.lyricsOffsetMs == ms,
                        ) { Prefs.update { it.copy(lyricsOffsetMs = ms) } }
                    }
                }
                Divider()
                ToggleRow(
                    "Colour from the cover",
                    if (settings.artColour) {
                        "The player takes its tint from the artwork"
                    } else {
                        "A stable colour per track, derived from its id"
                    },
                    settings.artColour,
                ) { v -> Prefs.update { it.copy(artColour = v) } }
                Divider()
                ToggleRow(
                    "Word-by-word lyrics",
                    "Lights up each word as the line is sung",
                    settings.wordLyrics,
                ) { v -> Prefs.update { it.copy(wordLyrics = v) } }

                Divider()
                ToggleRow("Haptics", "A small tick on like and play", settings.haptics) { v ->
                    Prefs.update { it.copy(haptics = v) }
                    app.bunmusic.core.Haptics.enabled = v
                }
            }
        }

        item { GroupLabel("Home screen") }
        item {
            SettingsCard {
                NavRow(
                    "Widgets",
                    "Four sizes · put one on your home screen",
                    G.Grid,
                ) { onOpenWidgets() }
                Divider()
                NavRow(
                    "Command deck",
                    "Every action in the app, searchable",
                    G.Search,
                ) { onOpenCommands() }
            }
        }

        item { GroupLabel("Listening") }
        item {
            SettingsCard {
                ToggleRow(
                    "ListenBrainz scrobbling",
                    if (Scrobble.token.isBlank()) {
                        "Paste a token first — nothing is sent without one"
                    } else {
                        "Sends each play to your ListenBrainz profile"
                    },
                    Scrobble.enabled,
                ) { v ->
                    if (v && Scrobble.token.isBlank()) {
                        tokenSheet = true
                    } else {
                        Scrobble.setEnabled(v)
                        onToast(if (v) "Scrobbling on" else "Scrobbling off")
                    }
                }
                Divider()
                NavRow(
                    "ListenBrainz token",
                    if (Scrobble.token.isBlank()) "Not set" else "••••" + Scrobble.token.takeLast(6),
                    G.Key,
                ) { tokenSheet = true }
                Divider()
                NavRow(
                    "Listening receipt",
                    "A shareable card of your numbers",
                    G.Receipt,
                ) { ShareCard.share(context) }
                if (Scrobble.pending() > 0) {
                    Divider()
                    NavRow(
                        "Queued scrobbles",
                        "${Scrobble.pending()} waiting for a connection",
                        G.Cloud,
                    ) {
                        Scrobble.clearQueue()
                        onToast("Queue cleared")
                    }
                }
            }
        }

        item { GroupLabel("Storage") }
        item {
            SettingsCard {
                ChoiceRow(
                    "Stream cache",
                    when (settings.cacheLimitMb) {
                        0 -> "Off — every play re-downloads"
                        -1 -> "Unlimited · ${fmtBytes(streamBytes)} used"
                        else -> "Up to ${settings.cacheLimitMb} MB · ${fmtBytes(streamBytes)} used"
                    },
                ) {
                    listOf(0, 256, 512, 1024, -1).forEach { mb ->
                        Chip(
                            when (mb) {
                                0 -> "Off"
                                -1 -> "Max"
                                else -> if (mb >= 1024) "1 GB" else "$mb MB"
                            },
                            selected = settings.cacheLimitMb == mb,
                        ) {
                            Prefs.update { it.copy(cacheLimitMb = mb) }
                            Cache.onLimitChanged(playbackActive = Playback.state.value.hasTrack)
                            cacheTick++
                        }
                    }
                }
                Divider()
                ToggleRow(
                    "Cache songs as they play",
                    if (settings.cacheLimitMb == 0) {
                        "Turn the stream cache on first"
                    } else {
                        "$offlineCount songs playable offline · each track is kept once it starts"
                    },
                    settings.prefetchAhead && settings.cacheLimitMb != 0,
                ) { v ->
                    if (settings.cacheLimitMb == 0 && v) {
                        Prefs.update { it.copy(cacheLimitMb = 512, prefetchAhead = true) }
                        Cache.onLimitChanged(playbackActive = Playback.state.value.hasTrack)
                    } else {
                        Prefs.update { it.copy(prefetchAhead = v) }
                    }
                    cacheTick++
                }
                Divider()
                ToggleRow("Skip silent gaps", "Jumps over silence inside a track", settings.skipSilence) { v ->
                    Prefs.update { it.copy(skipSilence = v) }
                    Playback.applySkipSilence(v)
                }
                Divider()
                NavRow(
                    "Clear stream cache",
                    if (streamBytes == 0L) "Empty" else "${fmtBytes(streamBytes)} of audio",
                    G.Trash,
                ) {
                    // A cache cannot be deleted from under a decoder: stop first,
                    // then clear, and say so rather than silently cutting the music.
                    val wasPlaying = Playback.state.value.hasTrack
                    if (wasPlaying) Playback.stop()
                    Cache.clearStream()
                    cacheTick++
                    onToast(if (wasPlaying) "Playback stopped · cache cleared" else "Stream cache cleared")
                }
                Divider()
                NavRow(
                    "Clear image cache",
                    if (imageBytes == 0L) "Empty" else "${fmtBytes(imageBytes)} of cover art",
                    G.Trash,
                ) {
                    Cache.clearImages()
                    cacheTick++
                    onToast("Image cache cleared")
                }
                Divider()
                // wifiOnly existed in Prefs since the first build with no reader
                // and no switch. It is the download gate now, so it needs a face.
                ToggleRow(
                    "Download on Wi-Fi only",
                    if (settings.wifiOnly) {
                        "Downloads wait for Wi-Fi instead of using mobile data"
                    } else {
                        "Downloads use mobile data when there is no Wi-Fi"
                    },
                    settings.wifiOnly,
                ) { v -> Prefs.update { it.copy(wifiOnly = v) } }
                Divider()
                NavRow(
                    "Downloads",
                    when {
                        downloadQueue.isNotEmpty() -> "${downloadQueue.size} in the queue · ${downloads.size} saved"
                        else -> "${downloads.size} tracks · ${fmtBytes(downloads.sumOf { it.bytes })}"
                    },
                    G.Download,
                ) { onOpenDownloads() }
                Divider()
                NavRow("Listening stats", "Time, top artists, weekly bars", G.Chart) { onOpenStats() }
                Divider()
                NavRow("Year in review", "Your year as a scrollable reel", G.Sparkle) { onOpenWrapped() }
                Divider()
                // Four independent things decide whether the lock screen shows
                // anything at all, and a failure in any of them looks identical
                // from the outside: silence. The summary comes first and names
                // the blocker; the four rows under it are the detail, each with
                // the page that fixes it.
                NavRow(
                    "Lock screen & notifications",
                    PlaybackControls.headline(controls),
                    G.Bell,
                ) { openNotificationSettings(context, null) }
                Divider()
                controls.forEach { check ->
                    CheckRow(check.title, check.detail, check.state) {
                        when (check.fix) {
                            PlaybackControls.Fix.NOTIFICATIONS -> openNotificationSettings(context, null)
                            PlaybackControls.Fix.CHANNEL ->
                                openNotificationSettings(context, BunNotificationProvider.CHANNEL_ID)
                            PlaybackControls.Fix.BATTERY -> openBatterySettings(context)
                            null -> onToast(
                                if (check.ok) "Nothing to fix here"
                                else "Play a track — the service starts with the first one"
                            )
                        }
                    }
                    Divider()
                }
                NavRow("Clear search history", "Removes saved queries", G.Search) {
                    Store.clearQueries()
                    onToast("Search history cleared")
                }
                Divider()
                NavRow("Clear play history", "Keeps your playlists and likes", G.Clock) {
                    Store.clearHistory()
                    onToast("Play history cleared")
                }
                Divider()
                NavRow(
                    "Remove all downloads",
                    if (downloads.isEmpty()) "Nothing saved" else fmtBytes(downloads.sumOf { it.bytes }),
                    G.Trash,
                ) {
                    Downloads.clearAll()
                    onToast("Downloads removed")
                }
            }
        }

        item { GroupLabel("Your data") }
        item {
            SettingsCard {
                NavRow(
                    "Back up library",
                    "Likes, playlists and history as a JSON file",
                    G.Shield,
                ) {
                    exportLauncher.launch("bunmusic-backup-${System.currentTimeMillis() / 1000}.json")
                }
                Divider()
                NavRow("Restore from backup", "Merges a BunMusic JSON file", G.Refresh) {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
                Divider()
                NavRow("Share BunMusic", "Send the app link to a friend", G.Share) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "BunMusic — a quiet place for loud music. https://github.com/Iotatg/BunMusic",
                        )
                    }
                    context.startActivity(Intent.createChooser(send, "Share BunMusic"))
                }
            }
        }

        item { GroupLabel("Accounts") }
        item {
            SettingsCard {
                NavRow(
                    "Google sign-in",
                    if (settings.googleClientId.isBlank()) "Not configured — tap to set up" else "Client ID saved",
                    G.Google,
                ) { googleSheet = true }
                Divider()
                NavRow(
                    "Manage account",
                    account?.let { "Signed in as ${it.name}" } ?: "Guest mode",
                    G.User,
                ) { onOpenProfile() }
            }
        }

        item { GroupLabel("About") }
        item {
            SettingsCard {
                NavRow(
                    "About BunMusic",
                    "${AppVersion.VERSION} (build ${AppVersion.CODE}) · what changed",
                    G.Sparkle,
                ) { onOpenAbout() }
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
                        "No ads, no tracking. Bundled tracks are original recordings. " +
                        "Accounts live on this device — nothing is uploaded anywhere.",
                    color = Ink.Faint,
                    fontSize = 11.sp,
                    fontFamily = SoraFamily,
                    lineHeight = 17.sp,
                )
            }
        }
    }

    if (tokenSheet) {
        TokenSheet(
            initial = Scrobble.token,
            onDismiss = { tokenSheet = false },
            onSave = { value ->
                Scrobble.token = value
                Scrobble.setEnabled(value.isNotBlank())
                tokenSheet = false
                onToast(if (value.isBlank()) "Token cleared" else "Token saved")
            },
        )
    }

    if (googleSheet) {
        GoogleSetupSheet(
            current = settings.googleClientId,
            onDismiss = { googleSheet = false },
            onSave = { id ->
                Prefs.update { it.copy(googleClientId = id.trim()) }
                googleSheet = false
                onToast(if (id.isBlank()) "Client ID cleared" else "Google client ID saved")
            },
        )
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
        fontSize = 10.sp,
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.9.sp,
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
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Ink.Hairline))
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
private fun NavRow(title: String, subtitle: String, glyph: G, bun: BunSkin? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (bun != null) {
            // Show the real launcher icon rather than a stand-in glyph: it is what
            // the home screen is actually wearing. BunIcon composes the adaptive
            // layers instead of loading the adaptive-icon XML, which Compose
            // cannot paint — the 1.0.7 Settings crash.
            BunIcon(
                skin = bun,
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(10.dp),
            )
        } else {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(Ink.Hairline),
                contentAlignment = Alignment.Center,
            ) { Glyph(glyph, Ink.Muted, 15.dp) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.Text, fontSize = 13.5.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily)
        }
        Glyph(G.Forward, Ink.Faint, 14.dp)
    }
}

/**
 * One line of the lock-screen diagnostics: a coloured dot, what was checked, and
 * what to do about it. The dot carries the verdict, so the row can be read
 * without parsing the sentence — green means nothing to do.
 */
@Composable
private fun CheckRow(title: String, detail: String, state: PlaybackControls.State, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        PlaybackControls.State.OK -> Ink.Lime
                        PlaybackControls.State.WARN -> Ink.Faint
                        PlaybackControls.State.FAIL -> Ink.Rose
                    }
                )
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.Text, fontSize = 13.5.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
            Text(detail, color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily)
        }
        if (state != PlaybackControls.State.OK) {
            Text(
                "Fix",
                color = Ink.Lime,
                fontSize = 11.5.sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** The app's notification page, or the playback channel's own page when asked. */
private fun openNotificationSettings(context: Context, channelId: String?) {
    runCatching {
        val intent = if (channelId == null) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        }
        context.startActivity(intent)
    }
}

/** The battery-optimisation list. Not the per-app request dialog: that needs a
 *  permission Google Play refuses to apps that are not "battery critical". */
private fun openBatterySettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
}

/**
 * Where a ListenBrainz token is pasted.
 *
 * Deliberately not a link-and-redirect OAuth flow: ListenBrainz hands out a token
 * on a web page, the user copies it, and that is the whole exchange. A token in a
 * password-style field is honest about what it is and cannot leak a secret the app
 * does not have.
 */
@Composable
private fun TokenSheet(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Ink.Sheet)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(22.dp)
                .navigationBarsPadding(),
        ) {
            Text("ListenBrainz token", color = Ink.Text, fontSize = 16.sp, fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "listenbrainz.org → Settings → API tokens. Paste it here; it stays on this device.",
                color = Ink.Faint, fontSize = 11.5.sp, fontFamily = SoraFamily, lineHeight = 16.sp,
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.Field)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) {
                    Text("eyJhbGciOi…", color = Ink.Faint, fontSize = 12.5.sp, fontFamily = SoraFamily)
                }
                BasicTextField(
                    value = value,
                    onValueChange = { value = it.trim() },
                    singleLine = true,
                    textStyle = TextStyle(color = Ink.Text, fontSize = 12.5.sp, fontFamily = SoraFamily),
                    cursorBrush = SolidColor(Ink.Lime),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledBtn("Save", G.Check) { onSave(value) }
                GhostBtn("Clear") { onSave("") }
            }
        }
    }
}
