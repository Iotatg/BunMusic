# BunMusic

A dark, quiet music player for Android — built from scratch in Kotlin + Jetpack Compose.
Six original tracks ship inside the APK, YouTube Music is wired in for everything else,
and anything you like can be saved offline.

```
package   app.bunmusic
version   1.0.1 (2)
minSdk    26  ·  target/compile 37
author    Iota Coder · iota14934@gmail.com
```

---

## What it does

**Offline, always**
Six instrumental pieces and their cover art are bundled in `assets/`. They play with no
network, no login, no ads — the app is useful the second it is installed.

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

Stream URLs are resolved by `data/YtStream.kt`, which tries the NewPipe-style extractor
first (handles signature deciphering) and then falls back through four InnerTube clients
(`ANDROID_VR → ANDROID_MUSIC → IOS → MWEB`).

**Playback**
Media3 ExoPlayer behind a `MediaSessionService` — background playback, lock-screen and
notification controls, audio focus, becoming-noisy handling. Queue with remove / clear /
reorder, shuffle, three repeat modes, seek, speed (0.5×–2×), sleep timer (5–60 min or
end-of-track), automatic advance.

**Downloads**
Any track can be saved to app-private storage. The player prefers the local file over the
network automatically, downloads show progress, and the Downloads screen totals the space.

**Lyrics**
Multi-provider and fail-soft: LRCLIB exact match (synced LRC) → LRCLIB search →
[lyrics.ovh](https://lyrics.ovh) plain text. The current line is highlighted and the sheet
auto-scrolls; the player also shows a one-line lyric peek under the artwork.

**Library**
Likes, playlists, history and listening stats persist in `SharedPreferences`. Playlists can
be created, renamed, reordered and deleted. The Library has four tabs — Playlists, Liked,
Recent, Downloaded — plus shortcuts to Downloads and Stats.

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
| `Ink.Mint` | `#7FE3C0` | offline badges |
| `Ink.Text` | `#F4F1E9` | primary type |

- **Type** — [Sora](https://fonts.google.com/specimen/Sora) for UI, [Instrument Serif](https://fonts.google.com/specimen/Instrument+Serif) for titles and lyrics.
- **Icons** — every glyph in `core/Glyphs.kt` is drawn with Canvas paths on a 24×24 grid. 41 icons, zero dependencies.
- **Ambient colour** — each track gets a stable hue derived from its id (`core/Ambient.kt`), used for the player wash, seek bar and mood tiles.
- **Player styles** — three layouts: rotating **Disc**, grooved **Vinyl** with a tone arm, and full-bleed **Card**.
- **Motion** — depth-aware screen transitions, spring-free eased fades, shimmer skeletons while loading, a floating glass nav pill with a lime indicator.

---

## Project layout

```
app/src/main/java/app/bunmusic/
├── BunApp.kt                 Application · Coil image loader
├── MainActivity.kt           single activity, edge-to-edge
├── core/
│   ├── Theme.kt              palette, typography, Material 3 scheme
│   ├── Glyphs.kt             hand-drawn icon set
│   └── Ambient.kt            per-track colour derivation
├── data/
│   ├── Models.kt             Track / Collection / Shelf / Lyrics / Playlist / Mood
│   ├── Catalog.kt            the six bundled tracks + playlists
│   ├── YtMusic.kt            InnerTube client (search, home, explore, charts, browse)
│   ├── YtStream.kt           stream resolution (extractor + 4 clients)
│   ├── LyricsApi.kt          LRCLIB + lyrics.ovh, LRC parser, cache
│   ├── Store.kt              likes, playlists, history, stats, search history
│   ├── Downloads.kt          offline download manager
│   └── Prefs.kt              quality, player style, glow, crossfade…
├── player/
│   ├── Playback.kt           ExoPlayer singleton + queue, speed, sleep timer
│   └── PlaybackService.kt    MediaSessionService
└── ui/
    ├── Root.kt               routes, animated transitions, glass nav, mini player
    ├── Components.kt         artwork, rows, cards, chips, shimmer, seek bar
    ├── HomeScreen.kt
    ├── ExploreScreen.kt      moods, new releases, charts entry, mood pages
    ├── SearchScreen.kt       suggestions, history, filters
    ├── LibraryScreen.kt      playlists / liked / recent / downloads
    ├── PlaylistScreen.kt     local playlist editing
    ├── DetailScreen.kt       album / artist / playlist / charts
    ├── StatsScreen.kt        stats + downloads screens
    ├── SettingsScreen.kt     playback, appearance, storage, about
    ├── Sheets.kt             track actions, playlist picker, sleep timer
    └── PlayerScreen.kt       full player, 3 styles, queue, lyrics, speed
```

---

## Build

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleRelease      # signed APK → app/build/outputs/apk/release/
./gradlew :app:testDebugUnitTest    # live checks against YouTube Music + LRCLIB
```

The unit tests hit the real endpoints, so they need network access. They verify search,
home, album pages, Explore, Charts and mood pages.

---

## Honest limits

- **Streaming depends on the network you are on.** From a data-centre IP, YouTube answers
  `Sign in to confirm you're not a bot` for every client. On a normal phone connection the
  extractor path works; when it doesn't, the player shows the reason instead of hanging.
- **Not implemented yet:** Discord Rich Presence, Last.fm scrobbling, song recognition,
  synced group listening, account login, equaliser, word-by-word lyrics, Android Auto and
  the home-screen widget. They are on the list, not in this build.
- **No account sync.** Likes, playlists and history live on the device.
- **Debug builds** install as `app.bunmusic.debug`, so they can sit next to the release build.

---

## Credits

- Stream extraction: [BravePipeExtractor](https://github.com/maxrave-dev/BravePipeExtractor) (Apache-2.0), used as a library.
- Lyrics: [LRCLIB](https://lrclib.net) and [lyrics.ovh](https://lyrics.ovh).
- Fonts: Sora and Instrument Serif (SIL Open Font License).
- Bundled audio and artwork were generated for this project — no third-party samples.

BunMusic is an independent project by **Iota Coder**. It is not affiliated with, endorsed by,
or connected to YouTube or Google.
