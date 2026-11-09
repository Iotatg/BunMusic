package app.bunmusic.data

/**
 * The play log.
 *
 * [Store] keeps counters — "this artist, 41 plays" — which answers "who is my
 * top artist of all time" and nothing else. It cannot answer "what did I play
 * in March" or "do I listen at 2 am", and those are exactly the questions the
 * period charts and the year in review ask.
 *
 * So every play also lands here with its timestamp. The log is a ring: capped at
 * [CAP] events, newest first, oldest dropped. A cap is not a compromise — an
 * unbounded log inside SharedPreferences would eventually be a megabyte string
 * rewritten on every play.
 *
 * Everything in this file except [encode]/[decode] is pure, so the arithmetic is
 * testable without a device or a clock.
 */
object Listening {

    /** One play. [ms] is what the track claimed to be worth, not how long it was listened to. */
    data class Event(val trackId: String, val atMs: Long, val ms: Long)

    /** ~1200 plays: a heavy year, and about 45 KB of prefs. */
    const val CAP = 1200

    const val DAY = 86_400_000L

    private const val SEP = '\u0001'

    fun encode(e: Event): String = "${e.trackId}$SEP${e.atMs}$SEP${e.ms}"

    fun decode(line: String): Event? {
        val p = line.split(SEP)
        if (p.size < 3 || p[0].isBlank()) return null
        return Event(p[0], p[1].toLongOrNull() ?: return null, p[2].toLongOrNull() ?: 0L)
    }

    /**
     * Put [event] at the front and trim the tail.
     *
     * A replay of the same track within the same minute replaces the previous
     * entry instead of adding a second one: pausing and resuming a track fires
     * the play callback again, and a log that counts a resume as a play inflates
     * every number on the Wrapped screen.
     */
    fun append(log: List<Event>, event: Event, cap: Int = CAP): List<Event> {
        val last = log.firstOrNull()
        val fresh = if (last != null &&
            last.trackId == event.trackId &&
            event.atMs - last.atMs in 0..60_000L
        ) {
            log.drop(1)
        } else {
            log
        }
        return (listOf(event) + fresh).take(cap)
    }

    /** Plays inside `[fromMs, toMs)`, newest first. */
    fun inRange(events: List<Event>, fromMs: Long, toMs: Long): List<Event> =
        events.filter { it.atMs >= fromMs && it.atMs < toMs }

    /**
     * Plays per local day, oldest first, [days] buckets ending today.
     *
     * [offsetMs] is the zone offset at read time, passed in rather than read
     * from the device so a test can pin it. Day boundaries are local: a play at
     * 00:30 belongs to the new day, and `atMs / DAY` in UTC would file it under
     * yesterday for half the planet.
     */
    fun daily(
        events: List<Event>,
        days: Int,
        nowMs: Long,
        offsetMs: Long = 0L,
    ): List<Int> {
        if (days <= 0) return emptyList()
        val today = dayIndex(nowMs, offsetMs)
        val out = IntArray(days)
        for (e in events) {
            val back = (today - dayIndex(e.atMs, offsetMs)).toInt()
            if (back in 0 until days) out[days - 1 - back] += 1
        }
        return out.toList()
    }

    /**
     * Plays per calendar month of [year], January first.
     *
     * Uses the same offset argument as [daily] for the same reason.
     */
    fun monthly(events: List<Event>, year: Int, offsetMs: Long = 0L): List<Int> {
        val out = IntArray(12)
        for (e in events) {
            // localDate reads the pseudo-UTC timestamp back in UTC, so the offset
            // is applied exactly once. The old Calendar(device zone) applied it a
            // second time and pushed plays near midnight into the wrong month/year.
            val date = localDate(e.atMs, offsetMs)
            if (date.year != year) continue
            out[date.monthValue - 1] += 1
        }
        return out.toList()
    }

