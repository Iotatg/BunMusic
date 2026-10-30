package app.bunmusic.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Keeps playback alive in the background and drives the system media controls. */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        Playback.init(this)
        val intent = Intent(this, app.bunmusic.MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Media3 would fall back to a generic play glyph in the status bar. The bun
        // belongs there instead.
        val notifications = androidx.media3.session.DefaultMediaNotificationProvider.Builder(this)
            .build()
            .apply { setSmallIcon(app.bunmusic.R.drawable.ic_notification) }

        setMediaNotificationProvider(notifications)

        session = MediaSession.Builder(this, Playback.player)
            .setSessionActivity(pending)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!Playback.player.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
