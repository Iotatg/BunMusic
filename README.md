# BunMusic

A quiet music player for Android — built from scratch in Kotlin + Jetpack Compose.
Five skins, "buns", and you pick which one the app wears.
Nothing is bundled: every track comes from YouTube Music, and anything you like can be
saved offline.

```
package   app.bunmusic
version   2.4.0 (26)
minSdk    26  ·  target/compile 37
author    Iota Coder · iota14934@gmail.com
```

---

## 2.4.0 — the machine remembers

Stats tell you how much you listened. This release tells you **how** you listened
— and what you were playing on this exact date in earlier years.

| Piece | What it is |
|---|---|
| **`data/Memory.kt`** | Pure arithmetic over the play log: `onThisDay`, `dna`, `currentStreak`, `radar`. Takes `now` and the zone offset as arguments, so a year boundary and a leap day are testable rather than hopeful |
| **Listening DNA** | Five indices over the last 90 days — Curiosity (tracks heard once), Loyalty (tracks heard 4+ times), Range (artists per track), Night (plays after 22:00), Consistency (days with a play). Drawn as a pentagon that grows from the centre, with a list underneath saying what each axis counts |
| **On this day** | Up to five years back, the same calendar date. Distinct tracks, most recent first, the real play count beside them, and a Play button that queues that day |
| **Streak** | Counts back from today — or from yesterday if today is still young. A streak that breaks at midnight and repairs itself at breakfast is not a streak |
| **Shareable** | The same pentagon rendered with `android.graphics` onto a 1080 × 1350 card. Screen and image call the same `Memory.radar`, so they cannot disagree |
| **Commands** | `mem.dna` (share the card) and `mem.today` (play that day) — 160 in total |

What it deliberately does **not** do: claim to know the music. There is no
"energy" score, because the app does not analyse audio. These numbers describe
behaviour, and below 20 plays in the window the screen says so instead of drawing
a shape that would look authoritative anyway.

459 unit tests, 0 failures. Same signing key.

Honest limit, unchanged: no phone and no emulator here. Every number is pinned by
tests, every screen compiles — nobody has looked at the pentagon on a display.

---

## 2.3.0 — Nebula

A fifth bun, and the first one that is a place rather than a palette. Deep space
violet, neon edges, a sky that is different for every song, and a bun asleep on a
cloud with a cat against its shoulder.

| Piece | What it is |
|---|---|
| **`core/Cosmos.kt`** | Pure arithmetic: a 31-hash seed, sixty stars from six lines of xorshift, a raised-cosine twinkle, a crescent's position. Testable, so "the same song always looks the same" is a claim with a test behind it |
| **`ui/Cosmos.kt`** | Paint only — `NebulaBackdrop`, `Crescent`, `BunMascot`, `Modifier.glass`, `Modifier.neon`, `GlowPill`. A violet well, three clouds, a field of stars with cross flares on the bright ones |
| **The bun** | Drawn in a 100 × 100 unit space: hood, headphones, closed eyes, blushes, a cat. One drawing scales from a 28 dp avatar in the header to a 220 dp hero in the player. It breathes at 4.2 s asleep, 2.6 s playing |
| **Glass** | Surfaces hold light at the top edge and fade toward the bottom. On the light buns the same modifier degrades to the plain card — white-on-white glass is a slightly wrong white |
| **Neon** | The accent used as a shadow colour rather than a stroke, so the play button and the nav bar look lit. API 26–27 paints it black, which is still a shadow |
| **Where it applies** | Home (sky + header mascot + empty-state card), the player (same sky, crescent behind the artwork, lit transport button), the nav bar, the mini player, the search pill, panels, the bun picker's preview |
| **The icon** | A real adaptive icon, a window theme pre-tinted `#0A0518`, and a launcher alias — cold start no longer flashes the lime bun first |
| **The widget** | Nebula card in three corner shapes and a violet transport button |
| **Swipe to skip** | The now-playing strip follows the thumb and commits past a third of its width, with the threshold in `SwipeSkip` so it is measured in fractions rather than pixels — the same 120 dp drag is most of a phone strip and a sliver of a tablet one |

The other four buns are untouched. `Skin.isNebula` gates every piece of it, so
Midnight, Charcoal, Cloud and Pink still draw the flat paper they always did.

436 unit tests, 0 failures. Same signing key.

