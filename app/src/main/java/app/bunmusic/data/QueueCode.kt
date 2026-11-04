package app.bunmusic.data

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * A queue, written as one string.
 *
 * The point is that it needs no server: the whole list travels inside the text
 * itself, so two phones can trade a queue over any channel that carries words —
 * a QR code, a message, a note to yourself.
 *
 * The payload is `id \t title \t artist \t seconds` per line, deflated, then
 * base64url. Deflate is not decoration: fifty tracks run about 2 KB raw and
 * about 950 bytes packed, which is the difference between a QR code that fits
 * on a phone screen and one that does not. zlib wraps the stream, so the adler32
 * checksum comes for free — a truncated paste fails loudly instead of producing
 * a queue of mangled titles.
 *
 * What it does not carry: anything you cannot share. Local files have no video
 * id, so they are dropped rather than silently turned into broken rows.
 */
object QueueCode {

    const val PREFIX = "BQ1"

    /** Beyond this a QR code stops being scannable off a screen. */
    const val MAX_TRACKS = 50

    /**
     * The hard ceiling on the code itself, in characters.
     *
     * A version-40 symbol holds 2331 bytes at M, but 177x177 modules on a phone
     * screen is a QR code in name only — cameras cannot reliably find it. v25 is
     * 113x113 and holds 997 bytes; 900 leaves room for the prefix and keeps the
     * symbol in that neighbourhood. Titles are deflated first, so a normal
     * 50-song queue is far below this. Only long, unrepeating names get trimmed.
     */
    const val MAX_CHARS = 900

    private const val MAX_FIELD = 120
    private const val SEP = '\t'

    data class Item(
        val videoId: String,
        val title: String,
        val artist: String,
        val durationMs: Long,
    )

    /**
     * Packs [tracks] into a code that always fits a scannable QR symbol.
     *
     * Returns an empty string when there is nothing shareable. When the packed
     * text would exceed [MAX_CHARS], tracks are dropped from the tail — the
     * first ones, which the sender is most likely to care about, survive. A
     * code that silently failed to become a QR picture would be worse: the
     * screen would say "nothing to send" while the queue was full.
     */
    fun encode(tracks: List<Track>, limit: Int = MAX_TRACKS): String {
        val seen = HashSet<String>()
        val rows = ArrayList<String>(minOf(tracks.size, limit))
        for (track in tracks) {
            if (rows.size >= limit) break
            // A track with no video id cannot be played on somebody else's
            // phone — local files and half-built rows are dropped, not mangled.
            val id = track.videoId
            if (id.isBlank() || track.isLocal) continue
            if (!seen.add(id)) continue
            rows += listOf(
                id,
                clean(track.title),
                clean(track.artist),
                (track.durationMs / 1000).coerceAtLeast(0).toString(),
            ).joinToString(SEP.toString())
        }
        if (rows.isEmpty()) return ""
        val whole = pack(rows)
        if (whole.length <= MAX_CHARS) return whole

        // Deflate is not strictly monotonic — a repeated tail can compress
        // better than a shorter one — so scan every prefix and keep the
        // longest that fits. Fifty deflates of a few KB is still microseconds.
        var best = ""
        for (n in 1 until rows.size) {
            val candidate = pack(rows.take(n))
            if (candidate.length <= MAX_CHARS) best = candidate
        }
        // A single row is always small enough; if even that did not fit, best
        // stays empty and the sheet says so honestly rather than drawing a
        // symbol that cannot be scanned.
        return best
    }

    /** How many tracks a code actually carries, for the sheet's label. */
    fun count(code: String): Int = decode(code)?.size ?: 0

    private fun pack(rows: List<String>): String =
        PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(deflate(rows.joinToString("\n")))

    /**
     * Reads a code back. Null means "this is not a queue code, or it did not
     * survive the trip" — never a half-parsed list.
     */
    fun decode(code: String): List<Item>? {
        val body = code.filterNot { it.isWhitespace() }
        if (!body.startsWith(PREFIX)) return null
        val packed = body.removePrefix(PREFIX)
        if (packed.isEmpty()) return null

        val raw = try {
            inflate(Base64.getUrlDecoder().decode(pad(packed)))
        } catch (e: IllegalArgumentException) {
            return null
        } catch (e: java.util.zip.DataFormatException) {
            return null
        }

        val items = ArrayList<Item>()
        for (line in raw.split('\n')) {
            if (line.isBlank()) continue
            val parts = line.split(SEP)
            if (parts.size < 4) continue
            val id = parts[0].trim()
            if (id.isEmpty()) continue
            items += Item(
                videoId = id,
                title = parts[1].ifBlank { "Unknown track" },
                artist = parts[2],
                durationMs = parts[3].toLongOrNull()?.times(1000) ?: 0L,
            )
            if (items.size >= MAX_TRACKS) break
        }
        return items.ifEmpty { null }
    }

    /** True when [text] looks like a queue code, without paying for the inflate. */
    fun looks(text: String): Boolean {
        val body = text.filterNot { it.isWhitespace() }
        return body.startsWith(PREFIX) && body.length > PREFIX.length + 8
    }

    /**
     * Rebuilds playable tracks. Artwork comes from the id, so a received queue
     * has covers before any of it has been played or resolved.
     */
    fun toTracks(items: List<Item>): List<Track> = items.map { item ->
        Track(
            // Same shape as everywhere else in the app, so a received track
            // lands in likes, history and downloads instead of beside them.
            id = "yt:${item.videoId}",
            title = item.title,
            artist = item.artist,
            artUrl = "https://i.ytimg.com/vi/${item.videoId}/hqdefault.jpg",
            durationMs = item.durationMs,
            videoId = item.videoId,
        )
    }

    // ---------------------------------------------------------------- guts

    private fun clean(s: String): String {
        val flat = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
        val squeezed = buildString {
            var space = false
            for (c in flat) {
                if (c == ' ') {
                    if (!space) append(c)
                    space = true
                } else {
                    append(c)
                    space = false
                }
            }
        }
        return if (squeezed.length <= MAX_FIELD) squeezed else squeezed.take(MAX_FIELD - 1) + "…"
    }

    private fun deflate(text: String): ByteArray {
        val deflater = Deflater(9, false)
        return try {
            val input = text.toByteArray(Charsets.UTF_8)
            deflater.setInput(input)
            deflater.finish()
            val out = ByteArrayOutputStream(input.size)
            val buffer = ByteArray(4096)
            while (!deflater.finished()) {
                val n = deflater.deflate(buffer)
                if (n == 0) break
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(bytes: ByteArray): String {
        val inflater = Inflater(false)
        return try {
            inflater.setInput(bytes)
            val out = ByteArrayOutputStream(bytes.size * 4)
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0) break
                out.write(buffer, 0, n)
            }
            // A cut-off code still inflates a prefix of rows without complaint.
            // Half a queue presented as a whole one is worse than a refusal.
            if (!inflater.finished()) throw java.util.zip.DataFormatException("truncated code")
            out.toString("UTF-8")
        } finally {
            inflater.end()
        }
    }

    private fun pad(s: String) = s + "=".repeat((4 - s.length % 4) % 4)
}
