package app.bunmusic.data

/**
 * Playlists as a file, in the format every other player already speaks.
 *
 * M3U is the lowest common denominator: a text file, one entry per line, with an
 * optional `#EXTINF` carrying the duration and the credit. Exporting to it means
 * a BunMusic playlist can be opened by VLC, foobar2000 or a car stereo; importing
 * from it means the library is not a prison.
 *
 * The parser is deliberately forgiving. Real `.m3u` files in the wild are written
 * by dozens of programs and contain BOMs, blank lines, Windows line endings and
 * absolute paths — none of which should stop an import.
 */
object PlaylistIo {

    data class Parsed(
        val name: String?,
        val ids: List<String>,
        val videoIds: List<String>,
        val skipped: Int,
    ) {
        val total: Int get() = ids.size + videoIds.size
        val isEmpty: Boolean get() = total == 0
    }

    fun export(name: String, tracks: List<Track>): String {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        sb.append("#PLAYLIST:").append(name.replace('\n', ' ')).append('\n')
        tracks.forEach { t ->
            val seconds = (t.durationMs / 1000).coerceAtLeast(0)
            sb.append("#EXTINF:").append(seconds).append(',')
                .append(t.artist.replace('\n', ' ')).append(" - ")
                .append(t.title.replace('\n', ' ')).append('\n')
            sb.append("bunmusic://track/").append(t.id).append('\n')
        }
        return sb.toString()
    }

    fun parse(text: String): Parsed {
        var name: String? = null
        val ids = ArrayList<String>()
        val videos = ArrayList<String>()
        var skipped = 0
        val clean = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')

        for (raw in clean.split('\n')) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#PLAYLIST:", ignoreCase = true) ->
                    name = line.substringAfter(':').trim().ifBlank { null }

                line.startsWith("#") -> Unit // #EXTM3U, #EXTINF and comments

                line.startsWith("bunmusic://track/", ignoreCase = true) -> {
                    val id = line.substringAfterLast('/').trim()
                    if (id.isNotEmpty()) ids += id else skipped++
                }

                line.contains("v=") -> {
                    val v = line.substringAfter("v=").substringBefore('&').trim()
                    if (v.isNotEmpty()) videos += v else skipped++
                }

                // A bare id is what an older export wrote.
                line.length in 6..40 && line.none { it == '/' || it == '\\' || it == '.' } ->
                    ids += line

                else -> skipped++
            }
        }
        return Parsed(name, ids.distinct(), videos.distinct(), skipped)
    }

    /**
     * Turn a parsed file into tracks, using the local registry first and the
     * YouTube id second. Anything still missing is genuinely not in this library;
     * it is counted, not silently dropped.
     */
    fun resolve(parsed: Parsed, all: List<Track>): Pair<List<Track>, Int> {
        val byId = all.associateBy { it.id }
        val byVideo = all.filter { it.videoId.isNotBlank() }.associateBy { it.videoId }
        val out = ArrayList<Track>()
        var missing = 0
        parsed.ids.forEach { id ->
            val t = byId[id]
            if (t != null) out += t else missing++
        }
        parsed.videoIds.forEach { v ->
            val t = byVideo[v]
            if (t != null) out += t else missing++
        }
        return out.distinctBy { it.id } to missing
    }

    /** A filename that will not be rejected by a filesystem. */
    fun fileName(name: String): String {
        val safe = name.map { if (it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .trim()
            .ifBlank { "playlist" }
        return "$safe.m3u"
    }
}
