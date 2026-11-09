package app.bunmusic.data

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real device equaliser built on the platform audio effects.
 *
 * The effects are attached to ExoPlayer's audio session, so they colour playback and
 * nothing else. Band count and frequency centres are read from the hardware — some
 * phones expose 5 bands, some 10 — and the UI renders whatever is actually there.
 */
object Eq {

    data class Band(
        val index: Int,
        val hz: Int,
        val levelMb: Int,
        val minMb: Int,
        val maxMb: Int,
    ) {
        val label: String
            get() = if (hz >= 1000) "${hz / 1000}k" else "$hz"

        val levelDb: Float get() = levelMb / 100f
    }

    data class State(
        val available: Boolean = false,
        val enabled: Boolean = false,
        val preset: String = "Flat",
        val bands: List<Band> = emptyList(),
        val bass: Int = 0,
        val bassAvailable: Boolean = false,
        val loudnessAvailable: Boolean = false,
    )

    val presets = listOf("Flat", "Bass", "Vocal", "Rock", "Electronic", "Podcast")

    /** Gains in dB, spread across however many bands the device exposes. */
    internal val presetGains = mapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
        "Bass" to listOf(7.5f, 5f, 0f, -1f, -2f),
        "Vocal" to listOf(-2f, 0f, 5f, 4f, 1f),
        "Rock" to listOf(5f, 2f, -2f, 3f, 5f),
        "Electronic" to listOf(6f, 3f, -1f, 4f, 6f),
        "Podcast" to listOf(-4f, -1f, 6f, 5f, -2f),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var equalizer: Equalizer? = null
    private var bass: BassBoost? = null
    private var loudness: LoudnessEnhancer? = null
    private var sessionId: Int = 0

    /** Called once the player has an audio session. Safe to call repeatedly. */
    @Synchronized
    fun attach(session: Int) {
        if (session <= 0) return
        if (session == sessionId && equalizer != null) return
        release()
        sessionId = session
        runCatching {
            equalizer = Equalizer(0, session).apply { enabled = false }
        }.onFailure { equalizer = null }
        runCatching {
            bass = BassBoost(0, session).apply { enabled = false }
        }.onFailure { bass = null }
        runCatching {
            loudness = LoudnessEnhancer(session).apply { enabled = false }
        }.onFailure { loudness = null }

        val eq = equalizer
        if (eq == null) {
            _state.value = State(available = false)
            return
        }
        val count = runCatching { eq.numberOfBands.toInt() }.getOrDefault(0)
        // OEMs sometimes return a short or reversed bandLevelRange. Reading
        // [0]/[1] blindly threw, and min > max made every coerceIn below throw.
        val range = safeBandRange(runCatching { eq.bandLevelRange }.getOrNull())
        val bands = (0 until count).map { i ->
            val idx = i.toShort()
            val centre = runCatching { eq.getCenterFreq(idx) / 1000 }.getOrDefault(0)
            Band(
                index = i,
                hz = centre,
                levelMb = runCatching { eq.getBandLevel(idx).toInt() }.getOrDefault(0),
                minMb = range.first,
                maxMb = range.last,
            )
        }
        _state.value = State(
            available = true,
            enabled = eq.enabled,
            preset = Prefs.settings.value.eqPreset,
            bands = bands,
            bass = Prefs.settings.value.bassBoost,
            bassAvailable = bass != null,
            loudnessAvailable = loudness != null,
        )
    }

    fun setEnabled(on: Boolean) {
        val eq = equalizer ?: return
        runCatching { eq.enabled = on }
        if (on) {
            val s = Prefs.settings.value
            applyBands(s.eqBands, s.eqPreset)
            runCatching { bass?.enabled = s.bassBoost > 0 }
        } else {
            runCatching { bass?.enabled = false }
        }
        _state.value = _state.value.copy(enabled = on)
    }

    private fun applyBands(levels: List<Int>, preset: String) {
        val eq = equalizer ?: return
        val bands = _state.value.bands.mapIndexed { i, band ->
            val mb = (levels.getOrNull(i) ?: 0).coerceIn(band.minMb, band.maxMb)
            runCatching { eq.setBandLevel(band.index.toShort(), mb.toShort()) }
            band.copy(levelMb = mb)
        }
        _state.value = _state.value.copy(bands = bands, preset = preset)
    }

