package app.bunmusic.player

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import app.bunmusic.R
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
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + app.bunmusic.core.CrashGuard.handler("service"),
    )

    override fun onCreate() {
        super.onCreate()
        Playback.init(this)

        // A session built on a player that does not exist is a crash inside a
        // service, which takes the whole process with it. If the player could not
        // be created there is nothing to control, so the service leaves quietly.
        if (!Playback.ready) {
            stopSelf()
            return
        }

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
        // ALWAYS, not NEVER. Media3 re-runs shouldShowNotification() on every
        // update and cancels the card when the controller's playback state is
        // idle under NEVER. A restored session is idle *and real*: the track is
        // on screen, the queue is saved, only the stream is unprepared. Hiding
        // it produced the card that appeared for a second and vanished.
        //
        // An empty state still shows no card: Media3's other guard is an empty
        // timeline, and SessionBridge only reports a non-empty timeline while
        // Playback.state has a track. Stop() clears both, so a full stop still
        // takes the card away without us ever drawing "Nothing playing".
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_ALWAYS)

        session = MediaSession.Builder(this, SessionBridge(Playback.player))
            .setSessionActivity(pending)
            .setCallback(Callbacks())
            .setMediaButtonPreferences(buttons())
            .build()

        // A startForegroundService() call obliges the service to promote itself
        // within five seconds. Media3 only promotes once its own controller has
        // connected, which can be later than that on a cold start. Put a
        // placeholder in the foreground slot now; the first real update
        // replaces it, or Media3 takes it down when there is nothing to show.
        runCatching {
            startForeground(
                BunNotificationProvider.NOTIFICATION_ID,
                NotificationCompat.Builder(this, BunNotificationProvider.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("BunMusic")
                    .setContentText("Starting playback")
                    .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                    .setSilent(true)
                    .setOngoing(true)
                    .build(),
            )
        }

        watch()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away should not stop the music; a media service that
        // dies with its task is a music player that stops when you look at it.
        if (!Playback.ready || !Playback.player.isPlaying) stopSelf()
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
     * Keep the card alive, and keep it true.
     *
     * A notification is a RemoteViews tree in another process — there is no
     * animation to attach to and no callback to receive, so the bar only moves
     * if we redraw it. Once a second is the cheapest rate that still reads as
     * smooth at 3 dp tall.
     *
     * The old watcher republished *only* on `isPlaying && secondChanged`. That
     * left two holes: a like, a pause, a track change, a shuffle or repeat
     * toggle, a resolved duration or a buffering stall drew nothing until the
     * next second of audio; and a paused or restoring track was never redrawn
     * at all, so Media3's own re-check could quietly cancel the card.
     * [NotificationPolicy] now decides: every shape change republishes
     * immediately, plus one redraw a second while audio is moving.
     *
     * `startInForegroundRequired` is `true` for every live card, not just while
     * playing. `triggerNotificationUpdate()` would be the shorter call, but it
     * passes `false`, and a service that never asks to go foreground is a
     * service the system is free to kill the moment the screen goes off — the
     * music would die with the app in the background. A paused card that can
     * still be resumed deserves the same protection.
     */
    private fun watch() {
        scope.launch {
            var lastSecond = -1L
            var lastShape: NotificationPolicy.Shape? = null
            Playback.state.collect { s ->
                val current = session ?: return@collect
                val liked = s.track?.let { Store.isLiked(it.id) } == true
                val shape = NotificationPolicy.Shape.of(s, liked)
                val shapeChanged = shape != lastShape
                val second = NotificationPolicy.secondOf(s.positionMs)
                val secondChanged = second != lastSecond
                val live = NotificationPolicy.shouldBeLive(s.track != null)

                if (shapeChanged) {
                    lastShape = shape
                    current.setMediaButtonPreferences(buttons())
                }

                if (!live) {
                    // No track: let Media3 take the card down. Reposting here
                    // would draw the "Nothing playing" card we deliberately
                    // avoid with the empty logical timeline.
                    lastSecond = -1L
                    runCatching { onUpdateNotification(current, false) }
                    return@collect
                }

                if (NotificationPolicy.shouldRepublish(live, shapeChanged, s.isPlaying, secondChanged)) {
                    lastSecond = second
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
            // heart stays hollow until the next track. Pass `true` for the
            // foreground requirement: a tap on a paused card must not silently
            // demote the service, or the card can be killed with it.
            session.setMediaButtonPreferences(buttons())
            runCatching { onUpdateNotification(session, true) }
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
