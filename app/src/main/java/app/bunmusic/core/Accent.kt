package app.bunmusic.core

/**
 * Accent Studio — a bun you can re-colour without breaking it.
 *
 * A bun is a whole surface language, not a tint: switching one restarts the
 * process. But the *accent* — the lime, the blue, the rose — is the one colour
 * a person reasonably wants to choose for themselves, and it was the one axis
 * with no setting at all. This is that setting, done the way the rest of the
 * palette is done: as pure arithmetic with a measured contrast guarantee.
 *
 * The problem with letting a user pick an accent is not picking it. It is that
 * the accent is also used as 11 sp text (the selected nav label), and as the
 * fill under a button label. A bright yellow on a chalk page is invisible; a
 * deep violet on near-black is invisible the other way. So the four roles below
 * are never assigned the raw colour blindly:
 *
 *  - [onAccent] is black or white, chosen by measured ratio, not by a guess;
 *  - [navActiveInk] is nudged toward the better ink until it clears WCAG AA on
 *    the pill it actually sits on, which is the nav background with the accent
 *    blended in at the bun's own alpha — not the raw accent;
 *  - [navActiveBg] keeps the bun's alpha (Midnight's 14 % wash, Charcoal's
 *    solid pill) so a swap cannot change the *shape* of the design, only its hue.
 *
 * Everything here is Int ARGB and pure. [Contrast] already owns the WCAG maths;
 * this object only decides which roles follow the accent and how they do it.
 * The thin [BunPalette] adapter lives in [Skin], where Compose's [Color] does.
 */
object Accent {

    /** A preset, keyed by id so a stored choice survives a rename of the label. */
    data class Swatch(val id: String, val label: String, val argb: Int) {
        val hex: String get() = hex(argb)
    }

    /**
     * Eight starting points, one per colour family, plus the bun's own accent
     * as the implicit ninth ("Default"). They are deliberately spread across
     * luminance: the interesting part of this feature is watching [legible]
     * pull a too-bright swatch down on a light bun and lift a too-dark one on a
     * dark bun, rather than shipping a palette where half the choices vanish.
     */
    val SWATCHES: List<Swatch> = listOf(
        Swatch("lime", "Lime", 0xFFD8F65C.toInt()),
        Swatch("violet", "Violet", 0xFFA78BFF.toInt()),
        Swatch("blue", "Electric blue", 0xFF2E6BFF.toInt()),
        Swatch("rose", "Rose", 0xFFD63366.toInt()),
        Swatch("ink", "Ink", 0xFF171C26.toInt()),
        Swatch("amber", "Amber", 0xFFFFB020.toInt()),
        Swatch("mint", "Mint", 0xFF12B48A.toInt()),
        Swatch("coral", "Coral", 0xFFFF6B5C.toInt()),
    )

    fun swatch(id: String): Swatch? = SWATCHES.firstOrNull { it.id == id }

    /**
     * Parse a stored colour. Accepts `#RRGGBB`, `RRGGBB` and `#AARRGGBB`, and
     * nothing else — no named colours, no short form, no floats.
     *
     * The result is always **opaque**: a translucent accent would make every
     * contrast number below a lie, because compositing happens after we measure.
     * Malformed input returns null so the caller can fall back to the bun's own
     * accent instead of painting black from a bad hex.
     */
    fun parse(raw: String?): Int? {
        if (raw == null) return null
        var s = raw.trim()
        if (s.startsWith("#")) s = s.substring(1)
        if (s.length != 6 && s.length != 8) return null
        if (s.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        val value = runCatching { s.toLong(16) }.getOrNull() ?: return null
        // Only the RGB half is kept even when an alpha channel was supplied.
        return (0xFF000000L or (value and 0xFFFFFFL)).toInt()
    }

    /** `#RRGGBB`, uppercase, always six digits — the form [parse] writes back. */
    fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** Ink on an accent fill: whichever of black/white measures better. */
    fun onInk(accent: Int): Int = Contrast.bestInk(accent)

    /**
     * The accent used as small text on [background], pulled toward the better
     * ink until it clears AA. Delegates to [Contrast.readable] so there is one
     * definition of "legible" in the app, not two that drift apart.
     */
    fun legible(accent: Int, background: Int): Int = Contrast.readable(accent, background)

    /**
     * Swap RGB while keeping [source]'s alpha. This is how [derive] preserves
     * Midnight's 0x24 wash and Charcoal's solid pill with the same rule.
     */
    fun withRgb(source: Int, rgb: Int): Int =
        (source and 0xFF000000.toInt()) or (rgb and 0xFFFFFF)

    /** The four palette roles that follow the accent, as raw ARGB. */
    data class Tones(
        val accent: Int,
        val onAccent: Int,
        val navBg: Int,
        val navActiveBg: Int,
        val navActiveInk: Int,
    )

    /**
     * Derive the four roles for [accent]. Pure: the same bun + the same colour
     * always yields the same answer on every device.
     *
     * The two nav designs need two different rules, and the alpha of the bun's
     * original pill is what tells them apart without a per-bun special case:
     *
     *  - **Solid pill** (Charcoal / Cloud / Pink / Nebula): the selected item is
     *    a filled accent block, so its label has to be black or white on the
     *    accent — the same answer as [onAccent]. Painting accent-coloured words
     *    on an accent-coloured block would be invisible.
     *  - **Translucent wash** (Midnight only, 0x24): the nav background still
     *    shows through, and the label *is* the accent used as small text.
     *    Measuring against the composited surface matters here — the pill is only
     *    14 % accent, so measuring against the raw accent would "fix" a label
     *    that was already legible on the near-black it really sits on.
     */
    fun derive(tones: Tones, accent: Int): Tones {
        val pillAlpha = ((tones.navActiveBg ushr 24) and 0xFF) / 255f
        val solid = pillAlpha >= 0.9f
        val navActiveInk = if (solid) {
            onInk(accent)
        } else {
            legible(accent, Contrast.blend(tones.navBg, accent, pillAlpha))
        }
        return Tones(
            accent = accent,
            onAccent = onInk(accent),
            navBg = tones.navBg,
            // keep the bun's alpha: the shape of the selection stays, only hue moves
            navActiveBg = withRgb(tones.navActiveBg, accent),
            navActiveInk = navActiveInk,
        )
    }

    /**
     * Per-bun storage: `MIDNIGHT=#D8F65C;CLOUD=#2E6BFF`. Sorted for stable
     * writes, tolerant of junk on read — a bad entry is dropped, never fatal.
     * Kept as a string rather than a second preferences file so [Prefs.update]
     * stays the single read-modify-write lock for all settings.
     */
    fun encode(map: Map<String, Int>): String = buildString {
        map.toSortedMap().forEach { (bun, argb) ->
            if (bun.isNotBlank()) {
                if (isNotEmpty()) append(';')
                append(bun).append('=').append(hex(argb))
            }
        }
    }

    fun decode(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, Int>()
        for (part in raw.split(';')) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val bun = part.substring(0, eq).trim()
            val colour = parse(part.substring(eq + 1)) ?: continue
            if (bun.isNotEmpty()) out[bun] = colour
        }
        return out
    }
}
