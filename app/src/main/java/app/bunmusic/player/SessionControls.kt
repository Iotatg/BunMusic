package app.bunmusic.player

/**
 * What the lock screen and the notification are allowed to offer, worked out
 * from [PlayerState] alone.
 *
 * Kept out of [SessionBridge] on purpose: everything here is arithmetic over a
 * data class, so it can be tested without a device, a player or a session. The
 * bridge only turns these answers into `Player.Commands`.
 */
object SessionControls {

    /**
     * There is somewhere to go.
     *
     * A one-track queue with repeat off genuinely has no next — the button used
     * to be shown anyway, and pressing it did nothing, which reads as a broken
     * app rather than an empty queue.
     */
    fun hasNext(s: PlayerState): Boolean {
        if (s.queue.isEmpty() || s.index < 0) return false
        return when {
            s.repeat == 1 -> true                    // repeat-all wraps from the last track
            s.shuffle -> s.queue.size > 1            // the shuffle order decides, not the index
            else -> s.index < s.queue.lastIndex
        }
    }

    /**
     * Previous always does something: back a track, or restart this one when it
     * is already past the first few seconds. That is why it is not gated on the
     * queue position the way [hasNext] is.
     */
    fun hasPrev(s: PlayerState): Boolean = s.queue.isNotEmpty() && s.index >= 0

    /** 0..1000, or 0 when the length is not known yet. */
    fun progressPermille(positionMs: Long, durationMs: Long): Int {
        if (durationMs <= 0L) return 0
        val raw = (positionMs.toDouble() / durationMs * 1000.0).toInt()
        return raw.coerceIn(0, 1000)
    }

    /** `m:ss`, or `h:mm:ss` once it runs past an hour. */
    fun clock(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /**
     * How long a track has left, with a minus in front — the notification shows
     * "remaining" rather than "total", because at a glance the useful question is
     * how much longer, not how long it was.
     */
    fun remaining(positionMs: Long, durationMs: Long): String =
        if (durationMs <= 0L) "–:––" else "-" + clock((durationMs - positionMs).coerceAtLeast(0L))
}
