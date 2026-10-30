package app.bunmusic.core

import androidx.compose.ui.graphics.Color
import app.bunmusic.data.Track

/**
 * Ambient colour: every track gets a stable, hand-picked hue derived from its
 * id. Deterministic, so the player glow never flickers between recompositions,
 * and always inside a palette that works on the Midnight background.
 */
object Ambient {

    private val hues = floatArrayOf(268f, 212f, 342f, 158f, 28f, 190f, 292f, 6f)

    fun colorFor(track: Track?): Color = colorFor(track?.id ?: "bunmusic")

    fun colorFor(seed: String): Color {
        var h = 7
        for (ch in seed) h = (h * 31 + ch.code) and 0x7fffffff
        val hue = hues[h % hues.size]
        return Color.hsv(hue, 0.46f, 0.72f)
    }

    fun glowFor(seed: String, alpha: Float): Color =
        colorFor(seed).copy(alpha = alpha)
}