    fun setBand(index: Int, levelMb: Int) {
        val eq = equalizer ?: return
        val band = _state.value.bands.getOrNull(index) ?: return
        val clamped = levelMb.coerceIn(band.minMb, band.maxMb)
        runCatching { eq.setBandLevel(band.index.toShort(), clamped.toShort()) }
        val bands = _state.value.bands.toMutableList()
        bands[index] = band.copy(levelMb = clamped)
        _state.value = _state.value.copy(bands = bands, preset = "Custom")
        persist(bands, "Custom", _state.value.bass)
    }

    fun setPreset(name: String) {
        val eq = equalizer ?: return
        val gains = presetGains[name] ?: presetGains["Flat"]!!
        val bands = _state.value.bands.mapIndexed { i, band ->
            val gain = spread(gains, i, _state.value.bands.size)
            val mb = (gain * 100).toInt().coerceIn(band.minMb, band.maxMb)
            runCatching { eq.setBandLevel(band.index.toShort(), mb.toShort()) }
            band.copy(levelMb = mb)
        }
        _state.value = _state.value.copy(bands = bands, preset = name)
        persist(bands, name, _state.value.bass)
    }

    fun setBass(strength: Int) {
        val b = bass ?: return
        val value = strength.coerceIn(0, 1000)
        runCatching {
            b.setStrength(value.toShort())
            b.enabled = value > 0 && _state.value.enabled
        }
        _state.value = _state.value.copy(bass = value)
        persist(_state.value.bands, _state.value.preset, value)
    }

    /** Volume normalisation: a gentle fixed gain so quiet masters stay audible. */
    fun setNormalisation(on: Boolean) {
        val l = loudness ?: return
        runCatching {
            l.setTargetGain(if (on) 300 else 0)
            l.enabled = on
        }
    }

    fun reset() = setPreset("Flat")

    @Synchronized
    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bass?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        bass = null
        loudness = null
    }

    /** Restore persisted state after the player is created. */
    fun restore(settings: Prefs.Settings) {
        if (!settings.eqEnabled) {
            setEnabled(false)
            return
        }
        setEnabled(true)
        if (settings.eqBands.isNotEmpty()) {
            val eq = equalizer ?: return
            val bands = _state.value.bands.mapIndexed { i, band ->
                val mb = (settings.eqBands.getOrNull(i) ?: 0).coerceIn(band.minMb, band.maxMb)
                runCatching { eq.setBandLevel(band.index.toShort(), mb.toShort()) }
                band.copy(levelMb = mb)
            }
            _state.value = _state.value.copy(bands = bands, preset = settings.eqPreset)
        } else {
            setPreset(settings.eqPreset)
        }
        setBass(settings.bassBoost)
        setNormalisation(settings.normalize)
    }

    private fun persist(bands: List<Band>, preset: String, bass: Int) {
        Prefs.update {
            it.copy(
                eqBands = bands.map { b -> b.levelMb },
                eqPreset = preset,
                bassBoost = bass,
            )
        }
    }

    /**
     * Turn whatever the hardware reported into a valid, ordered millibel range.
     * The platform contract says `bandLevelRange` is `[min, max]`, but some OEMs
     * return a short (even single-element) array or swap the two values. Reading
     * `[0]/[1]` blindly threw, and a `min > max` makes every `coerceIn` in this
     * file throw. Pure so the guard is testable without an audio session.
     */
    internal fun safeBandRange(raw: ShortArray?): IntRange {
        // A malformed array (null / empty / one element) carries no usable pair;
        // trusting a single element would invent a bogus lower bound. Fall back
        // to the platform's standard ±15 dB range as one whole.
        if (raw == null || raw.size < 2) return -1500..1500
        val a = raw[0].toInt()
        val b = raw[1].toInt()
        val lo = minOf(a, b)
        val hi = maxOf(a, b)
        // An empty range still makes coerceIn throw; keep at least one step.
        return lo..if (hi > lo) hi else lo + 1
    }

    /** Map a five-point curve onto an arbitrary band count. */
    internal fun spread(gains: List<Float>, index: Int, total: Int): Float {
        if (total <= 1) return gains.first()
        val pos = index.toFloat() / (total - 1) * (gains.size - 1)
        val low = pos.toInt().coerceIn(0, gains.size - 1)
        val high = (low + 1).coerceAtMost(gains.size - 1)
        val t = pos - low
        return gains[low] * (1 - t) + gains[high] * t
    }
}
