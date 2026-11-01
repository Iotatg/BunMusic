package app.bunmusic.data

import java.util.Calendar
import java.util.TimeZone

/**
 * The part of the app that decides what to play next when nobody asked.
 *
 * Spotify's home screen is mostly arithmetic over what you already did: a handful
 * of artist mixes, a "because you liked" shelf, a time capsule, a shuffle that
 * avoids the song you skipped twice. None of that needs a server, and none of it
 * needs a second copy of the truth — everything here is derived from [Store] at
 * read time.
 *
 * Every function is pure and takes its inputs explicitly, so the whole engine can
 * be tested without a device, a clock or a network.
 */
object Discovery {

    const val DAY = 86_400_000L

    /** One generated playlist. `accent` is a stable colour seed, not a colour. */
    data class Mix(
        val id: String,
        val title: String,
        val subtitle: String,
        val tracks: List<Track>,
        val accent: Int = 0,
    )

    /** What the app thinks you listen to. */
    data class Taste(
        val artists: List<Pair<String, Int>>,
        val topArtist: String?,
        val depth: Int,
        val plays: Int,
        val newShare: Float,
    )

    enum class Mood(val label: String, val line: String, val query: String) {
        MORNING("Morning", "Slow start, warm songs", "morning acoustic"),
        AFTERNOON("Afternoon", "Something with a pulse", "afternoon upbeat"),
        EVENING("Evening", "The light is going", "evening chill"),
        NIGHT("Night", "Low volume, low lights", "late night lofi"),
    }

    fun artistOf(track: Track): String =
        track.artist.substringBefore(',').substringBefore('&').trim().ifEmpty { "Unknown artist" }

    private fun sameArtist(a: String, b: String): Boolean = a.trim().equals(b.trim(), ignoreCase = true)

    // ------------------------------------------------------------------ taste

    /**
     * Artist affinity: a play is worth one, a like is worth two. A like says more
     * than a play — you chose it, it did not merely come next.
     */
    fun taste(
        history: List<HistoryItem>,
        liked: List<Track>,
        limit: Int = 6,
    ): Taste {
        val score = LinkedHashMap<String, Int>()
        history.forEach { h ->
            val a = artistOf(h.track)
            score[a] = (score[a] ?: 0) + 1
        }
        liked.forEach { t ->
            val a = artistOf(t)
            score[a] = (score[a] ?: 0) + 2
        }
        val ranked = score.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .map { it.key to it.value }
        return Taste(
            artists = ranked.take(limit),
            topArtist = ranked.firstOrNull()?.first,
            depth = ranked.size,
            plays = history.size,
            newShare = discoveryShare(history),
        )
    }

    /**
     * Six mixes, one per artist you actually return to.
     *
     * A mix is built from that artist's tracks first, then padded with liked
     * songs from other artists so the list is long enough to shuffle. The padding
     * is sorted by affinity, never random, so the same library produces the same
     * mix twice.
     */
    fun mixes(
        history: List<HistoryItem>,
        liked: List<Track>,
        all: List<Track>,
        limit: Int = 6,
        size: Int = 30,
    ): List<Mix> {
        if (all.isEmpty()) return emptyList()
        val taste = taste(history, liked, limit)
        val plays = history.groupingBy { it.track.id }.eachCount()
        val out = ArrayList<Mix>()

        taste.artists.forEachIndexed { i, (artist, weight) ->
            val own = all.filter { sameArtist(artistOf(it), artist) }
                .sortedWith(compareByDescending<Track> { plays[it.id] ?: 0 }.thenBy { it.title.lowercase() })
            if (own.isEmpty()) return@forEachIndexed
            val pad = liked
                .filterNot { sameArtist(artistOf(it), artist) }
                .sortedBy { it.id }
            val tracks = (own + pad).distinctBy { it.id }.take(size)
            if (tracks.size < 3) return@forEachIndexed
            val number = i + 1
            out += Mix(
                id = "mix:${artist.lowercase().replace(' ', '-')}",
                title = if (number == 1) "Daily Mix 1 · $artist" else "Mix $number · $artist",
                subtitle = "${own.size} by $artist · affinity $weight",
                tracks = tracks,
                accent = i,
            )
            if (out.size >= limit) return out
        }
        return out
    }

    // ------------------------------------------------------------ shelves

