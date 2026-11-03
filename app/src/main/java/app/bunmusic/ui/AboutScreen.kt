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
import app.bunmusic.core.AppVersion
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
                    "${AppVersion.VERSION} · build ${AppVersion.CODE} · ${Skin.current.title} bun",
                    color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily,
                )
            }
        }

        item { GroupTitle("New in 2.2.0") }
        item {
            AboutCard {
                Change("One motion vocabulary", "Three curves and four springs, named once in the core and used everywhere. Forty slightly different `tween(300)`s is not speed, it is noise — a page change, a sheet and a button now land on the same beat")
                Change("Every tap answers", "Cards, rows, chips, buttons and tabs dip under the finger and spring back. Three per cent is enough for the thumb to know the tap registered; more and a grid of albums visibly wobbles as you scroll past it")
                Change("Pages arrive instead of appearing", "A screen change now fades, slides a ninth of a page and scales 2.8 % in the direction of travel — back goes the other way. It is the same information, but the eye is told whether it went deeper or came back")
                Change("The home page has weather", "Three hues derived from whatever is playing, drifting on a slow Lissajous behind the list. Not a blur: two overlapping gradients that look the same and cost nothing, because they sit behind a list that is also scrolling")
                Change("The record keeps its angle", "Pausing used to reset the disc to 0°, which reads as the record being lifted off and put back between two frames. It coasts to a stop now, and holds wherever it stopped")
                Change("The play button morphs", "Play, pause and buffering swap with a scale-and-fade instead of a cut, the disc dips under the thumb, and the haptic fires on press rather than release — the order a physical button works in")
                Change("Numbers count, bars grow", "Plays, days, the seven-day chart and the listening clock animate from zero with a 14 ms head start per bar. The one page whose whole subject is the shape of the data now draws that shape")
                Change("Lists are dealt, not loaded", "The greeting, the search pill, the hero card and the made-for-you row arrive in sequence. The stagger is capped at 160 ms: without a cap the fortieth row waits a second, which is the difference between animated and slow")
                Change("The chrome arrives with the music", "The now-playing strip slides up the first time a track exists instead of blinking into place, and toasts enter from below and leave the way they came")
                Change("Skeletons that move", "The loading rows had a shimmer mixed from two card tones that are nearly the same colour on Midnight, so it read as a grey box. The sheen is a measured mix toward white or black now, and it travels further")
                Change("All of it yields", "Every effect above checks the reduced-motion switch — in the app and in the phone's own developer settings — and collapses to a still frame. An aurora is not worth a migraine")
            }
        }

        item { GroupTitle("New in 2.1.0") }
        item {
            AboutCard {
                Change("Nothing takes the app down", "An uncaught exception used to end the process and the listener's evening. It is written to a small file now, and the app comes back on Home with the queue where it was — one line at the bottom, not a dialog")
                Change("Two crashes in ninety seconds stops trying", "A relaunch loop is worse than a crash: the phone gets warm and the bug never gets read. The second failure inside the window hands the process back to the system, honestly")
                Change("Background work cannot kill it either", "Cache writes, downloads, widget redraws and the playback tick each run in their own scope. A throw in one used to reach the default handler and take everything with it; each scope has a handler now and a failure is a log line")
                Change("Startup survives a corrupt file", "Every initialiser — store, prefs, auth, cache, downloads, session, scrobble, playback — is wrapped. A phone with a full disk opens a slightly empty app instead of closing on launch and closing again on the next one")
                Change("Settings › Stability", "What went wrong, when, on which screen, and the stack. Copy the report, clear it, or press one of two buttons that make it happen on purpose — a claim about crash recovery is worth nothing until it has been made to happen on the phone in your hand")
                Change("Retry, where the error is", "A dead socket, an expired stream URL or a cache entry trimmed mid-song used to leave a red sentence and no way out but to find the song again. The error row carries the retry")
                Change("Shape, the axis that had no setting", "Every cover, playlist and shortcut was the same 14 dp corner. Eight silhouettes now — squircle, arch, leaf, gem, pill, clam — and “Auto” gives each cover one from its own id, so an album always looks like itself")
                Change("A split button", "“Play all” and “Shuffle” were two pills of equal weight, which is a question the screen should have answered. One control leads with the action; shuffle, play-next and download sit behind the chevron")
                Change("A segmented group", "The library's four tabs were chips that scrolled sideways, which is right for tags and wrong for a switch. One track, one travelling indicator — and the same control scopes the stats periods")
                Change("Throw a row sideways", "Right queues it next, left queues it last. The row follows the finger, a labelled panel appears underneath, and the action fires past a threshold so a flick while scrolling costs nothing")
                Change("Long press to select, twice for a range", "Selection needed the header button and then one tap per song. A long press starts it, a second long press takes the run between, and the bar gained select-all and play-next")
                Change("Readable on any cover", "The play button drew its glyph in the player's own background colour, so a dark cover gave a black glyph on near-black. The tint is measured against the artwork and mixed until it clears 4.5:1 — the same arithmetic decides whether a scrim is needed at all")
                Change("Find in queue", "A radio queue runs to hundreds of songs and the only way to reach one was to scroll. The queue has a filter now, and it keeps each row's real position so tapping still jumps to the right slot")
                Change("The library stopped being quadratic", "Downloaded and stats rows resolved each track by re-reading and re-splitting the whole registry — a few hundred parses per scroll frame. The registry is parsed once and kept, and every bulk lookup goes through a map")
                Change("A line break is a title, not a record separator", "Escaping the newline rather than deleting it: “First\\nsecond” came back as “First second”, which is a different song")
                Change("156 commands", "Shape presets, the stability page, the crash report and its clear — the catalogue is the inventory of the app, so it grew with it")
                Change("1.6: the notification that never appeared", "A media session service is not created by its manifest entry — something has to start it, and nothing did. So the session, the lock-screen card, the headset buttons and media resumption never existed. Every play path starts the service now, and it goes foreground honestly")
                Change("2.0: fifty-eight things", "Daily mixes, taste profile, time capsule, smart shuffle, A–B loop, crossfade-friendly sleep, storage screen, duplicate finder, M3U import, cover grid, queue panel, visualiser, motion and contrast settings")
            }
        }

        item { GroupTitle("New in 1.5.0") }
        item {
            AboutCard {
                Change("The lock screen, fixed at the root", "The media session was built on the player directly, and this player only ever holds one track — so the session concluded there was no next and no previous, and the lock screen showed a play button and nothing else. The queue is real; only the player's view of it was missing. A session bridge now answers for it")
                Change("A notification with a position", "The default one is the platform's media template: three buttons and no time. This one draws artwork, title, artist, elapsed and remaining, and a progress bar that is redrawn once a second while playing and stops when paused")
                Change("Like, shuffle and repeat from outside", "Three custom session commands, declared once and drawn both in the notification and by the system's own media control on Android 13+. The glyphs carry the state: a filled heart, a crossed shuffle, a repeat with a 1")
                Change("It comes back after a reboot", "Android 13 media resumption asks the app what was playing. The answer is the saved session, with each track carried whole inside the media item, because the player underneath cannot be trusted to remember a queue")
                Change("When nothing appears, the reason is a system switch", "Settings → Lock screen & notifications shows whether notifications are allowed at all, and opens the page that decides it. An app cannot grant itself that permission, so it can only tell you where it is")
            }
        }

        item { GroupTitle("Earlier") }
        item {
            AboutCard {
                Change("Your year, from your own log", "The app counted totals but threw the timestamps away, so it could say \"41 plays of this artist\" and not \"March\" or \"2 am\". Every play now lands in a timestamped log, capped at 1,200 entries — about 45 KB — so the questions that need a date finally have an answer")
                Change("A year in review", "Ten cards built from that log: minutes, top artist, the track you actually meant it about, busiest day, longest streak, the share of plays after midnight, and the tracks you tried exactly once. Under ten plays it says so instead of congratulating you for nothing")
                Change("Share the year as one image", "1080 × 1350, drawn with android.graphics from the same summary the screen renders, so the picture and the screen can never disagree. Settings → Year in review, or the command deck")
                Change("Stats you can scope", "The old screen was all-time or nothing. 7 days, 30 days and this year now scope the chart, the top lists and the clock together — one window, so the numbers above a chart always add up to it")
                Change("A listening clock", "24 bars, with the night hours tinted differently, and the peak hour printed in the corner. It is the one chart that is about you rather than about the music")
                Change("Clearing history clears the log", "One switch removes both, because a year in review that keeps showing plays you asked to forget is a bug with good typography")
            }
        }

        item { GroupTitle("Earlier") }
        item {
            AboutCard {
                Change("227 tests, from 65", "The suite pins the arithmetic the app is built out of: LRC parsing and query cleaning, the four formatters on every row, the equaliser's preset spread across five to ten bands, which audio stream a quality setting picks, both track encoders, the queue trimming that decides what comes back after a cold start, the window breakpoints, the command ranking, and the colour extractor's edge cases. Two of them found bugs — both below")
                Change("Saving the session could crash", "The queue is written to disk every few seconds, and the cursor was clamped with coerceIn(0, lastIndex) — which throws on an empty range. Remove tracks from the queue and leave the index past the end for one frame, and the save that lands in that frame took the app with it. The cursor is clamped against a floor of zero now")
                Change("A line break could cost the queue", "Rows were joined with \\n while a row is a track's own fields — so a local file whose ID3 title contains a line break split one track into two broken rows, and the restore quietly dropped everything after it. Rows are joined with a control character no title contains, under a new key so an old blob is never half-read")
                Change("Downloads you can watch", "Downloading used to be a silent suspend function: fifty tracks meant fifty connections, no progress, no way to stop one, and a failure that showed up as a toast you had already missed. It is a queue now — two at a time, a real progress bar per track, cancel, retry, and a speed read-out")
                Change("The queue survives the app dying", "What you asked for is written to disk, so a download list interrupted by the system coming back does not vanish. The bytes are not resumed — a partial file with no matching server range is a lie — only the list")
                Change("Wi-Fi only, finally wired", "The preference existed since the first build with no reader and no switch. It gates the queue now, and a download held back by it says \"Waiting for Wi-Fi\" instead of failing quietly")
                Change("Colour from the cover", "The player used to hash the track's id into one of eight hues, which is why two songs with the same artwork came out green and orange. It now takes a median-cut palette from the image itself, scored so the winner can carry white text, and cross-fades into place so the first frame is not a flash of the wrong colour")
                Change("A colour you can turn off", "If you liked the stable hash, Settings → Colour from the cover puts it back. Same switch in the command deck")
                Change("Four widgets, not one", "A 4×2 card with cover, progress, elapsed/total, like, transport and queue; a 2×2 cover; a 4×1 strip; and a 4×4 queue whose rows jump to the track. The old widget was a single 4×1 bar with three buttons")
                Change("The widget wears the bun", "Match the bun, or pin Midnight, Charcoal, Cloud, Pink, or smoked glass. Three corner shapes, and every element can be switched off per widget — long-press it to reconfigure")
                Change("It resizes instead of clipping", "Below 118 dp the time row goes, below 96 dp the bar goes, below 200 dp wide the like and queue buttons go. Nothing is ever cut in half by a cell that is one row shorter than expected")
                Change("It survives a reboot", "Cover art is cached on disk, because the launcher asks for a redraw before the phone has finished connecting. A widget with no art looks broken, not loading")
                Change("The command deck", "115 commands — playback, queue, library, navigation, appearance, audio, data, widgets, system — in one searchable sheet. Settings → Command deck, or the shortcut")
                Change("A widget tap now arrives", "The app is singleTop, so a second tap landed in onNewIntent, which was never overridden. Extras were read once in onCreate and then ignored — tapping the widget with the app open did nothing")
                Change("Quick Settings, four shortcuts", "A play/pause tile that mirrors the session, and long-press shortcuts for now playing, shuffle likes, search and the deck")
                Change("No widgets, no cost", "When nothing is placed, the twice-a-second playback tick skips the launcher entirely instead of asking four providers whether they exist")
                Change("The playing bar is no longer cut in half", "The light buns packed the now-playing strip (62 dp) and the tab bar (87 dp) into a hard-coded 122 dp box, so the bar was drawn straight over the bottom half of the strip. The bottom of the screen is a Column that is exactly as tall as what it holds now, and the system inset is applied once, at the container")
                Change("Pause pauses", "The pill on the home card was decoration inside a card that opened the player — the most obvious button in the app did something else. It toggles playback; tapping the card still opens the player")
                Change("The heart saves", "In the mini player the heart filled while the song played and paused the track when tapped. It is a like button now, and it says so")
                Change("No separator pointing at nothing", "YouTube Music doesn't always return an album, and the hero card printed \"Artist ·\" anyway. The credit line is built from the parts that exist")
                Change("A list, not a stray dot", "Rows without a number printed a lone bullet in the gutter, half a line below the title. Lists that have positions are numbered, and the number sits on the title's first line")
                Change("A pill, not a stripe", "On a wide window the tab bar stretched bezel to bezel and the strip's two ends drifted a screen apart. The bar caps at 560 dp and the strip at 780 dp, both centred")
                Change("Toasts know where the bar is", "The toast was lifted by a hard-coded 120 dp. It measures the bottom chrome instead")
                Change("Every screen, every screen", "The layout has three width classes now — phone, small tablet, large tablet — and each one is drawn for its size instead of the phone being stretched. Tested from 320 dp to 1280 dp")
                Change("The tabs move to the side", "From 600 dp the bottom bar becomes a rail down the left edge: same four destinations, same glyphs, same selected fill, and 84 dp of height back for the content")
                Change("A tablet player, not a blown-up phone", "From 840 dp in landscape the now-playing screen splits: artwork left, controls right. Portrait keeps one column, capped so the disc isn't a wall")
                Change("Landscape stops clipping", "On a short window the flexible gaps collapse to a fixed 8 dp and the artwork is capped by height, so the transport row can't be pushed off the bottom")
                Change("No more 1000 dp lines", "Reading columns are capped at 720 / 1040 dp and centred. A song title is no longer a 90-character line on a 12-inch tablet")
                Change("The app remembered nothing", "1.1.0 lost the queue, the position, the speed and the shuffle state the moment the process died. All four are written to disk now and come back paused at the same second")
                Change("Crossfade was a lie", "The row existed, the preference existed, the engine did not. There is a real gain curve now — smoothstep, halved on short tracks so the two fades cannot cancel out")
                Change("Gapless was a lie too", "It is the prefetch: the only gap between two separate streams is the resolve, so pre-loading the next track is the whole feature")
                Change("A library you can search", "Liked, Recent and Downloaded had no search and no sort. One field and three orders now, and Play all / Shuffle on the two lists that were missing them")
                Change("Playlists nobody made", "Most played, on repeat this week, and not played in a month — derived from what the app already counts, no second copy of the truth")
                Change("Select more than one", "Like, queue or download a whole selection at once. One write, not one per track")
                Change("Word by word", "The active lyric line lights up as it is sung. LRCLIB ships line timings, so the split is proportional — and the line is tappable to seek")
                Change("Lyrics you can take with you", "Copy the whole song, or share it as text, from the lyric sheet")
                Change("Follow an artist", "Artist pages get a follow, a radio seed and a bulk download; every collection gets Share")
                Change("A receipt", "Your numbers as a 1080 × 1350 card, drawn with android.graphics so it looks the same every time, out through a FileProvider")
                Change("Scrobbling, honestly", "ListenBrainz with a pasted token. Nothing leaves the device without one, failures are silent, and a listen that cannot go out is queued, not lost")
                Change("Not for me", "A dislike that also skips — a button that only writes a flag would be a lie")
                Change("Twice a second, for nothing", "Screens were handed the live player state, so the whole page recomposed at 2 Hz to draw a progress number none of them show. They get the tick-free copy now")
                Change("A mood wall, not a mood column", "Explore's grid runs 2 / 3 / 4 tiles wide depending on the window, and carousel cards grow with it")
                Change("Rows remember what they are", "Lists carry stable keys, so a re-render reuses a row instead of rebuilding it — and a duplicated id can no longer take the list down")
                Change("Resizing doesn't restart", "Rotation, split screen and unfolding are handled in place: the stack, the queue and the scroll position survive a resize")
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
                Fact("Version", AppVersion.LABEL)
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
