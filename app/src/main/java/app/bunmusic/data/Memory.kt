package app.bunmusic.data

import java.time.LocalDate

/**
 * Memory: what this app knows about *you*, as opposed to what it can play.
 *
 * Two things live here, and both are arithmetic over the listening log rather
 * than guesses:
 *
 * - **On this day** — the same calendar date in earlier years. Real plays, not a
 *   "memory" generated from a template.
 * - **Listening DNA** — five indices computed from the last [WINDOW_DAYS] of
 *   plays. These describe *behaviour*, not the music: nothing here claims to
 *   know whether a track is happy or fast, because the app does not analyse
 *   audio and pretending otherwise would be the one dishonest thing on the
 *   screen.
 *
 * Everything takes `now` and the zone offset as arguments instead of reading the
 * clock, which is what makes a year boundary testable.
 */
object Memory {

    const val DAY = 86_400_000L

    /** Below this many plays a DNA profile is noise, and showing it would be a lie. */
    const val MIN_PLAYS = 20

    /** The DNA describes the recent you, not the you of three years ago. */
    const val WINDOW_DAYS = 90

    /** How far back "on this day" looks. */
    const val YEARS_BACK = 5

    // ------------------------------------------------------------- on this day

    data class DayMemory(
        val yearsAgo: Int,
        val epochDay: Long,
        val tracks: List<Track>,
        val plays: Int,
    )

    /**
     * The same date in each of the last [yearsBack] years, newest first.
     *
     * Distinct tracks, most recently played first: a day you played one song
     * eleven times is a memory of one song, not of eleven.
     *
     * A leap day simply does not appear in non-leap years — [LocalDate.minusYears]
     * clamps 29 February to the 28th, and clamping would silently invent a
     * memory, so the day is compared exactly.
     */
    fun onThisDay(
        events: List<Listening.Event>,
        resolve: (String) -> Track?,
        nowMs: Long,
        offsetMs: Long = 0L,
        yearsBack: Int = YEARS_BACK,
        minTracks: Int = 1,
    ): List<DayMemory> {
        if (events.isEmpty() || yearsBack <= 0) return emptyList()
        val today = LocalDate.ofEpochDay(Listening.dayIndex(nowMs, offsetMs))
        val byDay = events.groupBy { Listening.dayIndex(it.atMs, offsetMs) }

        val out = ArrayList<DayMemory>(yearsBack)
        for (years in 1..yearsBack) {
            val date = today.minusYears(years.toLong())
            // A 29 February memory would otherwise be reported on the 28th of a
            // common year, which is a different day. `minusYears` clamps, so the
            // clamp is detected rather than trusted.
            if (date.monthValue != today.monthValue || date.dayOfMonth != today.dayOfMonth) continue
            val day = date.toEpochDay()
            val onDay = byDay[day] ?: continue

            val seen = LinkedHashSet<String>()
            val tracks = ArrayList<Track>(4)
            for (e in onDay.sortedByDescending { it.atMs }) {
                if (seen.add(e.trackId)) resolve(e.trackId)?.let { tracks += it }
            }
            if (tracks.size >= minTracks) {
                out += DayMemory(yearsAgo = years, epochDay = day, tracks = tracks, plays = onDay.size)
            }
        }
        return out
    }

    // -------------------------------------------------------------------- DNA

    data class Axis(
        val label: String,
        /** 0..100. */
        val value: Int,
        /** What the number actually counts, in six words or fewer. */
        val hint: String,
    )

    data class Dna(
        val plays: Int,
        val days: Int,
        /** Plays of tracks heard exactly once: how much you are exploring. */
        val curiosity: Int,
        /** Plays of tracks heard four times or more: how much you are nesting. */
        val loyalty: Int,
        /** Distinct artists per distinct track, scaled so 0.8 is a wide palette. */
        val range: Int,
        /** Plays between 22:00 and 04:00. */
        val night: Int,
        /** Days with at least one play, out of the window. */
        val consistency: Int,
    ) {
        val axes: List<Axis>
            get() = listOf(
                Axis("Curiosity", curiosity, "tracks heard once"),
                Axis("Loyalty", loyalty, "tracks heard 4+ times"),
                Axis("Range", range, "artists per track"),
                Axis("Night", night, "plays after 22:00"),
                Axis("Consistency", consistency, "days with a play"),
            )

        val strongest: Axis get() = axes.maxBy { it.value }
        val weakest: Axis get() = axes.minBy { it.value }
    }

