# BunMusic

A dark, quiet music player for Android — built from scratch in Kotlin + Jetpack Compose.
Six original tracks ship inside the APK, and YouTube Music is wired in for everything else.

```
package   app.bunmusic
version   1.0.0 (1)
minSdk    26  ·  target/compile 37
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
| `home()` | `/youtubei/v1/browse` (`FEmusic_home`) | the real "Quick picks" + editorial carousels |
| `collection(id)` | `/youtubei/v1/browse` | album / artist / playlist pages with full track lists |

Stream URLs are resolved by `data/YtStream.kt`, which tries the NewPipe-style extractor
first (handles signature deciphering) and then falls back through four InnerTube clients
(`ANDROID_VR → ANDROID_MUSIC → IOS → MWEB`).

**Playback**
Media3 ExoPlayer behind a `MediaSessionService` — background playback, lock-screen and
notification controls, audio focus, becoming-noisy handling. Queue, shuffle, three repeat
modes, seek, next/previous, and automatic advance.

**Lyrics**
Synced, from [LRCLIB](https://lrclib.net) — the current line is highlighted and the sheet
auto-scrolls while the track plays. Falls back to plain text when only unsynced lyrics exist.

**Library**
Likes and recently-played persist in `SharedPreferences`; playlists, liked and recent are
three tabs in one screen.

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
- **Icons** — every glyph in `core/Glyphs.kt` is drawn with Canvas paths on a 24×24 grid. 31 icons, zero dependencies.
- **Player** — circular artwork that rotates only while playing, radial lavender glow, 74 dp lime transport button.
- **Mini player** — glassy pill with a 2.5 dp progress hairline, sitting above the nav bar.

---

## Project layout

```
app/src/main/java/app/bunmusic/
├── BunApp.kt                 Application · Coil image loader
├── MainActivity.kt           single activity, edge-to-edge
├── core/
│   ├── Theme.kt              palette, typography, Material 3 scheme
│   └── Glyphs.kt             hand-drawn icon set
├── data/
│   ├── Models.kt             Track / Collection / Shelf / Lyrics
│   ├── Catalog.kt            the six bundled tracks + playlists
│   ├── YtMusic.kt            InnerTube client (search, home, browse)
│   ├── YtStream.kt           stream resolution (extractor + 4 clients)
│   ├── LyricsApi.kt          LRCLIB + LRC parser
│   └── Store.kt              likes & recents
├── player/
│   ├── Playback.kt           ExoPlayer singleton + queue state
│   └── PlaybackService.kt    MediaSessionService
└── ui/
    ├── Root.kt               routes, bottom bar, mini player
    ├── Components.kt         artwork, rows, cards, seek bar
    ├── HomeScreen.kt
    ├── SearchScreen.kt
    ├── LibraryScreen.kt
    ├── DetailScreen.kt       album / artist / playlist
    └── PlayerScreen.kt       full player, queue sheet, lyrics sheet
```

---

## Build

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleRelease      # signed APK → app/build/outputs/apk/release/
./gradlew :app:testDebugUnitTest    # live checks against YouTube Music + LRCLIB
```

The unit tests hit the real endpoints, so they need network access. They verify that search
returns songs, the home feed returns shelves, an album page returns tracks, and that stream
resolution either succeeds or fails with a clear, catchable error.

---

## Honest limits

- **Streaming depends on the network you are on.** From a data-centre IP, YouTube answers
  `Sign in to confirm you're not a bot` for every client. On a normal phone connection the
  extractor path works; when it doesn't, the player shows the reason instead of hanging.
- **No offline downloads** of YouTube tracks. Only the six bundled tracks are offline.
- **No account sync.** Likes and history live on the device.
- **Debug builds** install as `app.bunmusic.debug`, so they can sit next to the release build.

---

## Credits

- Stream extraction: [BravePipeExtractor](https://github.com/maxrave-dev/BravePipeExtractor) (Apache-2.0), used as a library.
- Lyrics: [LRCLIB](https://lrclib.net).
- Fonts: Sora and Instrument Serif (SIL Open Font License).
- Bundled audio and artwork were generated for this project — no third-party samples.

BunMusic is an independent project by **Iota Coder**. It is not affiliated with, endorsed by,
or connected to YouTube or Google.