    /**
     * "Because you liked X" — the artist you liked most, minus the songs you
     * already liked. Returns null when there is not enough to fill a shelf: an
     * empty shelf is worse than no shelf.
     */
    fun becauseYouLiked(
        liked: List<Track>,
        all: List<Track>,
        history: List<HistoryItem>,
        limit: Int = 20,
    ): Pair<String, List<Track>>? {
        if (liked.isEmpty()) return null
        val counts = liked.groupingBy { artistOf(it) }.eachCount()
        val artist = counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .firstOrNull()?.key ?: return null
        val likedIds = liked.map { it.id }.toSet()
        val heard = history.map { it.track.id }.toSet()
        val tracks = all
            .filter { sameArtist(artistOf(it), artist) && it.id !in likedIds }
            .sortedBy { if (it.id in heard) 1 else 0 }
            .take(limit)
        return if (tracks.size >= 3) artist to tracks else null
    }

    /**
     * "On this day" — what you were playing on the same date in earlier years.
     * Falls back to the same calendar month when the exact day is empty, because
     * a time capsule that is empty eleven months of the year is not a feature.
     */
    fun timeCapsule(
        history: List<HistoryItem>,
        now: Long = System.currentTimeMillis(),
        offsetMs: Int = 0,
        limit: Int = 20,
    ): List<Track> {
        if (history.isEmpty()) return emptyList()
        val zone = TimeZone.getTimeZone("UTC")
        fun cal(at: Long) = Calendar.getInstance(zone).apply { timeInMillis = at + offsetMs }
        val today = cal(now)
        val thisYear = today.get(Calendar.YEAR)
        val month = today.get(Calendar.MONTH)
        val day = today.get(Calendar.DAY_OF_MONTH)

        val exact = history.filter { h ->
            val c = cal(h.atMs)
            c.get(Calendar.YEAR) < thisYear &&
                c.get(Calendar.MONTH) == month &&
                c.get(Calendar.DAY_OF_MONTH) == day
        }
        val pool = exact.ifEmpty {
            history.filter { h ->
                val c = cal(h.atMs)
                c.get(Calendar.YEAR) < thisYear && c.get(Calendar.MONTH) == month
            }
        }
        return pool.sortedByDescending { it.atMs }.distinctBy { it.track.id }.take(limit).map { it.track }
    }

    /** The same month, a year or more ago. Used by the "then and now" card. */
    fun lastYearMonth(
        history: List<HistoryItem>,
        now: Long = System.currentTimeMillis(),
        offsetMs: Int = 0,
    ): Int {
        val zone = TimeZone.getTimeZone("UTC")
        val nowCal = Calendar.getInstance(zone).apply { timeInMillis = now + offsetMs }
        val year = nowCal.get(Calendar.YEAR)
        val month = nowCal.get(Calendar.MONTH)
        return history.count {
            val c = Calendar.getInstance(zone).apply { timeInMillis = it.atMs + offsetMs }
            c.get(Calendar.YEAR) == year - 1 && c.get(Calendar.MONTH) == month
        }
    }

    // ------------------------------------------------------------- shuffle

    private fun stableHash(id: String, seed: Int): Int {
        var h = seed * 31 + 17
        id.forEach { h = h * 31 + it.code }
        return h
    }

    /**
     * Smart shuffle.
     *
     * Three rules, in order: songs you skipped three times are dropped, songs you
     * heard in the last [recentIds] are moved behind everything else, and no two
     * neighbours share an artist. The last rule is what makes a shuffle sound
     * hand-made — a random order clusters three songs by the same band and the
     * listener notices immediately.
     */
    fun smartShuffle(
        tracks: List<Track>,
        recentIds: Set<String> = emptySet(),
        skips: Map<String, Int> = emptyMap(),
        seed: Int = 0,
    ): List<Track> {
        val cleaned = tracks.distinctBy { it.id }.filter { (skips[it.id] ?: 0) < 3 }
        if (cleaned.size < 2) return cleaned
        val ordered = if (seed == 0) cleaned else cleaned.sortedBy { stableHash(it.id, seed) }
        val pool = ordered.filterNot { recentIds.contains(it.id) } +
            ordered.filter { recentIds.contains(it.id) }
        val rest = pool.toMutableList()
        val out = ArrayList<Track>(rest.size)
        var last = ""
        while (rest.isNotEmpty()) {
            // Pick the artist with the most songs left, never the one that just
            // played. Greedy "first different" strands the last two of a kind at
            // the end; spending the biggest pile first is what avoids that.
            val counts = rest.groupingBy { artistOf(it) }.eachCount()
            val candidates = rest.withIndex().filter { !sameArtist(artistOf(it.value), last) }
            val idx = if (candidates.isEmpty()) {
                0
            } else {
                candidates.maxByOrNull { counts[artistOf(it.value)] ?: 0 }!!.index
            }
            val t = rest.removeAt(idx)
            out += t
            last = artistOf(t)
        }
        return out
    }