Honest limit, unchanged: no phone and no emulator here. The star field, the
twinkle curve, the seed stability and the crescent geometry are pinned by tests,
and every screen compiles — but nobody has looked at the mascot on a display.
If the bun reads wrong on your device, the numbers to move are the 100-unit
coordinates in `drawBun`, in `ui/Cosmos.kt`.

---

## 2.2.0 — everything moves

The release that gave the app a motion vocabulary instead of forty ad-hoc tweens.
Same screens, same information — but the interface now answers the finger and
tells the eye where it went.

| System | What landed |
|---|---|
| **Motion core** | Three named curves (`Emphasised`, `Standard`, `Accelerate`) and four named springs (`snappy`, `smooth`, `bouncy`, `gentle`) in `core/Motion.kt`, plus `BunStagger` for list delays. One vocabulary, so a page change, a sheet and a button press land on the same beat |
| **Press feedback** | `Modifier.tapable` / `longTapable`: cards, rows, chips, buttons, tabs, nav items and mini-player controls dip 3 % under the finger and spring back on `BunSpring.snappy`. The ripple alone fires on release, by which time the eye has moved on |
| **Page transitions** | Screen changes fade, slide a ninth of a page and scale 2.8 % in the direction of travel — deeper pushes right, back pulls left. `depth` already existed; it now drives the direction |
| **Aurora** | `AuroraBackdrop`: three hues derived deterministically from the playing track's id, drifting on a slow Lissajous behind Home. Not a blur — two overlapping radial gradients that look the same and cost nothing, because they sit behind a scrolling list |
| **The record** | The hero disc and the player's vinyl/disc now spin while playing and *hold their angle* when paused. They used to reset to 0°, which reads as the record being lifted off and put back between two frames |
| **Morphing transport** | `MorphPlayButton`: play / pause / buffering swap with a scale-and-fade, the disc dips under the thumb, the haptic fires on press rather than release. Used in both player layouts and the hero card |
| **Numbers and bars** | `AnimatedCount` on the stats tiles; the seven-day chart, the year chart and the 24-hour clock grow from their baseline with a 14 ms head start per bar |
| **Seek smoothing** | The player publishes its position twice a second, so the seek bar used to advance in half-second steps. A linear tween across the tick makes it continuous; the thumb under the finger and any backwards jump snap instead of gliding |
| **Entrances** | Greeting, search pill, hero card and the made-for-you row arrive in sequence; the now-playing strip slides up the first time a track exists; toasts enter from below and leave the way they came; empty states fade in |
| **Skeletons** | The shimmer was mixed from two card tones that are nearly identical on Midnight, so it read as a grey box. It is a measured mix toward white or black now, and travels further |
| **Reduced motion** | Every effect above collapses to a still frame when the app switch or the phone's animator scale says so — including the aurora, the disc, the stagger and the shimmer |

422 unit tests, 0 failures. Same signing key.

Honest limit, unchanged: no phone and no emulator here. The curves, the springs,
the stagger and the colour derivation are pinned by tests, and every screen
compiles — but nobody has watched these animations on a display. If a transition
feels slow on your device, the numbers to change are in `core/Motion.kt`.

---

## 2.1.0 — nothing takes it down

Two releases in one: the crash-proofing pass, and the first pass at Material 3
Expressive's actual components rather than its colours.

| System | What landed |
|---|---|
| **Crash guard** | Every uncaught exception is written to a capped log and the app comes back on Home with its queue; two failures inside ninety seconds stop the relaunch instead of looping; every startup initialiser is wrapped so a corrupt preference file cannot close the app on launch and again on the next one |
| **Background scopes** | Cache writes, downloads, widget redraws and the playback tick each carry a `CoroutineExceptionHandler` — a throw there used to reach the default handler and end the process |
| **Stability page** | Settings › Stability: what failed, when, on which screen, the stack, copy / clear, and two buttons that make it happen on purpose |
| **Shape** | Eight silhouettes (squircle, arch, leaf, gem, pill, clam, block, round) as fractions of the short side, a morph between any two, and `Auto` — a cover's shape from its own id, so an album always looks like itself. Applied to the grid, playlist rows, mixes and the settings preview |
| **Expressive components** | A split button (Play all ▾), a segmented group with a travelling indicator (library tabs, stats periods), a morphing loading indicator, and swipe actions on rows: right plays next, left queues last |
| **Selection** | Long press starts it, a second long press takes the run between, select-all / invert in the bar, and one bulk queue call instead of N |
| **Contrast** | WCAG luminance, ratio, `bestInk`, `readable` and a solved scrim alpha. The play button no longer draws a black glyph on a dark cover; the active tint is lifted until it clears 4.5:1 |
| **Smoothness** | The registry is parsed once and kept; downloaded, stats and command rows resolve ids through a map instead of re-splitting the store per row |
| **Codec** | A newline in a title is escaped, not deleted — `First\nsecond` came back as `First second`, which is a different song |
| **Queue** | A filter over "up next" that keeps each row's real position, so tapping a match still jumps to the right slot |
| **Commands** | 156, twelve of them new |

