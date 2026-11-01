package app.bunmusic.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User preferences. Every screen reads [settings] and writes through [update],
 * so a toggle in Settings takes effect immediately everywhere.
 */
object Prefs {

    enum class Quality(val label: String, val bitrate: Long, val note: String) {
        LOW("Low", 64_000, "Saves data"),
        NORMAL("Normal", 128_000, "Balanced"),
        HIGH("High", 256_000, "Best quality"),
    }

    enum class PlayerStyle(val label: String) {
        DISC("Disc"),
        VINYL("Vinyl"),
        CARD("Card"),
    }

    enum class Glow(val label: String) {
        OFF("Off"),
        SOFT("Soft"),
        BOLD("Bold"),
    }

    data class Settings(
        val quality: Quality = Quality.HIGH,
        val playerStyle: PlayerStyle = PlayerStyle.DISC,
        val glow: Glow = Glow.SOFT,
        val crossfadeSec: Int = 0,
        val normalize: Boolean = true,
        val gapless: Boolean = true,
        val wifiOnly: Boolean = true,
        val lyricsOnPlayer: Boolean = true,
        val showStats: Boolean = true,
        val autoplay: Boolean = true,
        val offlineOnly: Boolean = false,
        val eqEnabled: Boolean = false,
        val eqPreset: String = "Flat",
        val eqBands: List<Int> = emptyList(),
        val bassBoost: Int = 0,
        val googleClientId: String = "",
        /** Which bun is worn — see [app.bunmusic.core.BunSkin]. */
        val skin: String = "MIDNIGHT",
        val haptics: Boolean = true,
        /** Shift applied to synced lyrics, milliseconds. */
        val lyricsOffsetMs: Int = 0,
        /** Stream cache ceiling in MB: 0 = off, -1 = unlimited. */
        val cacheLimitMb: Int = 512,
        /** Pull the next couple of songs into the cache while this one plays. */
        val prefetchAhead: Boolean = true,
        /** Skip silent gaps inside a track instead of playing silence. */
        val skipSilence: Boolean = false,
        /** Bring the last queue back, paused, when the app is reopened. */
        val resumeOnLaunch: Boolean = true,
        /** Highlight lyrics word by word when the provider gives timings. */
        val wordLyrics: Boolean = true,
        /** Take the player's tint from the cover. Off keeps the stable id hash. */
        val artColour: Boolean = true,
        /** Cut every non-essential animation. Set by the system too. */
        val reducedMotion: Boolean = false,
        /** 0.85 – 1.30. Multiplies the type scale, not the layout. */
        val fontScale: Float = 1f,
        /** Stronger hairlines and text contrast for bright sunlight. */
        val highContrast: Boolean = false,
        /** Library shows covers in a two-column grid instead of rows. */
        val libraryGrid: Boolean = false,
        /** Shuffle keeps artists apart and pushes skipped songs to the back. */
        val smartShuffle: Boolean = true,
        /** Bars under the artwork on the player. */
        val visualizer: Boolean = true,
        /** Show the daily mixes and taste card on Home. */
        val discoveryHome: Boolean = true,
        /** Fade the last ten seconds of a sleep timer. */
        val sleepFade: Boolean = true,
        /** Show a banner when the queue runs offline. */
        val offlineBanner: Boolean = true,
    )

