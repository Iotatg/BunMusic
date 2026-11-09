package app.bunmusic.core

import kotlinx.coroutines.CancellationException

/**
 * Pure rules behind the player's load lifecycle. Kept free of Android and of the
 * player itself so the JVM tests can prove the ordering that a fast "next, next,
 * next" used to break.
 *
 * A load resolves its URL on IO. Between the moment that job starts and the
 * moment it configures the player, the listener may have already asked for two
 * more songs. The old job was cancelled, but it still finished resolving and
 * went on to prepare *its* media source — so the title said one thing and the
 * audio was another. These are the three small decisions that keep that from
 * happening, extracted so they can be asserted without a device.
 */
object LoadGuard {

    /** True when a newer load has started since [generation] was captured. */
    fun isStale(generation: Long, latest: Long): Boolean = generation != latest

    /**
     * A blanket `catch (Throwable)` used to turn a cancellation into an error
     * banner — the cancelled job overwrote the new song's state. Cancellation
     * and Errors must propagate; only ordinary failures become a message.
     */
    fun isFatal(t: Throwable): Boolean = t is CancellationException || t is Error

    /**
     * Media3 reports "no position" as C.TIME_UNSET (Long.MIN_VALUE), and a
     * resumption callback can hand us a position before our async load has a
     * timeline to seek into. Return the position to stash for after prepare(),
     * or 0 when there is nothing to restore.
     */
    fun pendingSeek(positionMs: Long, unset: Long): Long =
        if (positionMs > 0 && positionMs != unset) positionMs else 0L
}