412 unit tests, 0 failures. Same signing key.

Honest limit, again: no phone and no emulator here. Every screen compiles, every
calculation has a test, and the crash guard has a button that proves itself on the
device — but nobody has looked at these pixels on a display.

---

## 2.0.0 — fifty things Spotify does, done on the device

The list was taken from Spotify's own feature set — mixes, taste, queue, credits,
accessibility, storage — and rebuilt where BunMusic can honestly support it: no
server, no account on somebody else's machine, no second platform.

| System | What landed |
|---|---|
| **Discovery** | Daily mixes per artist, a taste profile (plays + likes, likes weigh two), "Because you liked X", "On this day" time capsule, fresh finds, deep cuts, blend with followed artists, a radio seed picker, a discovery-share number, and a "Made for you" screen that opens before the network answers |
| **Shuffle** | Smart shuffle: drops songs skipped three times, pushes the last ten plays to the back, and never puts the same artist twice in a row — the greedy "first different" version stranded the last two of a kind, so it now spends the biggest pile first |
| **Library** | Sort by title, artist, duration or plays; direction toggle on the active chip; a real filter pipeline; duplicate finder that ignores "(Remastered 2011)"; playlist totals; pinned playlists; playlist notes; a two-column cover grid |
| **Queue** | Split into recently played / now playing / up next, with running time, shuffle-upcoming, clear-played and drag reorder |
| **Player** | Visualiser bars driven by position, credits sheet, technical track info, A–B repeat, 0.05-step speed control, like burst, "go to artist / album" search |
| **Design** | Reduced motion (system + app), four-step text scale, high-contrast palette, marquee titles, counted section headers, stat pills, progress ring |
| **Storage** | Four buckets — downloads, stream cache, artwork, library — with a stacked bar, a per-bucket clear and a cleanup plan that never touches downloads |
| **Portability** | M3U export through the system document picker, M3U import with BOM/CRLF/URL tolerance, and a report of what the library did not have |
| **Offline** | A banner that appears when the phone loses its way out, polled every four seconds |
| **Commands** | 144, ten of them new |

368 unit tests, 0 failures. Same signing key.

Honest limit: no phone and no emulator in the build environment, so the new
screens are compiled and their arithmetic is tested, but nobody has watched them
on a display. The engine is pure functions on purpose — that is the part that can
be proven from here.

---

## 1.3.0 — the queue you can see

Two chains were broken in 1.2.0 and both looked fine from the outside.

| | |
|---|---|
| **Downloads are a queue** | two at a time, per-track progress, cancel, retry, pause all, and the list survives the process. It used to be one silent suspend function: fifty tracks, fifty connections, nothing on screen |
| **Wi-Fi only, wired** | the preference existed since the first build with no reader and no switch. It gates the queue, and a held-back download says so |
| **Colour from the cover** | a median cut over the real artwork instead of a hash of the track id, scored so the winner can carry white text, cross-faded in over 700 ms |
| **131 commands** | six more, including every download state |

Honest limit: pause and resume restart from zero — there is no byte-range resume,
because a partial file with no matching server range is a lie.

---

## 1.2.0 — the half that was missing

