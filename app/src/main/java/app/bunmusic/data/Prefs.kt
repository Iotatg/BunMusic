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
            .apply()
    }
}