    private lateinit var sp: SharedPreferences
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.getSharedPreferences("bunmusic_prefs", Context.MODE_PRIVATE)
        _settings.value = read()
    }

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            quality = runCatching { Quality.valueOf(sp.getString("quality", d.quality.name)!!) }.getOrDefault(d.quality),
            playerStyle = runCatching { PlayerStyle.valueOf(sp.getString("style", d.playerStyle.name)!!) }.getOrDefault(d.playerStyle),
            glow = runCatching { Glow.valueOf(sp.getString("glow", d.glow.name)!!) }.getOrDefault(d.glow),
            crossfadeSec = sp.getInt("crossfade", d.crossfadeSec),
            normalize = sp.getBoolean("normalize", d.normalize),
            gapless = sp.getBoolean("gapless", d.gapless),
            wifiOnly = sp.getBoolean("wifiOnly", d.wifiOnly),
            lyricsOnPlayer = sp.getBoolean("lyricsOnPlayer", d.lyricsOnPlayer),
            showStats = sp.getBoolean("showStats", d.showStats),
            autoplay = sp.getBoolean("autoplay", d.autoplay),
            offlineOnly = sp.getBoolean("offlineOnly", d.offlineOnly),
            eqEnabled = sp.getBoolean("eqEnabled", d.eqEnabled),
            eqPreset = sp.getString("eqPreset", d.eqPreset) ?: d.eqPreset,
            eqBands = (sp.getString("eqBands", "") ?: "").split(',').mapNotNull { it.toIntOrNull() },
            bassBoost = sp.getInt("bassBoost", d.bassBoost),
            googleClientId = sp.getString("googleClientId", d.googleClientId) ?: "",
            skin = sp.getString("skin", d.skin) ?: d.skin,
            haptics = sp.getBoolean("haptics", d.haptics),
            lyricsOffsetMs = sp.getInt("lyricsOffset", d.lyricsOffsetMs),
            cacheLimitMb = sp.getInt("cacheLimit", d.cacheLimitMb),
            prefetchAhead = sp.getBoolean("prefetchAhead", d.prefetchAhead),
            skipSilence = sp.getBoolean("skipSilence", d.skipSilence),
            resumeOnLaunch = sp.getBoolean("resumeOnLaunch", d.resumeOnLaunch),
            wordLyrics = sp.getBoolean("wordLyrics", d.wordLyrics),
            artColour = sp.getBoolean("artColour", d.artColour),
            reducedMotion = sp.getBoolean("reducedMotion", d.reducedMotion),
            fontScale = sp.getFloat("fontScale", d.fontScale).coerceIn(0.85f, 1.30f),
            highContrast = sp.getBoolean("highContrast", d.highContrast),
            libraryGrid = sp.getBoolean("libraryGrid", d.libraryGrid),
            smartShuffle = sp.getBoolean("smartShuffle", d.smartShuffle),
            visualizer = sp.getBoolean("visualizer", d.visualizer),
            discoveryHome = sp.getBoolean("discoveryHome", d.discoveryHome),
            sleepFade = sp.getBoolean("sleepFade", d.sleepFade),
            offlineBanner = sp.getBoolean("offlineBanner", d.offlineBanner),
        )
    }

    fun update(block: (Settings) -> Settings) {
        if (!::sp.isInitialized) return
        val next = block(_settings.value)
        _settings.value = next
        sp.edit()
            .putString("quality", next.quality.name)
            .putString("style", next.playerStyle.name)
            .putString("glow", next.glow.name)
            .putInt("crossfade", next.crossfadeSec)
            .putBoolean("normalize", next.normalize)
            .putBoolean("gapless", next.gapless)
            .putBoolean("wifiOnly", next.wifiOnly)
            .putBoolean("lyricsOnPlayer", next.lyricsOnPlayer)
            .putBoolean("showStats", next.showStats)
            .putBoolean("autoplay", next.autoplay)
            .putBoolean("offlineOnly", next.offlineOnly)
            .putBoolean("eqEnabled", next.eqEnabled)
            .putString("eqPreset", next.eqPreset)
            .putString("eqBands", next.eqBands.joinToString(","))
            .putInt("bassBoost", next.bassBoost)
            .putString("googleClientId", next.googleClientId)
            .putString("skin", next.skin)
            .putBoolean("haptics", next.haptics)
            .putInt("lyricsOffset", next.lyricsOffsetMs)
            .putInt("cacheLimit", next.cacheLimitMb)
            .putBoolean("prefetchAhead", next.prefetchAhead)
            .putBoolean("skipSilence", next.skipSilence)
            .putBoolean("resumeOnLaunch", next.resumeOnLaunch)
            .putBoolean("wordLyrics", next.wordLyrics)
            .putBoolean("artColour", next.artColour)
            .putBoolean("reducedMotion", next.reducedMotion)
            .putFloat("fontScale", next.fontScale)
            .putBoolean("highContrast", next.highContrast)
            .putBoolean("libraryGrid", next.libraryGrid)
            .putBoolean("smartShuffle", next.smartShuffle)
            .putBoolean("visualizer", next.visualizer)
            .putBoolean("discoveryHome", next.discoveryHome)
            .putBoolean("sleepFade", next.sleepFade)
            .putBoolean("offlineBanner", next.offlineBanner)
            .apply()
    }
}