Measured against [SimpMusic](https://github.com/maxrave-dev/SimpMusic), row by row
(`BunMusic-vs-SimpMusic-1.2.0.png` in the release). What changed:

| | |
|---|---|
| **Session restore** | queue, position, speed, repeat and shuffle survive a cold start; the app comes back paused at the same second |
| **Crossfade, for real** | a smoothstep gain curve at both ends of a track — the switch used to be wired to nothing |
| **Gapless, for real** | the prefetch *is* the feature: pre-resolving the next stream is what removes the gap |
| **Library search + sort** | one field, three orders, and Play all / Shuffle on Liked and Recent |
| **Auto playlists** | most played, on repeat this week, not played in a month |
| **Multi-select** | like, queue or download a whole selection in one write |
| **Word-by-word lyrics** | proportional wipe across the line, tap any line to seek, copy or share the whole song |
| **Artist pages** | follow, radio seed, bulk download, share |
| **Listening receipt** | a 1080 × 1350 PNG drawn with `android.graphics`, out through a `FileProvider` |
| **ListenBrainz** | token-based scrobbling, silent on failure, queued when offline |
| **Not for me** | a dislike that also skips |
| **125 commands** | ten more, including all of the above |

Still missing, and listed rather than hidden: Cast, Android Auto, Wear, desktop/iOS,
AI mixes, proxy, SponsorBlock, QR login sync and Listen Together. Every one of those
needs a server, a second platform, or a paid API key.

---

## What it does

**Buns**
Five complete skins, switched in Settings → Buns. A bun is not a tint: it replaces the
palette, the typeface, the corner radii, the shape of the bottom bar, whether a list shows
cover art or a track number, and whether the seek bar is a line or a waveform.

| Bun | What it is |
|---|---|
| **Midnight Lime** | Ink black, acid lime, lavender hero, Sora + Instrument Serif. The house bun. |
| **Charcoal** | Paper white, charcoal ink, Inter throughout, 24 dp cards, waveform seek bar, white nav bar with a dark active pill, dark mini player. |
| **Cloud Blue** | Chalk white and electric blue, Sora throughout, blue hero gradient, hairline seek bar with a knob, blue active nav pill, navy mini player. Its launcher icon is the bun in headphones. |
| **Pink Bun** | Blush paper and deep rose, Sora throughout, pink hero gradient, waveform seek bar, rose active nav pill, plum mini player. Its launcher icon is the bun with the music note. |
| **Nebula** | Deep space violet, neon accent shadows, glass panels, a computed star field that changes with the song, and a mascot asleep on a cloud. Its launcher icon is the bun under a crescent. |

Switching restarts the process — the only way to be sure no `remember`ed colour from the old
bun survives. Nothing else is touched: library, downloads and account stay put.

**Accounts, for real**
Three ways in, all of them implemented rather than mocked (`data/Auth.kt`):

| Door | How it works |
|---|---|
| Email | Password stretched with PBKDF2-HMAC-SHA256, 120 000 rounds, per-account salt. Only the derived key is stored. |
| Phone | Six-digit one-time code, two-minute lifetime, five attempts, 30-second resend cooldown. |
| Google | ID token from the Play services account picker, decoded to a profile; the token is what proves identity. |

Each account gets its own library slice — likes, playlists, history and stats are namespaced
per account, so a shared phone never mixes two people's music. The first account created on a
device adopts any guest library that already exists. Switch accounts, rename, change password,
link an email to a phone account, sign out, or delete the account and everything in it.

> **Google sign-in needs one thing you must create:** an OAuth client ID. Google will not issue
> a token to an app it does not recognise. Settings → Google sign-in shows this build's
> package name and SHA-1, then takes the client ID at runtime. No ID, no fake login — the app
> says so plainly.

**Cache, not clutter**
Two disk caches, kept apart on purpose:

| Cache | Where | Ceiling | Cleared by |
|---|---|---|---|
| Stream | `filesDir/stream` | 256 MB – 1 GB, or off / unlimited | Settings → Storage, or LRU |
| Cover art | `cacheDir/art` | 220 MB | Settings → Storage |

Anything played from the network is written as it buffers, so a replay, a scrub back or the
same song tomorrow costs no data. The ceiling is a least-recently-used evictor, so a full
cache drops the oldest audio rather than refusing the new track.

Downloads are **not** a cache: they live in their own folder with no evictor, because "saved"
has to mean saved — and the player reads them straight off disk instead of copying the same
bytes into the cache.

**YouTube Music, wired in**
A hand-written InnerTube client (`data/YtMusic.kt`) talks to `music.youtube.com` directly:

| Call | Endpoint | Result |
|---|---|---|
| `search(q)` | `/youtubei/v1/search` | songs, albums, artists, playlists — classified from the result's own type token |
| `suggestions(q)` | `suggestqueries-clients6` | type-ahead while you type |
| `home()` | `browse · FEmusic_home` | real "Quick picks" + editorial carousels |
| `explore()` | `browse · FEmusic_explore` | new albums, 32 mood tiles, trending list |
| `charts()` | `browse · FEmusic_charts` | top artists, top videos, trending playlists |
| `browseShelves(id, params)` | `browse · any` | mood / genre pages |
| `collection(id)` | `browse` | album / artist / playlist pages with full track lists |
| `radio(videoId)` | `next · RDAMVM…` | the real automix queue — up to 50 tracks for autoplay |

Stream URLs are resolved by `data/YtStream.kt`, which tries the NewPipe-style extractor
first (handles signature deciphering) and then falls back through four InnerTube clients
(`ANDROID_VR → ANDROID_MUSIC → IOS → MWEB`).

**Playback**
Media3 ExoPlayer behind a `MediaSessionService` — background playback, lock-screen and
notification controls, audio focus, becoming-noisy handling. Queue with remove / clear /
reorder, shuffle, three repeat modes, seek, speed (0.5×–2×), sleep timer (5–60 min or
end-of-track), automatic advance.

The service is started on the first play (`ServiceLauncher`) and stopped with the last track:
without that the session the notification is built from does not exist at all, which is
indistinguishable from a broken notification. The card itself is a `MediaNotification.Provider`
with a RemoteViews layout — artwork, title, artist, elapsed/remaining and a real progress bar
redrawn once a second while playing — plus like / shuffle / repeat as session commands whose
icons carry their state. Headset and Bluetooth buttons reach the session through
`MediaButtonReceiver`, and Settings → *Playback controls* reports the four things that can
silence the card (permission, channel, service, battery rule) instead of guessing.

**Autoplay radio**
When the queue runs dry, `Playback` asks `YtMusic.radio()` for the current track's automix and
keeps playing — no silence, no repeats. "Start radio" in the track sheet seeds a station on
demand. Toggleable in Settings.

**Equaliser**
A real one, on the platform audio effects: `Equalizer` + `BassBoost` + `LoudnessEnhancer`
attached to ExoPlayer's own audio session. Band count and centre frequencies are read from the
device DSP, so a 5-band phone shows five sliders and a 10-band phone shows ten. Presets (Flat,
Bass, Vocal, Rock, Electronic, Podcast) are interpolated onto whatever bands exist.

**Downloads**
Any track can be saved to app-private storage. The player prefers the local file over the
network automatically, downloads show progress, and the Downloads screen totals the space.
**Offline only** mode refuses to stream at all.

**Lyrics**
Multi-provider and fail-soft: LRCLIB exact match (synced LRC) → LRCLIB search →
[lyrics.ovh](https://lyrics.ovh) plain text. The current line is highlighted and the sheet
auto-scrolls; the player also shows a one-line lyric peek under the artwork.

**Library**
Likes, playlists, history and listening stats persist in `SharedPreferences`. Playlists can
be created, renamed, reordered and deleted. The Library has four tabs — Playlists, Liked,
Recent, Downloaded — plus shortcuts to Downloads and Stats.

**Search**
Live suggestions, filters (Top / Songs / Albums / Artists / Playlists), search history, and
voice search through the system recogniser.

**Backup**
The whole library exports to a readable JSON file through the system file picker, and imports
back with merge semantics. No cloud, no account server.

**Stats**
Listening time, play count, active days, a seven-day bar chart, and ranked bars for top
artists and top tracks. Counted on device only.

---

## Design

The visual language is deliberate and entirely custom — no Material template, no icon font.

| Token | Value | Use |
|---|---|---|
| `Ink.Void` | `#08070B` | app background |
| `Ink.Card` | `#1C1823` | surfaces, chips |
| `Ink.Lime` | `#D8F65C` | primary accent, progress, active states |
| `Ink.Lavender` | `#C6BEF2` | hero card, glow behind artwork |
| `Ink.Mint` | `#7FE3C0` | offline badges, verified |
| `Ink.Text` | `#F4F1E9` | primary type |

- **Type** — [Sora](https://fonts.google.com/specimen/Sora) for UI, [Instrument Serif](https://fonts.google.com/specimen/Instrument+Serif) for titles and lyrics.
- **Icons** — every glyph in `core/Glyphs.kt` is drawn with Canvas paths on a 24×24 grid. 52 icons, zero dependencies.
- **Ambient colour** — each track gets a stable hue derived from its id (`core/Ambient.kt`), used for the player wash, seek bar and mood tiles.
- **Player styles** — three layouts: rotating **Disc**, grooved **Vinyl** with a tone arm, and full-bleed **Card**.
- **Motion** — depth-aware screen transitions, eased fades, shimmer skeletons while loading, a floating glass nav pill with a lime indicator.

---

## Project layout

```
app/src/main/java/app/bunmusic/
├── BunApp.kt                 Application · Coil image loader · Store/Prefs/Auth/Playback init
├── MainActivity.kt           single activity, edge-to-edge
├── core/
│   ├── Theme.kt              palette, typography, Material 3 scheme
│   ├── Glyphs.kt             hand-drawn icon set
│   └── Ambient.kt            per-track colour derivation
├── data/
│   ├── Models.kt             Track / Collection / Shelf / Lyrics / Playlist / Mood
│   ├── Cache.kt              stream cache (LRU) + cover-art cache
│   ├── YtMusic.kt            InnerTube client (search, home, explore, charts, radio)
│   ├── YtStream.kt           stream resolution (extractor + 4 clients)
│   ├── LyricsApi.kt          LRCLIB + lyrics.ovh, LRC parser, cache
│   ├── Auth.kt               accounts: PBKDF2 email, OTP phone, Google tokens
│   ├── GoogleAuth.kt         Play services account picker + signing SHA-1
│   ├── Eq.kt                 equaliser, bass boost, loudness on the player session
│   ├── Backup.kt             library ⇄ JSON
│   ├── Store.kt              likes, playlists, history, stats — scoped per account
│   ├── Downloads.kt          offline download manager
│   └── Prefs.kt              quality, player style, glow, crossfade, EQ state…
├── player/
│   ├── Playback.kt           ExoPlayer singleton + queue, speed, sleep, autoplay radio
│   └── PlaybackService.kt    MediaSessionService
└── ui/
    ├── Root.kt               auth gate, routes, transitions, glass nav, mini player
    ├── AuthScreen.kt         email / phone / Google, OTP boxes, Google setup sheet
    ├── ProfileScreen.kt      identity, stats, switch, sign out, delete
    ├── EqualizerScreen.kt    live bands from the device DSP
    ├── Components.kt         artwork, rows, cards, chips, shimmer, seek bar
    ├── HomeScreen.kt
    ├── ExploreScreen.kt      moods, new releases, charts entry, mood pages
    ├── SearchScreen.kt       suggestions, history, filters, voice
    ├── LibraryScreen.kt      playlists / liked / recent / downloads
    ├── PlaylistScreen.kt     local playlist editing
    ├── DetailScreen.kt       album / artist / playlist / charts
    ├── StatsScreen.kt        stats + downloads screens
    ├── SettingsScreen.kt     account, playback, sound, appearance, data, about
    ├── Sheets.kt             track actions, playlist picker, sleep timer
    └── PlayerScreen.kt       full player, 3 styles, queue, lyrics, speed
```

---

## Build

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleRelease      # signed APK → app/build/outputs/apk/release/
./gradlew :app:testReleaseUnitTest  # live checks against YouTube Music + LRCLIB
```

The unit tests hit the real endpoints, so they need network access. They verify search,
home, album pages, Explore, Charts, mood pages and the automix radio queue.

---

## Honest limits

- **Streaming depends on the network you are on.** From a data-centre IP, YouTube answers
  `Sign in to confirm you're not a bot` for every client. On a normal phone connection the
  extractor path works; when it doesn't, the player shows the reason instead of hanging.
- **Accounts are device-local.** Passwords are hashed, but there is no server: a second phone
  needs its own sign-in, and library sync between devices is not implemented. Backup/restore
  is the bridge.
- **SMS delivery needs a gateway.** `Auth.sender` is a pluggable interface; with nothing
  plugged in, the code is shown in the UI so the flow stays usable and testable.
- **Google sign-in needs your OAuth client ID** (see above). Until then the button opens the
  setup sheet rather than failing silently.
- **Not implemented yet:** per-bun custom accents, Discord Rich Presence, Last.fm scrobbling, song recognition,
  synced group listening, word-by-word lyrics and Android Auto.
- **Debug builds** install as `app.bunmusic.debug`, so they can sit next to the release build.

---

## Changelog

**1.1.0** — on the home screen, and in one search box.

- **Four widgets.** A 4×2 card (cover, progress, elapsed/total, like, prev · play ·
  next, queue), a 2×2 cover, a 4×1 strip, and a 4×4 queue whose rows jump to the
  track. The 1.0.x widget was a single 4×1 bar with three buttons.
- **The widget wears the bun.** Match the current bun, or pin Midnight, Charcoal,
  Cloud, Pink, Nebula or smoked glass. Three corner shapes, and each element — progress,
  times, like, queue — can be switched off per widget. Long-press to reconfigure.
- **It resizes instead of clipping.** Below 118 dp the time row goes; below 96 dp
  the bar goes; below 200 dp wide the like and queue buttons go. A cell that is one
  row shorter than expected no longer cuts the card in half.
- **It survives a reboot.** Cover art is cached on disk under `cacheDir/widget_art`,
  because the launcher asks for a redraw before the phone has rejoined Wi-Fi.
- **Progress is a bitmap, not a ProgressBar.** RemoteViews cannot tint a ProgressBar
  below API 31 (`setProgressTintList` takes a `ColorStateList`, which `setInt` cannot
  pass), and the bar has to be lime on Midnight and blue on Cloud. Drawing 480 × 12 px
  is cheaper than fifteen tinted drawables and scales exactly.
- **The command deck — 115 commands.** Playback, queue, library, navigation,
  appearance, audio, data, widgets and system, all searchable from one field.
  Settings → Command deck, or the launcher shortcut.
- **A widget tap now arrives.** The app is `singleTop`, so a second tap went to
  `onNewIntent`, which was never overridden: extras were read once in `onCreate` and
  then ignored. Tapping the widget with the app open did nothing.
- **Quick Settings tile and four shortcuts** — play/pause that mirrors the session,
  plus now playing, shuffle likes, search and the deck.
- **No widgets, no cost.** With nothing placed, the twice-a-second playback tick
  skips the launcher instead of asking four providers whether they exist.

**1.0.12** — the bottom of the screen, fixed properly.

- **The playing bar was cut in half.** The light buns packed the now-playing strip
  (62 dp) and the tab bar (87 dp) into a hard-coded `height(122.dp)` box, so the
  bar was drawn straight over the bottom half of the strip — artwork and all.
  The bottom of the screen is a `Column` that is exactly as tall as its children,
  with the window inset applied once at the container.
- **Pause pauses.** The pill on the home hero card was decoration inside a card
  that opened the player. It toggles playback now; the card still opens it.
- **The heart saves.** In the mini player the heart filled while the song played
  and paused the track when tapped. It is a like button now.
- **No separator pointing at nothing.** `artist + " · " + album` printed
  "Mehul Mahesh & DJ Aynik ·" whenever YouTube Music returned no album.
- **A list, not a stray dot.** Rows without a number printed a lone "•" in the
  gutter, half a line below the title. Numbered lists are numbered, and the
  number sits on the title's first line.
- **A pill, not a stripe.** The tab bar caps at 560 dp, the strip at 780 dp.
- **Toasts measure the chrome** instead of assuming 120 dp.

**1.0.11** — it fits the screen it is on.

- **Three width classes.** Compact (< 600 dp), medium (600–839), expanded (≥ 840),
  plus a separate height class so a landscape phone is not treated as a small
  tablet. One `BunWindow` value, read through a composition local.
- **Navigation rail from 600 dp.** Same four destinations, same glyphs, same
  selected fill — moved to the edge a thumb is not holding. 84 dp of height goes
  back to the content.
- **Two-pane player from 840 dp in landscape.** Artwork left, controls right, the
  disc squared off the *height*. Portrait keeps one column, capped at 560 dp.
- **Landscape no longer clips.** The flexible spacers collapse to 8 dp on a short
  window and the artwork is height-capped, so the transport row stays on screen.
- **Reading columns are capped** at 720 / 1040 dp and centred — no 90-character
  song titles on a 12-inch tablet.
- **The page stopped redrawing at 2 Hz.** Screens used to receive the live
  `PlayerState`, so every visible screen recomposed twice a second for a
  `positionMs` it does not display. They get a derived, tick-free copy now.
- **Explore's mood grid** runs 2 / 3 / 4 tiles wide; carousel cards scale with the
  window instead of staying phone-sized.
- **Stable keys** on every list, so rows are reused rather than rebuilt.
- **Resizing is handled in place** — rotation, split screen and unfolding keep the
  stack, the queue and the scroll position.

**1.0.10** — a craft pass over all four buns, measured rather than eyeballed.

- **Captions were unreadable.** The `faint` tone sat at 2.0–2.9:1 in every bun
  while carrying timestamps, section overlines and hints. All three text tones
  per bun were recomputed; body, secondary and caption now clear 4.5:1 on the
  page *and* on a card, and a unit test enforces it.
- **No pure-white page.** Charcoal painted `#FFFFFF` behind `#FFFFFF` cards, so
  a card had no edge. The page is a soft grey now, matching Cloud and Pink.
- **Accent as text.** Cloud's blue and Pink's rose were 4.2:1 at 11 sp. Both are
  one step deeper; fills and hero gradients keep the brighter originals.
- **Touch targets.** Row actions were 30–40 dp hit areas. The glyph is unchanged;
  the box reserves 48 dp.
- **Tap the waveform.** Charcoal and Pink could only scrub by dragging.
- **Explore admits failure** instead of rendering a near-empty page.
- **Three real weights.** `Medium` was mapped onto the semibold file, so Medium
  and SemiBold were the same weight; hierarchy had collapsed into one tone.
- **Type floor.** Nothing renders below 10 sp; all-caps labels carry 0.06–0.1 em.

**1.0.9** — Settings opens again. 1.0.7 handed the adaptive-icon XML to `painterResource`,
which resolves to an `AdaptiveIconDrawable` that Compose cannot paint; the Buns row now
composes the icon's background and foreground layers instead. Plus a fourth bun,
**Pink Bun**: blush paper, deep rose, Sora, a waveform seek bar, and its own launcher icon
cut from the pink artwork. Every screen, row, setting and shortcut stays where it was.

**1.0.8** — a third bun, **Cloud Blue**: chalk-white surfaces, electric blue, Sora throughout.
Its own launcher icon (the bun in headphones, cut from the supplied artwork into adaptive,
legacy and round layers at every density), a blue hero card, a blue play disc, a blue nav
pill, and a hairline seek bar with a knob instead of Charcoal's waveform. Every screen, row
and setting stays exactly where it was.

**1.0.7** — one launcher icon per bun, swapped through activity-aliases and re-pointed on
every cold start so an app update cannot leave the wrong face on the home screen.

**1.0.6** — the bun gets a face: a new app icon, a themed monochrome layer, and a matching
status-bar icon.

**1.0.5** — the cache is keyed on the song rather than the signed URL, so a finished track
really does play with the internet off; whole-track prefetch, bigger buffers, a home-screen
widget, 1080 px artwork, a sleep fade, artist-aware shuffle and skip-silence.

**1.0.4** — bun switch fixed (the choice is committed to disk before the restart), bundled
demo audio removed (3.6 MB lighter), stream cache with an LRU ceiling and a size readout,
cover-art cache with its own ceiling, both clearable from Storage.

**1.0.3** — the Buns skin system with a second full bun (Charcoal), Hotlist chart tab,
drag-to-reorder queue with "save queue as playlist", lyrics offset, haptics, About screen,
Inter bundled, every hard-coded colour folded into the palette.

**1.0.2** — accounts (email, phone, Google), per-account libraries, profile screen, equaliser
with bass boost, autoplay radio from YouTube's automix, backup/restore, offline-only mode,
voice search, 11 new hand-drawn icons.

**1.0.1** — Explore, Charts, mood pages, sleep timer, playback speed, queue editing, stats.

**1.0.0** — first end-to-end build: bundled demo catalog, YouTube search, downloads, lyrics.

---

## Credits

- Stream extraction: [BravePipeExtractor](https://github.com/maxrave-dev/BravePipeExtractor) (Apache-2.0), used as a library.
- Lyrics: [LRCLIB](https://lrclib.net) and [lyrics.ovh](https://lyrics.ovh).
- Fonts: Sora and Instrument Serif (SIL Open Font License).
- The 1.0.0 demo tracks were original recordings made for this project; they were removed in
  1.0.4 so the APK ships no audio at all.

BunMusic is an independent project by **Iota Coder**. It is not affiliated with, endorsed by,
or connected to YouTube or Google.
