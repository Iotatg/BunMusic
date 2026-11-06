package app.bunmusic.widget

import app.bunmusic.data.hq
import java.security.MessageDigest

/**
 * The arithmetic behind the artwork cache, kept pure so it can be tested.
 *
 * The bug it exists to prevent: the disk key used to be
 * `md5(rawUrl)_<sizePx>_<radiusPx>`. The main widget loaded a cover at 180 px
 * with one corner radius, the compact widget at 320 px with another, and the
 * queue rows asked for 96 px / 20 px. Every one of those wrote and read a
 * *different file*, so a thumb already on disk was never the one the caller
 * looked for — "cached thumbnails don't show", exactly.
 *
 * Now there is one canonical square per cover on disk, keyed only by the
 * normalised URL. Sizes and corner radii are memory-only derivations from that
 * one file, so every consumer finds the same bytes.
 */
object ArtKeys {

    /**
     * The app asks Coil for `hq(artUrl)` but widgets were handed the raw URL.
     * Normalising here means both spellings resolve to one cache entry.
     */
    fun normalized(url: String?): String? = url?.takeIf { it.isNotBlank() }?.let { hq(it) }

    fun canonical(url: String?): String? = normalized(url)?.let { md5(it) }

    fun diskFile(url: String?): String? = canonical(url)?.let { "$it.png" }

    fun memory(url: String?, sizePx: Int, radiusPx: Int): String? =
        canonical(url)?.let { "${it}_${sizePx}_$radiusPx" }

    /**
     * Legacy entries were `<md5>_<size>_<radius>.png`. They can never be found
     * by the new reader, so the trim pass deletes them.
     */
    fun isCanonicalName(name: String): Boolean = CANONICAL.matches(name)

    private val CANONICAL = Regex("[0-9a-f]{32}\\.png")

    private fun md5(s: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
