package app.bunmusic.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import app.bunmusic.R
import com.google.common.collect.ImmutableList
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * The notification you actually get, instead of the one Media3 hands you.
 *
 * Media3's default provider draws the platform's media template, which has the
 * three transport buttons and nothing else — no position, no bar, no like. This
 * one draws its own layout: artwork, title, artist, a real progress bar with
 * elapsed and remaining, and six buttons, three of which are ours.
 *
 * Two things to know about the design:
 *
 *  - RemoteViews is a hostile canvas. Only a fixed set of widgets may appear
 *    (a plain `View` throws at inflate time in the launcher's process, which is
 *    where a notification is actually built), custom fonts are ignored, and the
 *    bitmap travelling in the parcel must stay small — artwork is decoded at
 *    192 px for that reason, not because 512 looked worse.
 *  - The bar does not animate by itself. It is redrawn when the service asks,
 *    once a second while something is playing. A notification is a remote view
 *    in another process; there is no ticker to attach to.
 */
class BunNotificationProvider(private val context: Context) : MediaNotification.Provider {

    private val art = ArtCache()
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "bun-notif-art").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    private var session: MediaSession? = null
    private var factory: MediaNotification.ActionFactory? = null
    private var callback: MediaNotification.Provider.Callback? = null
    private var buttons: ImmutableList<CommandButton> = ImmutableList.of()

    init {
        ensureChannel(context)
    }

    override fun createNotification(
        session: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        callback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        this.session = session
        this.factory = actionFactory
        this.callback = callback
        this.buttons = mediaButtons
        return MediaNotification(NOTIFICATION_ID, build(session, actionFactory, mediaButtons))
    }

    /** Our buttons arrive as session commands; the session handles them, not us. */
    override fun handleCustomCommand(session: MediaSession, action: String, extras: android.os.Bundle): Boolean = false

    override fun getNotificationChannelInfo() =
        MediaNotification.Provider.NotificationChannelInfo(CHANNEL_ID, "Playback")

    // ------------------------------------------------------------------ build

    private fun build(
        session: MediaSession,
        actionFactory: MediaNotification.ActionFactory,
        mediaButtons: ImmutableList<CommandButton>,
    ): Notification {
        val player = session.player
        val s = Playback.state.value
        val track = s.track

        val title = player.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: track?.title ?: "BunMusic"
        val artist = player.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: track?.artist ?: "Nothing playing"

        val playing = player.isPlaying
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: s.durationMs
        val permille = SessionControls.progressPermille(position, duration)

        // Transport first: these three are the ones a locked phone needs.
        val prev = actionFactory.createMediaAction(
            session, IconCompat.createWithResource(context, R.drawable.ic_w_prev), "Previous",
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )
        val toggle = actionFactory.createMediaAction(
            session,
            IconCompat.createWithResource(context, if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play),
            if (playing) "Pause" else "Play",
            Player.COMMAND_PLAY_PAUSE,
        )
        val next = actionFactory.createMediaAction(
            session, IconCompat.createWithResource(context, R.drawable.ic_w_next), "Next",
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        )

        // ...then ours. The icon is chosen here rather than read back off the
        // CommandButton: RemoteViews wants a resource id, and IconCompat does not
        // reliably expose one.
        val liked = track?.let { app.bunmusic.data.Store.isLiked(it.id) } == true
        val like = actionFactory.createCustomAction(
            session,
            IconCompat.createWithResource(context, if (liked) R.drawable.ic_n_heart_fill else R.drawable.ic_n_heart),
            SessionGlyphs.likeLabel(liked),
            SessionCommands.ACTION_LIKE,
            android.os.Bundle.EMPTY,
        )
        val shuffle = actionFactory.createCustomAction(
            session,
            IconCompat.createWithResource(context, if (s.shuffle) R.drawable.ic_n_shuffle_on else R.drawable.ic_n_shuffle),
            SessionGlyphs.shuffleLabel(s.shuffle),
            SessionCommands.ACTION_SHUFFLE,
            android.os.Bundle.EMPTY,
        )
        val repeat = actionFactory.createCustomAction(
            session,
            IconCompat.createWithResource(
                context,
                when (s.repeat) {
                    2 -> R.drawable.ic_n_repeat_one
                    1 -> R.drawable.ic_n_repeat
                    else -> R.drawable.ic_n_repeat_off
                },
            ),
            SessionGlyphs.repeatLabel(s.repeat),
            SessionCommands.ACTION_REPEAT,
            android.os.Bundle.EMPTY,
        )

        val open = session.sessionActivity
        // One lookup, used three times: the two layouts and the large icon that
        // the lock screen of an older Android draws instead of the custom view.
        val cover = art.get(track?.artUrl)
        val big = remoteBig(title, artist, position, duration, permille, cover, playing)
        val compact = remoteCompact(title, artist, position, duration, permille, cover, playing)

        // Only ids that exist in each layout: a RemoteViews action aimed at a
        // missing view is at best ignored and at worst an exception at apply
        // time, in the system's process, where it cannot be caught.
        big.setOnClickPendingIntent(R.id.notif_prev, prev.actionIntent)
        big.setOnClickPendingIntent(R.id.notif_next, next.actionIntent)
        big.setOnClickPendingIntent(R.id.notif_like, like.actionIntent)
        big.setOnClickPendingIntent(R.id.notif_shuffle, shuffle.actionIntent)
        big.setOnClickPendingIntent(R.id.notif_repeat, repeat.actionIntent)
        big.setOnClickPendingIntent(R.id.notif_toggle, toggle.actionIntent)
        compact.setOnClickPendingIntent(R.id.notif_toggle, toggle.actionIntent)

        big.setViewVisibility(R.id.notif_prev, if (SessionControls.hasPrev(s)) View.VISIBLE else View.INVISIBLE)
        big.setViewVisibility(R.id.notif_next, if (SessionControls.hasNext(s)) View.VISIBLE else View.INVISIBLE)

        if (open != null) {
            big.setOnClickPendingIntent(R.id.notif_root, open)
            compact.setOnClickPendingIntent(R.id.notif_root, open)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(open)
            .setCustomContentView(compact)
            .setCustomBigContentView(big)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(session).setShowActionsInCompactView(0, 1, 2))
            .addAction(prev)
            .addAction(toggle)
            .addAction(next)
            .addAction(like)
            .addAction(shuffle)
            .addAction(repeat)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // the lock screen shows all of it
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setShowWhen(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setColor(0xFFD8F65C.toInt())
            // Android 12+ otherwise holds a new foreground notification back for
            // ten seconds and shows it silently — the music is audible long
            // before the controls appear.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Swiping the card away while paused has to mean something. Without
            // this the notification disappears and the service keeps running with
            // no way left to reach it — a player that is neither playing nor
            // stoppable. While playing the card is ongoing, so it cannot be
            // swiped in the first place.
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    context,
                    2,
                    Intent(context, NotificationDismissReceiver::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )

        if (cover != null) builder.setLargeIcon(cover)

        return builder.build()
    }

    private fun remoteBig(
        title: String, artist: String, position: Long, duration: Long, permille: Int,
        bitmap: Bitmap?, playing: Boolean,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.notification_media).apply {
        setTextViewText(R.id.notif_title, title)
        setTextViewText(R.id.notif_artist, artist)
        setTextViewText(R.id.notif_elapsed, SessionControls.clock(position))
        setTextViewText(R.id.notif_remaining, SessionControls.remaining(position, duration))
        setProgressBar(R.id.notif_progress, 1000, permille, duration <= 0L)
        setImageViewResource(R.id.notif_toggle, if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        if (bitmap != null) setImageViewBitmap(R.id.notif_art, bitmap)
        else setImageViewResource(R.id.notif_art, R.mipmap.ic_launcher)
    }

    private fun remoteCompact(
        title: String, artist: String, position: Long, duration: Long, permille: Int,
        bitmap: Bitmap?, playing: Boolean,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.notification_media_compact).apply {
        setTextViewText(R.id.notif_title, title)
        setTextViewText(R.id.notif_artist, artist)
        setProgressBar(R.id.notif_progress, 1000, permille, duration <= 0L)
        setImageViewResource(R.id.notif_toggle, if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
        if (bitmap != null) setImageViewBitmap(R.id.notif_art, bitmap)
        else setImageViewResource(R.id.notif_art, R.mipmap.ic_launcher)
    }

    /**
     * Redraw with the artwork that has just finished downloading.
     *
     * Called from the art thread, so everything it touches was captured when the
     * notification was built. If the service has gone away there is nothing to
     * update and this is a no-op.
     */
    private fun republish() {
        val s = session ?: return
        val f = factory ?: return
        val c = callback ?: return
        // The artwork arrives on the download thread. RemoteViews, the player
        // state the card is built from and Media3's own callback all belong to the
        // main thread, so the redraw is posted there instead of run where the
        // bytes landed.
        main.post {
            runCatching {
                c.onNotificationChanged(MediaNotification(NOTIFICATION_ID, build(s, f, buttons)))
            }
        }
    }

    // ------------------------------------------------------------- artwork

    /**
     * Cover art for the notification.
     *
     * The service lives in this app's process, so it *could* use the image
     * loader — but the notification is built on a binder call that the system
     * may make at any moment, and blocking it on a network request freezes the
     * media control. So: memory cache, one background thread, placeholder until
     * the bytes arrive.
     */
    private inner class ArtCache {
        private val cache = object : LruCache<String, Bitmap>(8) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
        private val pending = HashSet<String>()
        private val failed = HashSet<String>()

        fun get(url: String?): Bitmap? {
            if (url.isNullOrBlank()) return null
            cache.get(url)?.let { return it }
            if (pending.contains(url) || failed.contains(url)) return null
            pending.add(url)
            io.execute {
                val bitmap = runCatching { download(url) }.getOrNull()
                synchronized(this) {
                    pending.remove(url)
                    if (bitmap != null) cache.put(url, bitmap) else failed.add(url)
                }
                if (bitmap != null) republish()
            }
            return null
        }

        private fun download(url: String): Bitmap? {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 6_000
            conn.readTimeout = 8_000
            conn.instanceFollowRedirects = true
            return try {
                conn.inputStream.use { BitmapFactory.decodeStream(it) }?.let { scale(it) }
            } finally {
                conn.disconnect()
            }
        }

        /** 192 px square: big enough for a 64 dp thumbnail at 3x, small enough to parcel. */
        private fun scale(src: Bitmap): Bitmap {
            val size = 192
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(out)
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            val side = minOf(src.width, src.height)
            val rect = android.graphics.Rect(
                (src.width - side) / 2, (src.height - side) / 2,
                (src.width + side) / 2, (src.height + side) / 2,
            )
            canvas.drawBitmap(src, rect, android.graphics.Rect(0, 0, size, size), paint)
            return out
        }
    }

    companion object {
        const val CHANNEL_ID = "bunmusic_playback"
        const val NOTIFICATION_ID = 1001

        /**
         * Create the playback channel if it is not there yet.
         *
         * Also called by the test card in Settings, which may run before the
         * service has ever been created — a notification posted on a channel that
         * does not exist is dropped without a word on Android 8 and later.
         */
        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "What is playing right now"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
            )
        }
    }
}
