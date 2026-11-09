package app.bunmusic.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * What a swipe on the playback card means.
 *
 * While something is playing the card is `ongoing`, so it cannot be swiped at
 * all. When it is paused it can, and before this the swipe did nothing visible:
 * the notification vanished, the service kept running, and the player was left
 * in a state that was neither playing nor reachable — no card to press, no way
 * to stop it.
 *
 * A dismissed card now stops the service but keeps the saved session, so the
 * next launch still offers "resume where you left off" instead of forgetting the
 * queue because someone tidied their notification shade.
 */
class NotificationDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!Dismiss.shouldStop(Playback.state.value.isPlaying)) return
        Playback.dismissCard()
    }
}

/** The decisions, kept out of the receiver so they can be tested without Android. */
object Dismiss {
    fun shouldStop(isPlaying: Boolean): Boolean = !isPlaying

    /**
     * On a cold start nothing has been adopted yet, so Playback.state is an
     * empty PlayerState. Saving that snapshot over the persisted session would
     * erase the resume queue just because someone swiped a stale card away.
     */
    fun shouldSaveSession(hasTrack: Boolean): Boolean = hasTrack
}
