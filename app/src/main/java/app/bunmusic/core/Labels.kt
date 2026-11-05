package app.bunmusic.core

/**
 * State-dependent names for the transport controls.
 *
 * A screen reader that says "Repeat" for all three repeat modes is telling the
 * listener nothing: the whole point of the button is which mode is next. These
 * are plain functions over the same integers [Playback] uses, so the label and
 * the behaviour cannot drift apart, and both are testable without a device.
 *
 * Deliberately kept out of Compose — no Android import, no composition needed.
 */
object Labels {

    /** `Playback.state.repeat`: 0 off, 1 repeat all, 2 repeat one. */
    fun repeat(mode: Int): String = when (mode) {
        0 -> "Repeat off. Turn on repeat all"
        1 -> "Repeat all. Switch to repeat one"
        2 -> "Repeat one. Turn repeat off"
        else -> "Repeat"
    }

    fun shuffle(on: Boolean): String =
        if (on) "Shuffle on. Turn it off" else "Shuffle off. Turn it on"

    fun like(liked: Boolean): String = if (liked) "Unlike" else "Like"

    /**
     * One-line summary for the whole player, used as the screen's content
     * description rather than per-control labels. Kept short on purpose.
     */
    fun transport(playing: Boolean, buffering: Boolean): String = when {
        buffering -> "Buffering"
        playing -> "Pause"
        else -> "Play"
    }
}
