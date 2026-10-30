# BunMusic

A quiet music player for Android — built from scratch in Kotlin + Jetpack Compose.
Four skins, "buns", and you pick which one the app wears.
Nothing is bundled: every track comes from YouTube Music, and anything you like can be
saved offline.

```
package   app.bunmusic
version   1.0.9 (10)
minSdk    26  ·  target/compile 37
author    Iota Coder · iota14934@gmail.com
```

---

## What it does

**Buns**
Four complete skins, switched in Settings → Buns. A bun is not a tint: it replaces the
palette, the typeface, the corner radii, the shape of the bottom bar, whether a list shows
cover art or a track number, and whether the seek bar is a line or a waveform.

| Bun | What it is |
|---|---|
| **Midnight Lime** | Ink black, acid lime, lavender hero, Sora + Instrument Serif. The house bun. |
| **Charcoal** | Paper white, charcoal ink, Inter throughout, 24 dp cards, waveform seek bar, white nav bar with a dark active pill, dark mini player. |
| **Cloud Blue** | Chalk white and electric blue, Sora throughout, blue hero gradient, hairline seek bar with a knob, blue active nav pill, navy mini player. Its launcher icon is the bun in headphones. |
| **Pink Bun** | Blush paper and deep rose, Sora throughout, pink hero gradient, waveform seek bar, rose active nav pill, plum mini player. Its launcher icon is the bun with the music note. |

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
