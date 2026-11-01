package app.bunmusic.data

/**
 * The year in review.
 *
 * A Wrapped screen is only worth building if the numbers are real, so this file
 * contains no layout and no colours — just arithmetic over the [Listening] log.
 * The screen turns a [Summary] into cards, the share card turns the same summary
 * into pixels, and both read the same numbers, so the image and the screen can
 * never disagree.
 *
 * [summarise] takes the zone offset and the year as arguments instead of reading
 * the clock, which is what makes it testable.
 */
object Wrapped {

    /** Below this many plays there is no story to tell, and inventing one would be a lie. */
    const val MIN_PLAYS = 10

    data class Summary(
        val year: Int,
        val plays: Int,
        val minutes: Long,
        val artists: Int,
        val tracks: Int,
        val topArtist: Pair<String, Int>?,
        val topTrack: Pair<Track, Int>?,
        val topAlbum: Pair<String, Int>?,
        /** Epoch day of the busiest day, and how many plays it held. */
        val busiestDay: Pair<Long, Int>?,
        /** Longest run of consecutive days with at least one play. */
        val streak: Int,
        /** Plays between 22:00 and 04:00. */
        val nightPlays: Int,
        /** Tracks played exactly once — the things you tried. */
        val discoveries: Int,
        /** Month index 0..11 and its play count. */
        val topMonth: Pair<Int, Int>?,
    ) {
        val hasStory: Boolean get() = plays >= MIN_PLAYS

        /** Top artist's share of all plays, 0..1. */
        val topArtistShare: Float
            get() = topArtist?.let { if (plays > 0) it.second.toFloat() / plays else 0f } ?: 0f

        val nightShare: Float get() = if (plays > 0) nightPlays.toFloat() / plays else 0f
    }

    data class Card(
        val kind: Kind,
        val kicker: String,
        val headline: String,
        val body: String,
        /** 0..3, picked by the screen for a gradient. */
        val accent: Int,
    )

    enum class Kind { INTRO, MINUTES, ARTIST, TRACK, ALBUM, DAY, STREAK, NIGHT, DISCOVERY, OUTRO }

    fun summarise(
        events: List<Listening.Event>,
        resolve: (String) -> Track?,
        year: Int,
        offsetMs: Long = 0L,
    ): Summary {
        val inYear = events.filter { yearOf(it.atMs, offsetMs) == year }
        val plays = inYear.size

        val perTrack = LinkedHashMap<String, Int>()
        for (e in inYear) perTrack[e.trackId] = (perTrack[e.trackId] ?: 0) + 1

        val resolved = perTrack.keys.mapNotNull { id -> resolve(id)?.let { id to it } }.toMap()

        val topTrack = perTrack.entries
            .sortedByDescending { it.value }
            .mapNotNull { (id, n) -> resolved[id]?.let { it to n } }
            .firstOrNull()

        val artists = perTrack.keys.mapNotNull { resolved[it]?.artist?.trim()?.ifEmpty { null } }.toSet()
        val topArtist = Listening.topArtists(inYear, resolve, limit = 1).firstOrNull()
        val topAlbum = Listening.topAlbums(inYear, resolve, limit = 1).firstOrNull()

        val minutes = inYear.sumOf { it.ms }.coerceAtLeast(0L) / 60_000L
        val discoveries = perTrack.count { it.value == 1 }

        val dayCounts = LinkedHashMap<Long, Int>()
        for (e in inYear) {
            val d = Listening.dayIndex(e.atMs, offsetMs)
            dayCounts[d] = (dayCounts[d] ?: 0) + 1
        }
        val busiestDay = dayCounts.entries
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.let { it.key to it.value }

        val months = Listening.monthly(inYear, year, offsetMs)
        val topMonth = months.withIndex()
            .maxByOrNull { it.value }
            ?.takeIf { it.value > 0 }
            ?.let { it.index to it.value }

        val night = inYear.count { hourOf(it.atMs, offsetMs) in NIGHT_HOURS }

        return Summary(
            year = year,
            plays = plays,
            minutes = minutes,
            artists = artists.size,
            tracks = perTrack.size,
            topArtist = topArtist,
            topTrack = topTrack,
            topAlbum = topAlbum,
            busiestDay = busiestDay,
            streak = longestStreak(dayCounts.keys.toList()),
            nightPlays = night,
            discoveries = discoveries,
            topMonth = topMonth,
        )
    }

