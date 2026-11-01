package app.bunmusic.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import app.bunmusic.core.BunSkin
import app.bunmusic.core.AppVersion
import app.bunmusic.core.G
import app.bunmusic.core.Haptics
import app.bunmusic.core.Skin
import app.bunmusic.data.Auth
import app.bunmusic.data.Cache
import app.bunmusic.data.Downloads
import app.bunmusic.data.Eq
import app.bunmusic.data.Library
import app.bunmusic.data.Prefs
import app.bunmusic.data.Scrobble
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import app.bunmusic.player.Playback
import app.bunmusic.widget.WidgetArt
import app.bunmusic.widget.WidgetKind
import app.bunmusic.widget.Widgets

/** What a command is allowed to do. Implemented once, by the shell. */
interface CommandHost {
    val context: Context
    fun go(route: Route)
    fun openPlayer()
    fun openQueue()
    fun toast(message: String)
    fun close()
}

/**
 * One thing the app can do, addressable by name.
 *
 * Commands are the app's verbs. The palette is only the first caller — a
 * shortcut, a widget button or a future automation can build the same list and
 * run the same lambdas, because nothing here reaches into a screen.
 */
data class Command(
    val id: String,
    val title: String,
    val hint: String,
    val group: String,
    val glyph: G,
    val keywords: String = "",
    val run: () -> Unit,
)

/**
 * The catalogue.
 *
 * Deliberately flat and explicit rather than reflective: every entry is a real
 * action with a real effect, so the list doubles as an inventory of the app.
 */
