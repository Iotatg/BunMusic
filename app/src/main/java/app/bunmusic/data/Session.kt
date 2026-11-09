package app.bunmusic.data

import android.content.Context
import android.content.SharedPreferences
import app.bunmusic.player.PlayerState

/**
 * The last thing that was playing, kept across a cold start.
 *
 * BunMusic used to forget everything the moment the process died: the queue, the
 * position, the speed, the shuffle state. Kill the app on the bus and the next
 * launch was a blank home screen. This stores the session in its own preferences
 * file — not the library store — because it is a *state*, not content: it is
 * replaced wholesale on every save and thrown away by "stop".
 *
 * The queue is capped at [MAX] tracks. A radio station that grew to four hundred
 * songs does not need all of them restored; the first hundred are enough to carry
 * on from, and it keeps the write under a few kilobytes.
 */
object Session {

    data class Saved(
        val queue: List<Track>,
        val index: Int,
        val positionMs: Long,
        val speed: Float,
        val repeat: Int,
        val shuffle: Boolean,
        val savedAt: Long,
    ) {
        val track: Track? get() = queue.getOrNull(index)
    }

    private const val MAX = 100

    /** How much of the queue sits *behind* the cursor when a session is written. */
    private const val BEHIND = 5

    /**
     * Clamp a restored position into a playable window.
     *
     * A saved position at (or past) the end makes Media3 fire STATE_ENDED the
     * moment it is prepared, so "resume where I left off" silently skipped to the
     * next track. Leaving one second of runway avoids that. When the duration is
     * unknown or implausibly short there is nothing safe to clamp against, so the
     * saved value is kept rather than replaced with 0.
     */
    fun resumePositionMs(savedMs: Long, durationMs: Long): Long {
        val start = savedMs.coerceAtLeast(0L)
        if (durationMs <= 1_000L) return start
        return start.coerceAtMost(durationMs - 1_000L)
    }

    /**
     * Row separator. Deliberately not `\n`: a local file's ID3 title can contain
     * a line break, and the queue is one string of encoded tracks joined end to
     * end — a newline inside a title used to split one track into two broken
     * rows, which silently truncated the restored queue.
     */
    private const val ROW = '\u0002'

    /** Bumped when the on-disk shape changed, so an old blob is never half-read. */
    private const val KEY_QUEUE = "queue2"

    /** A queue trimmed to what is worth restoring, plus the cursor inside it. */
    internal data class Cut(val queue: List<Track>, val index: Int)

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.getSharedPreferences("bunmusic_session", Context.MODE_PRIVATE)
    }

    /**
     * The part of a queue worth writing down.
     *
     * A few tracks of history, the cursor, and at most [max] ahead. Pure, so the
     * arithmetic that decides what comes back after a cold start is testable —
     * including the case that used to throw: an index left over from a queue the
     * listener had already shortened.
     */
    internal fun slice(queue: List<Track>, index: Int, max: Int = MAX): Cut {
        if (queue.isEmpty()) return Cut(emptyList(), 0)
        val from = (index - BEHIND).coerceIn(0, queue.lastIndex)
        val part = queue.drop(from).take(max)
        val at = (index - from).coerceIn(0, part.lastIndex.coerceAtLeast(0))
        return Cut(part, at)
    }

    @Synchronized
    fun save(state: PlayerState) {
        if (!::sp.isInitialized) return
        val track = state.track
        if (track == null || state.queue.isEmpty()) {
            clear()
            return
        }
        // Only what is still ahead of the cursor plus a little history: restoring
        // an entire 400-track radio station costs more than it is worth.
        val cut = slice(state.queue, state.index)
        sp.edit()
            .putString(KEY_QUEUE, cut.queue.joinToString(ROW.toString()) { Store.encodeTrack(it) })
            .putInt("index", cut.index)
            .putLong("position", state.positionMs)
            .putFloat("speed", state.speed)
            .putInt("repeat", state.repeat)
            .putBoolean("shuffle", state.shuffle)
            .putLong("savedAt", System.currentTimeMillis())
            .apply()
    }

    /**
     * Normalise values read back from prefs before they ever reach the player.
     * A persisted speed can be NaN/0/negative (feeding PlaybackParameters an
     * illegal float), a position can be negative, and repeat can be out of the
     * 0..2 range. Pure, so a corrupt session is testable without a device.
     */
    internal fun sanitize(
        tracks: List<Track>,
        index: Int,
        positionMs: Long,
        speed: Float,
        repeat: Int,
        shuffle: Boolean,
        savedAt: Long,
    ): Saved = Saved(
        queue = tracks,
        index = index.coerceIn(0, tracks.lastIndex.coerceAtLeast(0)),
        positionMs = positionMs.coerceAtLeast(0L),
        speed = if (speed.isFinite() && speed > 0f) speed.coerceIn(0.5f, 2f) else 1f,
        repeat = repeat.coerceIn(0, 2),
        shuffle = shuffle,
        savedAt = savedAt,
    )

    fun load(): Saved? {
        if (!::sp.isInitialized) return null
        // A preference key written by an older build under a different type
        // throws ClassCastException on getInt/getFloat/getString. One bad key
        // must not abort the whole cold-start restore.
        return try {
            val raw = sp.getString(KEY_QUEUE, null) ?: return null
            val tracks = raw.split(ROW).mapNotNull { Store.decodeTrack(it) }
            if (tracks.isEmpty()) return null
            sanitize(
                tracks = tracks,
                index = sp.getInt("index", 0),
                positionMs = sp.getLong("position", 0L),
                speed = sp.getFloat("speed", 1f),
                repeat = sp.getInt("repeat", 0),
                shuffle = sp.getBoolean("shuffle", false),
                savedAt = sp.getLong("savedAt", 0L),
            )
        } catch (e: ClassCastException) {
            null
        }
    }

    fun clear() {
        if (!::sp.isInitialized) return
        sp.edit().clear().apply()
    }

    /** True when there is something worth offering on the home screen. */
    fun resumable(): Saved? = load()?.takeIf { it.track != null && it.positionMs > 3_000 }
}
