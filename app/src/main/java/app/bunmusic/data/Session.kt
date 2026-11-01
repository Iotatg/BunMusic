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

    fun load(): Saved? {
        if (!::sp.isInitialized) return null
        val raw = sp.getString(KEY_QUEUE, null) ?: return null
        val tracks = raw.split(ROW).mapNotNull { Store.decodeTrack(it) }
        if (tracks.isEmpty()) return null
        return Saved(
            queue = tracks,
            index = sp.getInt("index", 0).coerceIn(0, tracks.lastIndex),
            positionMs = sp.getLong("position", 0L),
            speed = sp.getFloat("speed", 1f),
            repeat = sp.getInt("repeat", 0),
            shuffle = sp.getBoolean("shuffle", false),
            savedAt = sp.getLong("savedAt", 0L),
        )
    }

    fun clear() {
        if (!::sp.isInitialized) return
        sp.edit().clear().apply()
    }

    /** True when there is something worth offering on the home screen. */
    fun resumable(): Saved? = load()?.takeIf { it.track != null && it.positionMs > 3_000 }
}