fun buildCommands(host: CommandHost): List<Command> {
    val out = ArrayList<Command>(140)

    fun add(
        group: String,
        id: String,
        title: String,
        hint: String = "",
        glyph: G = G.Sparkle,
        keywords: String = "",
        run: () -> Unit,
    ) {
        out += Command(id, title, hint, group, glyph, keywords, run)
    }

    fun tab(route: Route, title: String, hint: String, glyph: G) =
        add("Navigate", "go.${routeName(route)}", title, hint, glyph) { host.go(route) }

    // ------------------------------------------------------------- playback
    add("Playback", "play.toggle", "Play or pause", "Space", G.Play) { Playback.toggle() }
    add("Playback", "play.next", "Next track", "", G.Next) { Playback.next() }
    add("Playback", "play.prev", "Previous track", "", G.Prev) { Playback.previous() }
    add("Playback", "play.restart", "Restart this track", "From 0:00", G.Refresh) { Playback.restart() }
    add("Playback", "play.stop", "Stop playback", "Clears the queue", G.Close) { Playback.stop() }
    add("Playback", "play.fwd10", "Forward 10 seconds", "", G.Forward, "seek jump") { Playback.seekBy(10_000) }
    add("Playback", "play.back10", "Back 10 seconds", "", G.Back, "seek jump") { Playback.seekBy(-10_000) }
    add("Playback", "play.fwd30", "Forward 30 seconds", "", G.Forward, "seek jump") { Playback.seekBy(30_000) }
    add("Playback", "play.back30", "Back 30 seconds", "", G.Back, "seek jump") { Playback.seekBy(-30_000) }
    add("Playback", "play.shuffle", "Toggle shuffle", "", G.Shuffle) { Playback.toggleShuffle() }
    add("Playback", "play.repeat.off", "Repeat off", "", G.Repeat) {
        if (Playback.state.value.repeat != 0) Playback.cycleRepeat()
        host.toast("Repeat off")
    }
    add("Playback", "play.repeat.all", "Repeat the queue", "", G.Repeat) {
        while (Playback.state.value.repeat != 1) Playback.cycleRepeat()
        host.toast("Repeating the queue")
    }
    add("Playback", "play.repeat.one", "Repeat this track", "", G.RepeatOne) {
        while (Playback.state.value.repeat != 2) Playback.cycleRepeat()
        host.toast("Repeating this track")
    }
    add("Playback", "play.like", "Like or unlike this track", "", G.Heart) {
        val liked = Playback.likeCurrent()
        host.toast(if (liked) "Added to liked songs" else "Removed from liked songs")
    }
    add("Playback", "play.radio", "Start a radio from this track", "Keeps going", G.Wave) {
        Playback.state.value.track?.let { Playback.startRadio(it) }
    }
    add("Playback", "play.speed.up", "Speed up", "", G.Speed, "faster tempo") {
        Playback.setSpeed(Playback.state.value.speed + 0.25f)
    }
    add("Playback", "play.speed.down", "Slow down", "", G.Speed, "slower tempo") {
        Playback.setSpeed(Playback.state.value.speed - 0.25f)
    }
    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { speed ->
        add("Playback", "play.speed.$speed", "Speed ${trimSpeed(speed)}×", "Tempo", G.Speed, "rate") {
            Playback.setSpeed(speed)
            host.toast("Speed ${trimSpeed(speed)}×")
        }
    }
    listOf(5, 10, 15, 30, 45, 60).forEach { minutes ->
        add("Playback", "play.sleep.$minutes", "Sleep in $minutes minutes", "Fades out", G.Moon, "timer") {
            Playback.sleepIn(minutes)
            host.toast("Sleeping in $minutes min")
        }
    }
    add("Playback", "play.sleep.track", "Sleep at the end of this track", "", G.Moon, "timer") {
        Playback.sleepAtEndOfTrack()
        host.toast("Sleeping after this track")
    }
    add("Playback", "play.sleep.cancel", "Cancel the sleep timer", "", G.Close, "timer") {
        Playback.cancelSleep()
        host.toast("Sleep timer off")
    }
    add("Playback", "play.skip.on", "Skip silent gaps", "", G.Forward, "silence") {
        Prefs.update { it.copy(skipSilence = true) }
        Playback.applySkipSilence(true)
        host.toast("Skipping silence")
    }
    add("Playback", "play.skip.off", "Play silent gaps", "", G.Wave, "silence") {
        Prefs.update { it.copy(skipSilence = false) }
        Playback.applySkipSilence(false)
        host.toast("Silence is back")
    }

    // ---------------------------------------------------------------- queue
    add("Queue", "queue.open", "Open the queue", "", G.Queue) { host.openQueue() }
    add("Queue", "queue.clear", "Clear the queue", "Keeps this track", G.Trash) {
        Playback.clearQueue()
        host.toast("Queue cleared")
    }
    add("Queue", "queue.save", "Save the queue as a playlist", "", G.Plus) {
        val name = Playback.saveQueueAsPlaylist()
        host.toast(if (name.isBlank()) "Nothing to save" else "Saved as $name")
    }
    add("Queue", "queue.shuffle", "Shuffle what is left", "Keeps this track", G.Shuffle) {
        Playback.shuffleQueueOrder()
        host.toast("Up next shuffled")
    }
    add("Queue", "queue.remove", "Remove this track from the queue", "", G.Trash) {
        Playback.removeCurrent()
        host.toast("Removed")
    }
    add("Queue", "queue.up", "Move this track up the queue", "", G.Back) { Playback.moveCurrent(-1) }
    add("Queue", "queue.down", "Move this track down the queue", "", G.Forward) { Playback.moveCurrent(1) }

    // -------------------------------------------------------------- library
    add("Library", "lib.play.liked", "Play liked songs", "", G.Heart) {
        val tracks = Store.likedTracks()
        if (tracks.isEmpty()) host.toast("No liked songs yet") else Playback.play(tracks, 0)
    }
    add("Library", "lib.shuffle.liked", "Shuffle liked songs", "", G.Shuffle) {
        val tracks = Store.likedTracks()
        if (tracks.isEmpty()) host.toast("No liked songs yet") else Playback.shufflePlay(tracks)
    }
    add("Library", "lib.play.recent", "Play recently played", "", G.Clock) {
        val tracks = Store.recent(50)
        if (tracks.isEmpty()) host.toast("Nothing played yet") else Playback.play(tracks, 0)
    }
    add("Library", "lib.shuffle.recent", "Shuffle recently played", "", G.Shuffle) {
        val tracks = Store.recent(50)
        if (tracks.isEmpty()) host.toast("Nothing played yet") else Playback.shufflePlay(tracks)
    }
    add("Library", "lib.play.downloads", "Play downloads", "Offline", G.Download) {
        val tracks = downloadedTracks()
        if (tracks.isEmpty()) host.toast("No downloads") else Playback.play(tracks, 0)
    }
    add("Library", "lib.shuffle.downloads", "Shuffle downloads", "Offline", G.Shuffle) {
        val tracks = downloadedTracks()
        if (tracks.isEmpty()) host.toast("No downloads") else Playback.shufflePlay(tracks)
    }
    add("Library", "lib.clear.queries", "Clear search history", "", G.Search) {
        Store.clearQueries()
        host.toast("Search history cleared")
    }
    add("Library", "lib.clear.history", "Clear play history", "Keeps likes and playlists", G.Clock) {
        Store.clearHistory()
        host.toast("Play history cleared")
    }
    add("Library", "lib.clear.downloads", "Remove all downloads", "", G.Trash) {
        Downloads.clearAll()
        host.toast("Downloads removed")
    }

    // ------------------------------------------------------------- navigate
    tab(Route.Home, "Go home", "The shelf", G.Home)
    tab(Route.Explore, "Explore", "Moods and new releases", G.Compass)
    tab(Route.Search, "Search", "Find a song", G.Search)
    tab(Route.Library, "Library", "Likes, playlists, history", G.Library)
    tab(Route.Hotlist, "Hotlist", "What is climbing", G.Flame)
    tab(Route.Settings, "Settings", "", G.Gear)
    tab(Route.Stats, "Listening stats", "Minutes and top artists", G.Chart)
    tab(Route.YearReview, "Year in review", "Your year as a reel", G.Sparkle)
    tab(Route.Downloads, "Downloads", "", G.Download)
    tab(Route.Equalizer, "Equaliser", "", G.Sliders)
    tab(Route.Buns, "Buns", "Change the skin", G.Grid)
    tab(Route.Profile, "Profile", "Account", G.User)
    tab(Route.About, "About BunMusic", "What changed", G.Sparkle)

    // Sharing is a command too: the image is drawn from the same summary the
    // screen shows, so there is no second path to keep in sync.
    add("Share", "share.year", "Share my year", "A 1080 × 1350 image", G.Share, "wrapped recap receipt") {
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val summary = app.bunmusic.data.Wrapped.summarise(
            Store.events(), Store::track, year, app.bunmusic.data.Listening.zoneOffset(),
        )
        if (!summary.hasStory) {
            host.toast("Not enough listening yet")
        } else {
            host.close()
            ShareCard.shareWrapped(host.context, summary)
        }
    }

    // The lock screen is the one part of the app it cannot draw for itself:
    // whether it appears at all is a system permission, not a setting of ours.
    add(
        "System", "sys.notifications", "Lock screen & notifications",
        "Opens the system page", G.Bell, "media controls bar notification permission",
    ) {
        val context = host.context
        val opened = runCatching {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (opened) host.close() else host.toast("Could not open system settings")
    }

    // ----------------------------------------------------------- appearance
    BunSkin.entries.forEach { bun ->
        add("Appearance", "skin.${bun.id}", "Wear ${bun.title}", bun.tagline, G.Grid, "theme skin bun") {
            Skin.apply(host.context, bun)
        }
    }
    Prefs.PlayerStyle.entries.forEach { style ->
        add("Appearance", "style.${style.name}", "Player style: ${style.label}", "", G.Disc, "cover disc vinyl card") {
            Prefs.update { it.copy(playerStyle = style) }
            host.toast("Player style: ${style.label}")
        }
    }
    Prefs.Glow.entries.forEach { glow ->
        add("Appearance", "glow.${glow.name}", "Ambient glow: ${glow.label}", "", G.Sparkle, "aura") {
            Prefs.update { it.copy(glow = glow) }
            host.toast("Glow: ${glow.label}")
        }
    }
    add("Appearance", "haptics.on", "Turn haptics on", "", G.Phone) {
        Prefs.update { it.copy(haptics = true) }
        Haptics.enabled = true
        host.toast("Haptics on")
    }
    add("Appearance", "haptics.off", "Turn haptics off", "", G.Phone) {
        Prefs.update { it.copy(haptics = false) }
        Haptics.enabled = false
        host.toast("Haptics off")
    }
    add(
        "Appearance", "artcolour", "Colour from the cover", "Toggle", G.Disc,
        "palette artwork tint",
    ) {
        val next = !Prefs.settings.value.artColour
        Prefs.update { it.copy(artColour = next) }
        host.toast(if (next) "Tint follows the artwork" else "Tint is stable per track")
    }

    // ---------------------------------------------------------------- audio
    Prefs.Quality.entries.forEach { q ->
        add("Audio", "audio.quality.${q.name}", "Stream at ${q.label}", q.note, G.Wave, "bitrate") {
            Prefs.update { it.copy(quality = q) }
            host.toast("Streaming at ${q.label}")
        }
    }
    add("Audio", "audio.normalise.on", "Volume normalisation on", "", G.Sliders) {
        Prefs.update { it.copy(normalize = true) }
        Eq.setNormalisation(true)
        host.toast("Normalisation on")
    }
    add("Audio", "audio.normalise.off", "Volume normalisation off", "", G.Sliders) {
        Prefs.update { it.copy(normalize = false) }
        Eq.setNormalisation(false)
        host.toast("Normalisation off")
    }
    add("Audio", "audio.eq.on", "Turn the equaliser on", "", G.Sliders) {
        Prefs.update { it.copy(eqEnabled = true) }
        Eq.setEnabled(true)
        host.toast("Equaliser on")
    }
    add("Audio", "audio.eq.off", "Turn the equaliser off", "", G.Sliders) {
        Prefs.update { it.copy(eqEnabled = false) }
        Eq.setEnabled(false)
        host.toast("Equaliser off")
    }
    Eq.presets.forEach { preset ->
        add("Audio", "audio.eq.$preset", "Equaliser: $preset", "", G.Sliders, "preset eq") {
            Prefs.update { it.copy(eqEnabled = true, eqPreset = preset) }
            Eq.setEnabled(true)
            Eq.setPreset(preset)
            host.toast("EQ: $preset")
        }
    }
    listOf(0, 300, 600, 1000).forEach { strength ->
        add("Audio", "audio.bass.$strength", if (strength == 0) "Bass boost off" else "Bass boost ${strength / 10}%", "", G.Wave, "low end") {
            Prefs.update { it.copy(bassBoost = strength) }
            Eq.setBass(strength)
            host.toast(if (strength == 0) "Bass boost off" else "Bass boost ${strength / 10}%")
        }
    }

    // ----------------------------------------------------------------- data
    add("Data", "data.offline.on", "Offline mode on", "Downloads only", G.Offline) {
        Prefs.update { it.copy(offlineOnly = true) }
        host.toast("Offline mode on")
    }
    add("Data", "data.offline.off", "Offline mode off", "", G.Offline) {
        Prefs.update { it.copy(offlineOnly = false) }
        host.toast("Offline mode off")
    }
    listOf(0, 256, 512, 1024, -1).forEach { mb ->
        val label = when (mb) {
            0 -> "off"
            -1 -> "unlimited"
            1024 -> "1 GB"
            else -> "$mb MB"
        }
        add("Data", "data.cache.$mb", "Stream cache: $label", "", G.Download, "storage size") {
            Prefs.update { it.copy(cacheLimitMb = mb) }
            Cache.onLimitChanged(playbackActive = Playback.state.value.hasTrack)
            host.toast("Cache: $label")
        }
    }
    add("Data", "data.cache.clear.stream", "Clear the stream cache", "", G.Trash) {
        val wasPlaying = Playback.state.value.hasTrack
        if (wasPlaying) Playback.stop()
        Cache.clearStream()
        host.toast(if (wasPlaying) "Stopped · stream cache cleared" else "Stream cache cleared")
    }
    add("Data", "data.cache.clear.images", "Clear the image cache", "", G.Trash) {
        Cache.clearImages()
        host.toast("Image cache cleared")
    }
    add("Data", "data.prefetch.on", "Cache the next songs as you play", "", G.Download) {
        Prefs.update { it.copy(prefetchAhead = true) }
        host.toast("Prefetch on")
    }
    add("Data", "data.prefetch.off", "Stop caching ahead", "", G.Download) {
        Prefs.update { it.copy(prefetchAhead = false) }
        host.toast("Prefetch off")
    }

    // -------------------------------------------------------------- widgets
    WidgetKind.entries.forEach { kind ->
        add("Widgets", "widget.pin.${kind.name}", "Add the ${kindLabel(kind)} widget", "Home screen", G.Grid, "widget launcher") {
            if (Widgets.pin(host.context, kind)) {
                host.toast("Confirm it on your home screen")
            } else {
                host.toast("Your launcher does not support pinning — add it from the widget list")
            }
        }
    }
    add("Widgets", "widget.refresh", "Redraw every widget", "", G.Refresh, "widget update") {
        Widgets.refresh(host.context)
        host.toast("Widgets redrawn")
    }
    add("Widgets", "widget.settings", "Widget settings", "Style, corners, what shows", G.Grid, "widget") {
        host.go(Route.Widgets)
    }
    add("Widgets", "widget.art.clear", "Clear the widget art cache", "", G.Trash, "widget images") {
        WidgetArt.clear(host.context)
        Widgets.refresh(host.context)
        host.toast("Widget art cache cleared")
    }

    // ------------------------------------------------------------- listening
    add("Playback", "play.dislike", "Not for me", "Skips it and remembers", G.ThumbDown, "dislike skip ban") {
        val now = Playback.dislikeCurrent()
        host.toast(if (now) "Skipped — fewer like this" else "Removed from the dislike list")
    }
    add("Playback", "play.radio.here", "Radio from this song", "Endless station", G.Wave, "station autoplay") {
        val t = Playback.state.value.track
        if (t == null) host.toast("Nothing playing") else Playback.startRadio(t)
    }
    add("Playback", "play.resume", "Pick up where you left off", "Restores the last queue", G.Clock, "session resume") {
        if (Playback.state.value.hasTrack) host.toast("Already playing something") else {
            Playback.restoreSession()
            host.toast(if (Playback.state.value.hasTrack) "Restored" else "Nothing to resume")
        }
    }

    add("Library", "lib.most", "Play your most played", "", G.Chart, "top heavy rotation") {
        val list = Library.rank(Store.playCounts(), Store.allTracks())
        if (list.isEmpty()) host.toast("Nothing counted yet") else Playback.play(list, 0)
    }
    add("Library", "lib.repeat", "Play what is on repeat", "This week", G.Refresh, "repeat") {
        val list = Library.onRepeat(Store.history())
        if (list.isEmpty()) host.toast("Nothing repeated yet") else Playback.play(list, 0)
    }
    add("Library", "lib.forgotten", "Play something forgotten", "Not touched in a month", G.Moon, "old") {
        val list = Library.forgotten(Store.likedTracks(), Store.history())
        if (list.isEmpty()) host.toast("Nothing forgotten — you are thorough") else Playback.play(list, 0)
    }
    // Nothing here may read the store while the catalogue is being built: the
    // unit tests build it without a Context, and a lateinit prefs blows up.
    add("Library", "lib.followed", "Followed artists", "Who you keep an eye on", G.Bell, "following") {
        host.toast(
            Store.follows().takeIf { it.isNotEmpty() }?.joinToString(", ")?.take(80)
                ?: "Not following anyone yet — open an artist and tap Follow"
        )
    }

    add("Data", "data.receipt", "Share a listening receipt", "As an image", G.Receipt, "stats image share") {
        ShareCard.share(host.context)
    }
    add("Data", "data.scrobble.on", "Turn scrobbling on", "ListenBrainz", G.Cloud) {
        if (Scrobble.token.isBlank()) {
            host.toast("Paste a token in Settings → Listening first")
        } else {
            Scrobble.setEnabled(true)
            host.toast("Scrobbling on")
        }
    }
    add("Data", "data.scrobble.off", "Turn scrobbling off", "", G.Cloud) {
        Scrobble.setEnabled(false)
        host.toast("Scrobbling off")
    }

    // -------------------------------------------------------------- downloads
    add("Data", "data.dl.pause", "Pause downloads", "Stops after the current bytes", G.Pause, "queue stop") {
        if (Downloads.queue.value.isEmpty()) {
            host.toast("Nothing in the download queue")
        } else {
            Downloads.pauseAll()
            host.toast("Downloads paused")
        }
    }
    add("Data", "data.dl.resume", "Resume downloads", "Picks the queue back up", G.Play, "queue start") {
        if (Downloads.queue.value.isEmpty()) {
            host.toast("Nothing in the download queue")
        } else {
            Downloads.resumeAll()
            host.toast("Downloads resumed")
        }
    }
    add("Data", "data.dl.clear", "Cancel every download", "Empties the queue", G.Close, "queue cancel") {
        val n = Downloads.queue.value.size
        Downloads.queue.value.forEach { Downloads.cancel(it.track.id) }
        host.toast(if (n == 0) "Queue was already empty" else "Cancelled $n")
    }
    add("Data", "data.dl.retry", "Retry failed downloads", "Tries them again", G.Refresh, "queue failed") {
        val n = Downloads.failedCount()
        Downloads.retryAllFailed()
        host.toast(if (n == 0) "Nothing had failed" else "Retrying $n")
    }
    add("Data", "data.dl.wifi", "Wi-Fi only downloads", "Toggle", G.Cloud, "mobile data wifi") {
        val next = !Prefs.settings.value.wifiOnly
        Prefs.update { it.copy(wifiOnly = next) }
        host.toast(if (next) "Downloads will wait for Wi-Fi" else "Downloads may use mobile data")
    }

    // ------------------------------------------------------------ discovery
    add("Discover", "disc.open", "Made for you", "Mixes, taste, time capsule", G.Sparkle, "daily mix recommendation") {
        host.go(Route.Discovery)
    }
    add("Discover", "disc.mix", "Play the first mix", "Whatever the top artist is", G.Play, "daily mix play") {
        val mixes = app.bunmusic.data.Discovery.mixes(
            Store.history(), Store.likedTracks(), Store.allTracks(), limit = 1,
        )
        if (mixes.isEmpty()) host.toast("Play a few songs first") else Playback.play(mixes.first().tracks, 0)
    }
    add("Discover", "disc.radio", "Start a radio from your best song", "Seeded by play count", G.Wave, "radio station") {
        val seed = app.bunmusic.data.Discovery.radioSeed(
            Store.likedTracks(), Store.history(), Playback.state.value.track?.id,
        )
        if (seed == null) host.toast("Nothing to seed a radio with") else Playback.startRadio(seed)
    }
    add("Discover", "disc.smartshuffle", "Smart shuffle", "Toggle artist spread and skip learning", G.Shuffle, "shuffle smart") {
        val next = !Prefs.settings.value.smartShuffle
        Prefs.update { it.copy(smartShuffle = next) }
        host.toast(if (next) "Smart shuffle on" else "Plain shuffle")
    }

    // ------------------------------------------------------------- queue
    add("Queue", "queue.shuffleup", "Shuffle what is next", "Leaves the current song alone", G.Shuffle, "queue shuffle") {
        Playback.shuffleUpcoming()
        host.toast("Up next shuffled")
    }
    add("Queue", "queue.clearplayed", "Clear recently played", "Drops the rows above the current song", G.Trash, "history queue") {
        Playback.clearPlayed()
        host.toast("Recently played cleared from the queue")
    }
    add("Queue", "queue.ab", "A–B loop", "Mark, mark, clear", G.Repeat, "repeat section loop") {
        Playback.abCycle()
        host.toast(if (Playback.state.value.abActive) "A–B loop set" else "A–B cleared")
    }

    // ---------------------------------------------------------- appearance
    add("Appearance", "look.motion", "Reduced motion", "Toggle transitions and the visualiser", G.Speed, "animation accessibility") {
        val next = !Prefs.settings.value.reducedMotion
        Prefs.update { it.copy(reducedMotion = next) }
        host.toast(if (next) "Motion reduced" else "Full motion")
    }
    add("Appearance", "look.contrast", "High contrast", "Toggle daylight text", G.Eye, "accessibility contrast") {
        val next = !Prefs.settings.value.highContrast
        Prefs.update { it.copy(highContrast = next) }
        app.bunmusic.core.Skin.contrast = next
        host.toast(if (next) "High contrast on" else "Standard contrast")
    }
    add("Appearance", "look.storage", "Storage", "What the cache and artwork cost", G.Cloud, "space clean cache") {
        host.go(Route.Storage)
    }

    // --------------------------------------------------------------- system
    add("System", "sys.share", "Share BunMusic", "", G.Share) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "BunMusic — a quiet place for loud music. https://github.com/Iotatg/BunMusic")
        }
        host.context.startActivity(Intent.createChooser(send, "Share BunMusic"))
    }
    add("System", "sys.source", "Open the source", "github.com/Iotatg/BunMusic", G.Youtube) {
        host.context.startActivity(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Iotatg/BunMusic"))
        )
    }
    add("System", "sys.diagnostics", "Copy diagnostics", "Version, bun, queue, cache", G.Shield) {
        val s = Playback.state.value
        val text = buildString {
            appendLine("BunMusic ${AppVersion.LABEL}")
            appendLine("Bun: ${Skin.current.title}")
            appendLine("Track: ${s.track?.title ?: "—"} · ${s.track?.artist ?: "—"}")
            appendLine("Queue: ${s.queue.size} at ${s.index}")
            appendLine("Playing: ${s.isPlaying} · ${s.positionMs / 1000}s / ${s.durationMs / 1000}s")
            appendLine("Cache: ${Cache.streamBytes() / 1_048_576} MB stream, ${Cache.imageBytes() / 1_048_576} MB art")
            append("Downloads: ${Downloads.entries.value.size}")
        }
        val clip = host.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText("BunMusic diagnostics", text))
        host.toast("Diagnostics copied")
    }
    add("System", "sys.restart", "Restart BunMusic", "Reloads the bun", G.Refresh) {
        Skin.apply(host.context, Skin.current)
    }
    add("System", "sys.signout", "Sign out", "Keeps this device's library", G.Logout) {
        Auth.signOut()
        host.close()
    }
    add("System", "sys.about", "About BunMusic", AppVersion.VERSION, G.Sparkle) { host.go(Route.About) }

    return out
}

