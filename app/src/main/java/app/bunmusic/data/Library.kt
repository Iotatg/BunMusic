package app.bunmusic.data

/**
 * The playlists nobody had to make.
 *
 * SimpMusic has a whole screen of these — most played, top of the month, forgotten
 * favourites — and they are the cheapest honest feature a player can have: the app
 * already knows the answers, it just never asked. Everything here is derived from
 * [Store] at read time, so there is no second copy of the truth to keep in sync.
 *
 * The maths lives in pure functions ([rank], [onRepeat], [forgotten]) so it can be
 * tested without a device.
 */
object Library {

    data class Auto(
        val id: String,
        val title: String,
        val subtitle: String,
        val tracks: List<Track>,
    )

    private const val DAY = 86_400_000L

    /** Highest play count first; ties keep the registry order. */
    fun rank(counts: Map<String, Int>, tracks: List<Track>, limit: Int = 50): List<Track> =
        tracks
            .mapNotNull { t -> counts[t.id]?.let { c -> t to c } }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }

    /** Played at least [min] times inside the window, most recent first. */
    fun onRepeat(
        history: List<HistoryItem>,
        days: Int = 7,
        min: Int = 2,
        now: Long = System.currentTimeMillis(),
    ): List<Track> {
        val since = now - days * DAY
        return history
            .filter { it.atMs >= since }
            .groupBy { it.track.id }
            .filterValues { it.size >= min }
            .entries
            .sortedByDescending { it.value.size }
            .map { it.value.first().track }
    }

    /**
     * Liked or played, but not touched in [days]. The point of this list is to
     * resurface things, so the tracks you are already playing are excluded.
     */
    fun forgotten(
        liked: List<Track>,
        history: List<HistoryItem>,
        days: Int = 30,
        now: Long = System.currentTimeMillis(),
    ): List<Track> {
        val cutoff = now - days * DAY
        val lastSeen = history.associate { it.track.id to it.atMs }
        return liked.filter { (lastSeen[it.id] ?: 0L) < cutoff }
    }

    fun autoLists(now: Long = System.currentTimeMillis()): List<Auto> {
        val counts = Store.playCounts()
        val all = Store.allTracks()
        val history = Store.history()
        val liked = Store.likedTracks()

        val out = ArrayList<Auto>()
        rank(counts, all).takeIf { it.size >= 3 }?.let {
            out += Auto("auto.most", "Most played", "${it.size} tracks you keep coming back to", it)
        }
        onRepeat(history, now = now).takeIf { it.isNotEmpty() }?.let {
            out += Auto("auto.repeat", "On repeat", "Played twice or more this week", it)
        }
        forgotten(liked, history, now = now).takeIf { it.size >= 3 }?.let {
            out += Auto("auto.forgot", "Not played lately", "${it.size} you have not touched in a month", it)
        }
        return out
    }
}
