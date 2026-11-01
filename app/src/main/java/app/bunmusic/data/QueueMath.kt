package app.bunmusic.data

/**
 * Queue arithmetic without a player.
 *
 * The queue sheet needs four lists — already played, now playing, next, and the
 * ones you dragged somewhere else — and every one of them is an index problem.
 * Doing that inside a composable meant it could not be tested, and the off-by-one
 * when the current track was the last one kept coming back.
 */
object QueueMath {

    /** Everything after the current index, in play order. */
    fun upcoming(queue: List<Track>, index: Int): List<Track> {
        if (index < 0 || index >= queue.lastIndex) return emptyList()
        return queue.subList(index + 1, queue.size).toList()
    }

    /** Everything before the current index, most recent first. */
    fun played(queue: List<Track>, index: Int): List<Track> {
        if (index <= 0 || index > queue.size) return emptyList()
        return queue.subList(0, index).reversed()
    }

    fun totalMs(tracks: List<Track>): Long = tracks.sumOf { it.durationMs.coerceAtLeast(0L) }

    fun insertNext(queue: List<Track>, index: Int, track: Track): List<Track> {
        val q = queue.toMutableList()
        val at = (index + 1).coerceIn(0, q.size)
        q.add(at, track)
        return q
    }

    fun append(queue: List<Track>, track: Track): List<Track> = queue + track

    fun remove(queue: List<Track>, index: Int): List<Track> {
        if (index !in queue.indices) return queue
        val q = queue.toMutableList()
        q.removeAt(index)
        return q
    }

    /**
     * Where a jump lands, clamped. Used by "next" and "previous" so the first
     * track has no previous and the last has no next, rather than wrapping into
     * a loop nobody asked for.
     */
    fun jumpTarget(queue: List<Track>, index: Int, delta: Int): Int {
        if (queue.isEmpty()) return -1
        return (index + delta).coerceIn(0, queue.lastIndex)
    }

    /**
     * Shuffle everything after the current track, leaving the past alone.
     * Shuffling the whole queue would rewrite history: the row you just played
     * would jump to the bottom and "previous" would go somewhere new.
     */
    fun shuffleUpcoming(queue: List<Track>, index: Int, seed: Int = 1): List<Track> {
        if (index !in queue.indices || index >= queue.lastIndex) return queue
        val head = queue.subList(0, index + 1)
        val tail = queue.subList(index + 1, queue.size)
        val shuffled = tail.sortedBy { h(it.id, seed) }
        return head + shuffled
    }

    private fun h(id: String, seed: Int): Int {
        var v = seed * 131 + 7
        id.forEach { v = v * 31 + it.code }
        return v
    }

    fun positionLabel(index: Int, size: Int): String =
        if (index in 0 until size) "${index + 1} of $size" else "Not playing"
}