private fun downloadedTracks(): List<Track> = Downloads.entries.value.mapNotNull { entry ->
    Store.track(entry.trackId) ?: Track(
        id = entry.trackId,
        title = entry.title,
        artist = entry.artist,
        artUrl = entry.artUrl,
    )
}

private fun routeName(route: Route): String = when (route) {
    Route.Home -> "home"
    Route.Explore -> "explore"
    Route.Library -> "library"
    Route.Search -> "search"
    Route.Settings -> "settings"
    Route.Stats -> "stats"
    Route.YearReview -> "year"
    Route.Downloads -> "downloads"
    Route.Profile -> "profile"
    Route.Equalizer -> "equalizer"
    Route.Hotlist -> "hotlist"
    Route.Buns -> "buns"
    Route.About -> "about"
    Route.Widgets -> "widgets"
    Route.Discovery -> "discovery"
    Route.Storage -> "storage"
    is Route.SearchFor -> "search"
    is Route.Detail -> "detail"
    is Route.MoodPage -> "mood"
    is Route.PlaylistPage -> "playlist"
}

private fun kindLabel(kind: WidgetKind): String = when (kind) {
    WidgetKind.MEDIA -> "4×2"
    WidgetKind.COMPACT -> "2×2"
    WidgetKind.BAR -> "4×1"
    WidgetKind.QUEUE -> "queue"
}

private fun trimSpeed(value: Float): String =
    if (value == value.toInt().toFloat()) "${value.toInt()}" else value.toString().trimEnd('0').trimEnd('.')

/**
 * Filter and rank. Pure, so the palette's behaviour can be tested without a
 * screen: an empty query keeps the catalogue order, and a query has to match
 * every word somewhere in the title, hint, group or keywords.
 */
fun filterCommands(all: List<Command>, query: String): List<Command> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return all
    val words = q.split(' ').filter { it.isNotBlank() }
    return all
        .filter { c ->
            val hay = "${c.title} ${c.hint} ${c.group} ${c.keywords}".lowercase()
            words.all { hay.contains(it) }
        }
        .sortedBy { c ->
            val title = c.title.lowercase()
            when {
                title == q -> 0
                title.startsWith(q) -> 1
                else -> 2
            }
        }
}