    /** Plays per hour of the local day, 00:00 first. */
    fun hourly(events: List<Event>, offsetMs: Long = 0L): List<Int> {
        val out = IntArray(24)
        for (e in events) {
            val local = localMillis(e.atMs, offsetMs)
            out[((local % DAY + DAY) % DAY / 3_600_000L).toInt().coerceIn(0, 23)] += 1
        }
        return out.toList()
    }

    /** The busiest hour, 0..23, or null for an empty log. */
    fun peakHour(events: List<Event>, offsetMs: Long = 0L): Int? {
        val hours = hourly(events, offsetMs)
        val max = hours.maxOrNull() ?: 0
        if (max == 0) return null
        return hours.indexOf(max)
    }

    /** Artist name -> plays, highest first. Unknown ids are skipped, not counted as "". */
    fun topArtists(
        events: List<Event>,
        resolve: (String) -> Track?,
        limit: Int = 6,
    ): List<Pair<String, Int>> {
        val counts = LinkedHashMap<String, Int>()
        for (e in events) {
            val artist = resolve(e.trackId)?.artist?.trim().orEmpty()
            if (artist.isEmpty()) continue
            counts[artist] = (counts[artist] ?: 0) + 1
        }
        return counts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }

    /** Track id -> plays, for the same window. */
    fun topTracks(
        events: List<Event>,
        resolve: (String) -> Track?,
        limit: Int = 6,
    ): List<Pair<Track, Int>> {
        val counts = LinkedHashMap<String, Int>()
        for (e in events) counts[e.trackId] = (counts[e.trackId] ?: 0) + 1
        return counts.entries
            .sortedByDescending { it.value }
            .mapNotNull { (id, n) -> resolve(id)?.let { it to n } }
            .take(limit)
    }

    /** Album title -> plays. Tracks with no album are skipped rather than grouped under "". */
    fun topAlbums(
        events: List<Event>,
        resolve: (String) -> Track?,
        limit: Int = 4,
    ): List<Pair<String, Int>> {
        val counts = LinkedHashMap<String, Int>()
        for (e in events) {
            val album = resolve(e.trackId)?.album?.trim().orEmpty()
            if (album.isEmpty()) continue
            counts[album] = (counts[album] ?: 0) + 1
        }
        return counts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }

    /**
     * Local wall-clock time expressed as a pseudo-UTC epoch millis.
     *
     * A saturating add keeps a corrupt `atMs` (or a hostile offset) from
     * wrapping Long and silently inventing a date decades away.
     */
    fun localMillis(atMs: Long, offsetMs: Long): Long {
        val sum = atMs + offsetMs
        // Signed-overflow check: operands share a sign but the result does not.
        // Saturate in the direction of the offset that pushed us over.
        if (((atMs xor sum) and (offsetMs xor sum)) < 0L) {
            return if (offsetMs > 0) Long.MAX_VALUE else Long.MIN_VALUE
        }
        return sum
    }

    fun dayIndex(atMs: Long, offsetMs: Long): Long =
        Math.floorDiv(localMillis(atMs, offsetMs), DAY)

    /**
     * The local calendar date of a play.
     *
     * `atMs + offsetMs` is already local wall-clock time written as if it were
     * UTC, so it must be read back in UTC. A device-zone Calendar (the old
     * implementation of monthly/yearOf) applied the offset a *second* time and
     * filed plays near midnight under the wrong month or year.
     *
     * dayIndex is bounded by Long/DAY, well inside LocalDate's supported
     * epoch-day range, so ofEpochDay cannot throw even for corrupt input.
     */
    fun localDate(atMs: Long, offsetMs: Long): java.time.LocalDate =
        java.time.LocalDate.ofEpochDay(dayIndex(atMs, offsetMs))

    /** Local offset of the device right now, in ms east of UTC. */
    fun zoneOffset(nowMs: Long = System.currentTimeMillis()): Long =
        java.util.TimeZone.getDefault().getOffset(nowMs).toLong()
}
