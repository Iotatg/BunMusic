package app.bunmusic.player

import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand

/**
 * The three things worth reaching from outside the app: like, shuffle, repeat.
 *
 * They are declared once here and used twice — as `CommandButton`s on the
 * session (so the system's own media control on Android 13+ and the lock screen
 * draw them) and as buttons inside our notification layout. Two lists would
 * drift, and one of them would end up showing a heart that is a track behind.
 *
 * The glyph and label decisions live in [SessionGlyphs], where they can be
 * tested without a Context.
 */
object SessionCommands {

    const val ACTION_LIKE = "app.bunmusic.session.LIKE"
    const val ACTION_SHUFFLE = "app.bunmusic.session.SHUFFLE"
    const val ACTION_REPEAT = "app.bunmusic.session.REPEAT"

    val like = SessionCommand(ACTION_LIKE, Bundle.EMPTY)
    val shuffle = SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY)
    val repeat = SessionCommand(ACTION_REPEAT, Bundle.EMPTY)

    fun all(): List<SessionCommand> = listOf(like, shuffle, repeat)

    fun buttons(state: PlayerState, liked: Boolean): List<CommandButton> = listOf(
        CommandButton.Builder(SessionGlyphs.likeIcon(liked))
            .setSessionCommand(like)
            .setDisplayName(SessionGlyphs.likeLabel(liked))
            .setEnabled(state.track != null)
            .build(),
        CommandButton.Builder(SessionGlyphs.shuffleIcon(state.shuffle))
            .setSessionCommand(shuffle)
            .setDisplayName(SessionGlyphs.shuffleLabel(state.shuffle))
            .setEnabled(state.queue.size > 1)
            .build(),
        CommandButton.Builder(SessionGlyphs.repeatIcon(state.repeat))
            .setSessionCommand(repeat)
            .setDisplayName(SessionGlyphs.repeatLabel(state.repeat))
            .setEnabled(true)
            .build(),
    )
}