    /**
     * The last [days] days, as five indices.
     *
     * Returns null below [MIN_PLAYS]: a radar chart drawn from nine plays is a
     * shape, not a description, and a shape that looks authoritative is worse
     * than an honest "not yet".
     */
    fun dna(
        events: List<Listening.Event>,
        resolve: (String) -> Track?,
        nowMs: Long,
        offsetMs: Long = 0L,
        days: Int = WINDOW_DAYS,
    ): Dna? {
        val span = days.coerceAtLeast(1)
        val since = nowMs - span * DAY
        val window = events.filter { it.atMs in since..nowMs }
        val plays = window.size
        if (plays < MIN_PLAYS) return null

        val perTrack = HashMap<String, Int>()
        for (e in window) perTrack[e.trackId] = (perTrack[e.trackId] ?: 0) + 1

        val once = perTrack.count { it.value == 1 }
        val four = perTrack.filterValues { it >= 4 }

        val oncePlays = perTrack.filterValues { it == 1 }.values.sum()
        val fourPlays = four.values.sum()

        val artists = perTrack.keys
            .mapNotNull { resolve(it)?.artist?.trim()?.ifEmpty { null } }
            .toSet()
        val distinctTracks = perTrack.size

        val nightPlays = window.count { e ->
            val h = Wrapped.hourOf(e.atMs, offsetMs)
            h >= 22 || h < 4
        }

        val activeDays = window.map { Listening.dayIndex(it.atMs, offsetMs) }.toSet().size

        return Dna(
            plays = plays,
            days = activeDays,
            curiosity = pct(oncePlays, plays),
            loyalty = pct(fourPlays, plays),
            range = pct(artists.size, (distinctTracks * 0.8f).toInt().coerceAtLeast(1)),
            night = pct(nightPlays, plays),
            consistency = pct(activeDays, span),
        )
    }

    private fun pct(part: Int, whole: Int): Int {
        if (whole <= 0) return 0
        return ((part.toFloat() / whole) * 100f).toInt().coerceIn(0, 100)
    }

    // ----------------------------------------------------------------- streak

    /**
     * Consecutive days ending today — or ending yesterday, if today is still
     * young and nothing has been played yet.
     *
     * The second case matters: a streak that breaks at midnight and repairs
     * itself at breakfast is not a streak, it is a reason to stop trusting the
     * number.
     */
    fun currentStreak(days: kotlin.collections.Collection<Long>, today: Long): Int {
        if (days.isEmpty()) return 0
        val set = days.toHashSet()
        var cursor = if (set.contains(today)) today else today - 1
        if (!set.contains(cursor)) return 0
        var n = 0
        while (set.contains(cursor)) {
            n++
            cursor--
        }
        return n
    }

    // ------------------------------------------------------------------ radar

    /**
     * Points of a radar polygon, relative to its centre.
     *
     * Five axes, first one straight up, the rest clockwise. Kept out of the
     * composable so that "a zero sits at the centre and a hundred sits on the
     * rim" is a test rather than an eyeball.
     */
    fun radar(values: List<Int>, radius: Float): List<Pair<Float, Float>> {
        if (values.isEmpty() || radius <= 0f) return emptyList()
        val step = (2.0 * Math.PI / values.size).toFloat()
        val start = (-Math.PI / 2.0).toFloat()
        return values.mapIndexed { i, v ->
            val a = start + step * i
            val r = radius * (v.coerceIn(0, 100) / 100f)
            kotlin.math.cos(a) * r to kotlin.math.sin(a) * r
        }
    }

    /** "3 years ago", "last year", "yesterday" — the label a person would use. */
    fun ago(yearsAgo: Int): String = when (yearsAgo) {
        1 -> "a year ago"
        2 -> "two years ago"
        3 -> "three years ago"
        4 -> "four years ago"
        else -> "$yearsAgo years ago"
    }
}
