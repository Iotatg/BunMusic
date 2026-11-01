package app.bunmusic.data

/**
 * Sorting and filtering for every list of songs in the app.
 *
 * The library screen had three sort orders hard-coded inside one composable,
 * which meant the playlist screen could not use them and a test could not see
 * them. They live here now, as a value ([SortSpec]) and a pure function.
 */
enum class SortKey(val label: String) {
    RECENT("Recently added"),
    TITLE("Title"),
    ARTIST("Artist"),
    DURATION("Duration"),
    PLAYS("Most played"),
}

data class SortSpec(
    val key: SortKey = SortKey.RECENT,
    /** false = ascending. The UI flips this with one tap on the active chip. */
    val descending: Boolean = false,
)

object Sorting {

    fun normalize(s: String): String = s.trim().lowercase()

    /**
     * [addedOrder] is newest-first, so a track's position in it *is* its recency.
     * Tracks that were never in the list go to the end instead of the top, which
     * is what `sortedBy { 0 }` would have done by accident.
     */
    fun sort(
        tracks: List<Track>,
        spec: SortSpec,
        plays: Map<String, Int> = emptyMap(),
        addedOrder: List<String> = emptyList(),
    ): List<Track> {
        val pos = addedOrder.withIndex().associate { it.value to it.index }
        val base = when (spec.key) {
            SortKey.RECENT -> tracks.sortedBy { pos[it.id] ?: Int.MAX_VALUE }
            SortKey.TITLE -> tracks.sortedBy { normalize(it.title) }
            SortKey.ARTIST -> tracks.sortedWith(
                compareBy({ normalize(it.artist) }, { normalize(it.title) })
            )
            SortKey.DURATION -> tracks.sortedBy { it.durationMs }
            SortKey.PLAYS -> tracks.sortedBy { plays[it.id] ?: 0 }
        }
        return if (spec.descending) base.reversed() else base
    }

    fun filter(
        tracks: List<Track>,
        query: String = "",
        downloadedOnly: Boolean = false,
        downloadedIds: Set<String> = emptySet(),
        likedOnly: Boolean = false,
        likedIds: Set<String> = emptySet(),
    ): List<Track> {
        val q = normalize(query)
        return tracks.filter { t ->
            val matches = q.isEmpty() ||
                normalize(t.title).contains(q) ||
                normalize(t.artist).contains(q) ||
                normalize(t.album).contains(q)
            val saved = !downloadedOnly || t.isLocal || t.id in downloadedIds || t.videoId in downloadedIds
            val liked = !likedOnly || t.id in likedIds
            matches && saved && liked
        }
    }

    /**
     * Same song, twice. Compared on title + artist with punctuation and a
     * "(Remastered 2011)" style suffix removed, because a playlist assembled from
     * search results is full of those and they are the same recording.
     */
    fun duplicates(tracks: List<Track>): List<List<Track>> =
        tracks
            .groupBy { key(it) }
            .filterValues { it.size > 1 }
            .values
            .sortedByDescending { it.size }

    fun key(track: Track): String = "${normalize(baseTitle(track.title))}|${normalize(track.artist)}"

    fun baseTitle(title: String): String {
        var t = title
        val cut = listOf(" (", " [", " - Remaster", " - Live", " - Radio Edit")
        cut.forEach { marker ->
            val at = t.indexOf(marker, ignoreCase = true)
            if (at > 0) t = t.substring(0, at)
        }
        return t.trim()
    }

    fun totalMs(tracks: List<Track>): Long = tracks.sumOf { it.durationMs.coerceAtLeast(0L) }

    fun summary(tracks: List<Track>): String {
        if (tracks.isEmpty()) return "Empty"
        val minutes = totalMs(tracks) / 60_000
        val hours = minutes / 60
        val time = when {
            hours >= 1 -> "${hours}h ${minutes % 60}m"
            else -> "${minutes}m"
        }
        return "${tracks.size} songs · $time"
    }
}
