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
            .apply()
    }
}