    /** 22:00 through 03:59 — the hours people are surprised by. */
    private val NIGHT_HOURS = setOf(22, 23, 0, 1, 2, 3)

    /**
     * Longest run of consecutive days.
     *
     * Sorted first, because the log is newest-first and a streak counted in log
     * order would restart on every out-of-order play.
     */
    fun longestStreak(days: List<Long>): Int {
        if (days.isEmpty()) return 0
        val sorted = days.distinct().sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] - sorted[i - 1] == 1L) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    /**
     * The reel.
     *
     * [monthName] and [dayLabel] are injected so this stays a pure function and
     * the test can use "March" instead of whatever locale the runner has.
     */
    fun cards(
        s: Summary,
        monthName: (Int) -> String = { DEFAULT_MONTHS[it.coerceIn(0, 11)] },
        dayLabel: (Long) -> String = { "$it" },
    ): List<Card> {
        val out = ArrayList<Card>(10)
        out += Card(
            Kind.INTRO, "${s.year} in review", "${s.plays} plays",
            if (s.tracks == 1) "One song, over and over." else "Across ${s.tracks} tracks.",
            0,
        )
        out += Card(
            Kind.MINUTES, "Time listened", fmtMinutes(s.minutes),
            when {
                s.minutes >= 60 * 24 -> "That is ${"%.1f".format(s.minutes / 1440f)} days of music."
                s.minutes >= 60 -> "About ${s.minutes / 60} hours, one song at a time."
                else -> "A short year. The next one is longer."
            },
            1,
        )
        s.topArtist?.let { (name, n) ->
            out += Card(
                Kind.ARTIST, "Top artist", name,
                "$n plays · ${(n * 100f / s.plays).toInt()}% of everything you played.",
                2,
            )
        }
        s.topTrack?.let { (track, n) ->
            out += Card(
                Kind.TRACK, "On repeat", track.title,
                "${track.artist} · $n plays${if (n >= 10) " — you really meant it" else ""}.",
                3,
            )
        }
        s.topAlbum?.let { (album, n) ->
            out += Card(Kind.ALBUM, "Album of the year", album, "$n plays.", 0)
        }
        s.busiestDay?.let { (day, n) ->
            out += Card(
                Kind.DAY, "Busiest day", dayLabel(day),
                "$n plays in one day. ${if (n >= 40) "That was a whole afternoon." else "A good day."}",
                1,
            )
        }
        if (s.streak >= 3) {
            out += Card(
                Kind.STREAK, "Longest streak", "${s.streak} days",
                "Not a single day without a song.",
                2,
            )
        }
        if (s.nightPlays > 0) {
            out += Card(
                Kind.NIGHT, "After midnight", "${(s.nightShare * 100).toInt()}%",
                "${s.nightPlays} plays between 10 pm and 4 am.",
                3,
            )
        }
        if (s.discoveries > 0) {
            out += Card(
                Kind.DISCOVERY, "New to you", "${s.discoveries}",
                if (s.discoveries == 1) "One track you played exactly once."
                else "${s.discoveries} tracks you played exactly once.",
                0,
            )
        }
        s.topMonth?.let { (m, n) ->
            out += Card(
                Kind.OUTRO, "Your month", monthName(m),
                "$n plays — the loudest month of the year.",
                1,
            )
        }
        return out
    }

    fun fmtMinutes(minutes: Long): String = when {
        minutes < 60 -> "$minutes min"
        minutes < 60 * 100 -> "${minutes / 60} h ${minutes % 60} min"
        else -> "${minutes / 60} hours"
    }

    private val DEFAULT_MONTHS = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    fun yearOf(atMs: Long, offsetMs: Long): Int {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = atMs + offsetMs
        return cal.get(java.util.Calendar.YEAR)
    }

    fun hourOf(atMs: Long, offsetMs: Long): Int {
        val local = atMs + offsetMs
        return ((local % Listening.DAY + Listening.DAY) % Listening.DAY / 3_600_000L).toInt().coerceIn(0, 23)
    }
}