    // ------------------------------------------------------------- shelves 2

    /** Liked songs interleaved with the artists you follow. Two of yours, one of theirs. */
    fun blend(
        liked: List<Track>,
        all: List<Track>,
        follows: Set<String>,
        limit: Int = 40,
    ): List<Track> {
        if (follows.isEmpty()) return emptyList()
        val likedIds = liked.map { it.id }.toSet()
        val theirs = all.filter { t ->
            follows.any { sameArtist(it, artistOf(t)) } && t.id !in likedIds
        }.distinctBy { it.id }
        if (theirs.isEmpty()) return emptyList()
        val out = ArrayList<Track>()
        var i = 0
        var j = 0
        while (out.size < limit && (i < liked.size || j < theirs.size)) {
            repeat(2) {
                if (i < liked.size && out.size < limit) out += liked[i++]
            }
            if (j < theirs.size && out.size < limit) out += theirs[j++]
        }
        return out.distinctBy { it.id }
    }

    /** The best song to start a radio from, skipping whatever is playing now. */
    fun radioSeed(
        liked: List<Track>,
        history: List<HistoryItem>,
        currentId: String? = null,
    ): Track? {
        val counts = history.groupingBy { it.track.id }.eachCount()
        return (liked + history.map { it.track })
            .distinctBy { it.id }
            .filter { it.id != currentId && it.videoId.isNotBlank() }
            .sortedWith(compareByDescending<Track> { counts[it.id] ?: 0 }.thenBy { it.title.lowercase() })
            .firstOrNull()
    }

    /** Songs by artists you like that you have never opened. */
    fun freshFinds(
        liked: List<Track>,
        all: List<Track>,
        history: List<HistoryItem>,
        limit: Int = 20,
    ): List<Track> {
        if (liked.isEmpty()) return emptyList()
        val artists = liked.map { artistOf(it) }.toSet()
        val heard = (liked.map { it.id } + history.map { it.track.id }).toSet()
        return all
            .filter { artistOf(it) in artists && it.id !in heard }
            .sortedBy { it.title.lowercase() }
            .take(limit)
    }

    /** Played exactly once, ever. The songs you liked enough to try and then forgot. */
    fun deepCuts(all: List<Track>, counts: Map<String, Int>, limit: Int = 20): List<Track> =
        all.filter { counts[it.id] == 1 }.sortedBy { it.title.lowercase() }.take(limit)

    // --------------------------------------------------------------- numbers

    /**
     * Share of plays this month that went to an artist outside your all-time top
     * five. A crude "how adventurous were you" number, and honest about being crude.
     */
    fun discoveryShare(
        history: List<HistoryItem>,
        now: Long = System.currentTimeMillis(),
        days: Int = 30,
    ): Float {
        val since = now - days * DAY
        val window = history.filter { it.atMs >= since }
        if (window.isEmpty()) return 0f
        val allTime = history.groupingBy { artistOf(it.track) }.eachCount()
        val top = allTime.entries.sortedByDescending { it.value }.take(5).map { it.key }.toSet()
        val newPlays = window.count { artistOf(it.track) !in top }
        return newPlays.toFloat() / window.size
    }

    fun topArtistThisWeek(
        history: List<HistoryItem>,
        now: Long = System.currentTimeMillis(),
    ): Pair<String, Int>? {
        val since = now - 7 * DAY
        return history
            .filter { it.atMs >= since }
            .groupingBy { artistOf(it.track) }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
            .firstOrNull()
            ?.let { it.key to it.value }
    }

    fun hourMood(hour: Int): Mood = when (hour) {
        in 5..11 -> Mood.MORNING
        in 12..16 -> Mood.AFTERNOON
        in 17..21 -> Mood.EVENING
        else -> Mood.NIGHT
    }
}
