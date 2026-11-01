package app.bunmusic.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import app.bunmusic.data.Session
import app.bunmusic.data.Store
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps playback alive in the background and owns everything the system can see:
 * the media session, the notification, the lock-screen controls.
 *
 * Three things were wrong before this, and all three were invisible from inside
 * the app:
 *
 *  1. the session was built on ExoPlayer directly, which holds one track at a
 *     time, so the lock screen decided there was no next and no previous;
 *  2. the notification was Media3's default, which has no position and no bar;
 *  3. nothing told the session about like, shuffle or repeat.
 *
 * [SessionBridge] fixes the first, [BunNotificationProvider] the second, and the
 * media button preferences below the third.
 *
 * There was a fourth, and it was the one that mattered most: nothing ever
 * started this service. A `MediaSessionService` is not created by the manifest
 * entry alone — a controller has to connect or the app has to start it. BunMusic
 * plays in its own process on an ExoPlayer the system never sees, so the session
 * that the notification, the lock screen and the Bluetooth buttons are all built
 * from simply did not exist. [ServiceLauncher] now starts it on every play path.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Playback.init(this)

        val intent = Intent(this, app.bunmusic.MainActivity::class.java)
            // Tapping the card should land on the player, not on the home feed —
            // the notification is the player, as far as a locked phone is
            // concerned, and MainActivity already knows this extra.
            .putExtra("open_player", true)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // The bun belongs in the status bar, not a generic play glyph.
        setMediaNotificationProvider(BunNotificationProvider(this))
        // Deliberately NEVER: an idle player has no title, no artist and no
        // length, so showing a card for it means a notification that says
        // "Nothing playing" the moment the service starts — before a track has
        // been chosen. Pausing is not idle, so a paused player keeps its
        // controls; only a full stop takes the card away, and Playback.stop()
        // stops the service as well.
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER)

        session = MediaSession.Builder(this, SessionBridge(Playback.player))
            .setSessionActivity(pending)
            .setCallback(Callbacks())
            .setMediaButtonPreferences(buttons())
            .build()

        watch()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away should not stop the music; a media service that
        // dies with its task is a music player that stops when you look at it.
        if (!Playback.player.isPlaying) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // The state watcher holds this service alive through its scope; without
        // this it keeps collecting long after the service is gone.
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }

    // ------------------------------------------------------------- notification

    /**
     * One notification redraw per second while something is playing.
     *
     * A notification is a RemoteViews tree in another process — there is no
     * animation to attach to and no callback to receive, so the bar only moves
     * if we redraw it. Once a second is the cheapest rate that still reads as
     * smooth at 3 dp tall, and it stops entirely when paused.
     *
     * `startInForegroundRequired` is passed as `isPlaying` on purpose.
     * `triggerNotificationUpdate()` would be the shorter call, but it passes
     * `false`, and a service that never asks to go foreground is a service the
     * system is free to kill the moment the screen goes off — the music would
     * die with the app in the background, which is the whole failure this class
     * exists to avoid.
     */
    private fun watch() {
        scope.launch {
            var lastSecond = -1L
            var lastShape: List<Any?>? = null
            Playback.state.collect { s ->
                val shape = listOf(s.track?.id, s.repeat, s.shuffle, s.queue.size)
                if (shape != lastShape) {
                    lastShape = shape
                    session?.setMediaButtonPreferences(buttons())
                }
                val second = s.positionMs / 1_000
                if (s.isPlaying && second != lastSecond) {
                    lastSecond = second
                    val current = session ?: return@collect
                    runCatching { onUpdateNotification(current, true) }
                }
            }
        }
    }

    private fun buttons() = SessionCommands.buttons(
        Playback.state.value,
        Playback.state.value.track?.let { Store.isLiked(it.id) } == true,
    )

    // ------------------------------------------------------------------ session

    private inner class Callbacks : MediaSession.Callback {

        /**
         * Without this the three custom commands are refused to every controller
         * — including the system's own media control — and the buttons render
         * greyed out.
         */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .addSessionCommands(SessionCommands.all())
                        .build()
                )
                .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                SessionCommands.ACTION_LIKE -> Playback.likeCurrent()
                SessionCommands.ACTION_SHUFFLE -> Playback.toggleShuffle()
                SessionCommands.ACTION_REPEAT -> Playback.cycleRepeat()
            }
            // The icons carry the state, so they have to be re-sent; otherwise the
            // heart stays hollow until the next track.
            session.setMediaButtonPreferences(buttons())
            runCatching { onUpdateNotification(session, Playback.state.value.isPlaying) }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /**
         * Android 13 media resumption: the system asks "what were you playing?"
         * to put a media player back in quick settings after a reboot, or after
         * the app was killed.
         *
         * The items carry the whole track in their extras, because the player
         * underneath only ever holds one item and cannot be trusted to remember
         * the queue — [SessionBridge] reads them back.
         */
        override fun onPlaybackResumption(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val saved = Session.load()
            val track = saved?.track
            if (saved == null || track == null) {
                return Futures.immediateFuture(null)
            }
            val items: List<MediaItem> = saved.queue.map { SessionBridge.item(it) }
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(items, saved.index, saved.positionMs)
            )
        }
    }
}
