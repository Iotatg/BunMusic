package app.bunmusic.player

import androidx.media3.session.CommandButton

/**
 * Which glyph and which spoken label each of the three custom buttons gets.
 *
 * Split out of [SessionCommands] for a practical reason: that object holds
 * `SessionCommand` instances, whose static initialiser needs `android.os.Bundle`,
 * which does not exist in a JVM unit test. Everything here is a pure function
 * over an `Int`/`Boolean`, so "repeat one shows the 1 glyph" is a test and not a
 * screenshot.
 *
 * The labels describe what pressing the button does, not what is true right now:
 * a button announced as "Shuffle on" reads like a switch stuck in the on
 * position, and TalkBack users get no way to tell what a press will change.
 */
object SessionGlyphs {

    fun likeIcon(liked: Boolean): Int =
        if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED

    fun likeLabel(liked: Boolean): String = if (liked) "Unlike" else "Like"

    fun shuffleIcon(on: Boolean): Int =
        if (on) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF

    fun shuffleLabel(on: Boolean): String = if (on) "Turn shuffle off" else "Turn shuffle on"

    /** 0 = off, 1 = repeat the queue, 2 = repeat this track — the scale [PlayerState.repeat] uses. */
    fun repeatIcon(repeat: Int): Int = when (repeat) {
        2 -> CommandButton.ICON_REPEAT_ONE
        1 -> CommandButton.ICON_REPEAT_ALL
        else -> CommandButton.ICON_REPEAT_OFF
    }

    fun repeatLabel(repeat: Int): String = when (repeat) {
        2 -> "Repeat one track"
        1 -> "Repeat the queue"
        else -> "Turn repeat on"
    }
}
