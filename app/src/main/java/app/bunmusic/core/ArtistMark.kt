package app.bunmusic.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The face an artist wears when we have no photograph of them.
 *
 * YouTube Music publishes a portrait only on the artist's own page; a track row
 * carries the song cover and a name, nothing else. So the player could always
 * draw the *song*, never the *artist* — the name sat there as bare text.
 *
 * A monogram fixes that honestly: up to two initials on a disc whose colour is
 * derived from the name, so the same artist is always the same colour and a
 * different artist is visibly different. It is not a photo, and the UI prefers
 * a real portrait the moment one is known; this is what stops the circle from
 * ever being empty when it is not.
 *
 * All of it is pure arithmetic, so the rules — which letters, which hue, and
 * that the letters stay readable — are held by JVM tests rather than a device.
 */
object ArtistMark {

    /** Words that separate names rather than name anyone ("Simon & Garfunkel"). */
    private val CONNECTORS = setOf(
        "&", "+", "x", "×", "and", "with", "vs", "feat", "feat.", "ft", "ft.",
        "featuring",
    )

    /**
     * Up to two initials, uppercase. "?" when there is nothing to draw.
     *
     * "Arijit Singh" → "AS", "Adele" → "A", "Simon & Garfunkel" → "SG",
     * "A. R. Rahman" → "AR". The first letter of each of the first two
     * meaningful words, so a monogram never runs to three letters or a glyph.
     */
    fun initials(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "?"
        val letters = trimmed
            .split(WHITESPACE)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.lowercase() in CONNECTORS }
            .mapNotNull { firstLetter(it) }
            .take(2)
            .toList()
        return when {
            letters.isEmpty() -> firstLetter(trimmed) ?: "?"
            else -> letters.joinToString("")
        }
    }

    /**
     * A stable hue in [0, 360) for a name.
     *
     * FNV-1a over the lowercased name: cheap, dependency-free, and stable
     * across processes so an artist keeps the same colour between launches.
     */
    fun hue(name: String): Float {
        var hash = 2166136261L
        for (c in name.trim().lowercase()) {
            hash = hash xor c.code.toLong()
            hash = (hash * 16777619L) and 0xFFFFFFFFL
        }
        return (hash % 360L).toFloat()
    }

    /** The opaque disc colour behind the initials. */
    fun argb(name: String): Int = hsl(hue(name), 0.52f, 0.36f)

    /** Black or white, whichever clears WCAG AA on [argb]. */
    fun ink(name: String): Int = Contrast.bestInk(argb(name))

    // ------------------------------------------------------------------ guts

    private val WHITESPACE = Regex("\\s+")

    /**
     * The first letter or digit in a token, uppercase.
     *
     * Reads a code point rather than a `Char` so an emoji or an Indic letter
     * that needs a surrogate pair is not cut in half.
     */
    private fun firstLetter(token: String): String? {
        var i = 0
        while (i < token.length) {
            val cp = token.codePointAt(i)
            val ch = String(Character.toChars(cp))
            if (ch.first().isLetterOrDigit()) return ch.uppercase()
            i += Character.charCount(cp)
        }
        return null
    }

    private fun hsl(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = h / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        fun channel(v: Float) = ((v + m).coerceIn(0f, 1f) * 255f).roundToInt()
        return (0xFF shl 24) or (channel(r1) shl 16) or (channel(g1) shl 8) or channel(b1)
    }
}
