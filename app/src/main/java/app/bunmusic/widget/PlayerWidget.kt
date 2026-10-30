package app.bunmusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import app.bunmusic.MainActivity
import app.bunmusic.R
import app.bunmusic.core.Skin
import app.bunmusic.data.hq
import app.bunmusic.player.Playback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * The now-playing bar that sits on the home screen.
 *
 * Deliberately dumb: it owns no state of its own. Every pixel comes from
 * [Playback.state], and the widget is redrawn whenever that flow changes.
 */
class PlayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> Bar.push(context, manager, id, force = true) }
    }

    override fun onEnabled(context: Context) {
        Bar.pushAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> {
                Playback.toggle()
                Bar.pushAll(context)
                return
            }
            ACTION_NEXT -> {
                Playback.next()
                Bar.pushAll(context)
                return
            }
            ACTION_PREV -> {
                Playback.previous()
                Bar.pushAll(context)
                return
            }
            ACTION_REFRESH,
            Intent.ACTION_CONFIGURATION_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            -> {
                Bar.pushAll(context)
                return
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_REFRESH = "app.bunmusic.widget.REFRESH"
        const val ACTION_TOGGLE = "app.bunmusic.widget.TOGGLE"
        const val ACTION_NEXT = "app.bunmusic.widget.NEXT"
        const val ACTION_PREV = "app.bunmusic.widget.PREV"
        const val ACTION_OPEN = "app.bunmusic.widget.OPEN"
    }
}

/** Everything the widget actually does, kept out of the provider so it can be called from anywhere. */
internal object Bar {

    private const val RC_TOGGLE = 9101
    private const val RC_NEXT = 9102
    private const val RC_PREV = 9103
    private const val RC_OPEN = 9104
    private const val RC_ROOT = 9105

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val art = ConcurrentHashMap<String, Bitmap>()
    private var artJob: Job? = null
    private var lastPush = 0L
    private var lastSignature = ""

    fun pushAll(context: Context, force: Boolean = true) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
        if (ids.isEmpty()) return
        ids.forEach { push(context, manager, it, force = force) }
    }

    /**
     * @param force bypass the throttle — used for real state changes (track, play/pause)
     *              as opposed to the once-a-second position tick.
     */
    fun push(context: Context, manager: AppWidgetManager, id: Int, force: Boolean) {
        val s = Playback.state.value
        val track = s.track
        val signature = "${track?.id}:${s.isPlaying}:${Skin.current.id}:${track?.artUrl}"
        val now = System.currentTimeMillis()
        if (!force && signature == lastSignature && now - lastPush < 1_000L) return
        lastSignature = signature
        lastPush = now

        val light = Skin.light
        val views = RemoteViews(context.packageName, R.layout.widget_player)

        views.setInt(
            R.id.widget_root, "setBackgroundResource",
            if (light) R.drawable.widget_bg_light else R.drawable.widget_bg_dark,
        )
        views.setInt(
            R.id.widget_toggle, "setBackgroundResource",
            if (light) R.drawable.widget_btn_light else R.drawable.widget_btn_dark,
        )
        views.setInt(
            R.id.widget_title, "setTextColor",
            if (light) 0xFF171C26.toInt() else 0xFFFFFFFF.toInt(),
        )
        views.setInt(
            R.id.widget_artist, "setTextColor",
            if (light) 0x99171C26.toInt() else 0x99FFFFFF.toInt(),
        )
        val tint = if (light) 0xFF171C26.toInt() else 0xFFFFFFFF.toInt()
        views.setInt(R.id.widget_prev, "setColorFilter", tint)
        views.setInt(R.id.widget_next, "setColorFilter", tint)
        // The toggle sits on a contrasting pill, so its glyph is always the opposite ink.
        views.setInt(R.id.widget_toggle, "setColorFilter", if (light) 0xFFFFFFFF.toInt() else 0xFF171C26.toInt())
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            views.setColorStateList(
                R.id.widget_progress, "setProgressTintList",
                android.content.res.ColorStateList.valueOf(tint),
            )
            views.setColorStateList(
                R.id.widget_progress, "setProgressBackgroundTintList",
                android.content.res.ColorStateList.valueOf(if (light) 0x1F171C26 else 0x33FFFFFF.toInt()),
            )
        }

        if (track == null) {
            views.setTextViewText(R.id.widget_title, "BunMusic")
            views.setTextViewText(R.id.widget_artist, "Nothing playing")
            views.setImageViewResource(R.id.widget_toggle, R.drawable.ic_w_play)
            views.setViewVisibility(R.id.widget_progress, View.GONE)
            views.setViewVisibility(R.id.widget_art, View.GONE)
            views.setOnClickPendingIntent(R.id.widget_prev, null)
            views.setOnClickPendingIntent(R.id.widget_next, null)
        } else {
            views.setTextViewText(R.id.widget_title, track.title.ifBlank { "Unknown" })
            views.setTextViewText(
                R.id.widget_artist,
                listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · ")
                    .ifBlank { "BunMusic" },
            )
            views.setImageViewResource(
                R.id.widget_toggle,
                if (s.isPlaying) R.drawable.ic_w_pause else R.drawable.ic_w_play,
            )
            views.setViewVisibility(R.id.widget_progress, View.VISIBLE)
            views.setViewVisibility(R.id.widget_art, View.VISIBLE)

            val duration = if (s.durationMs > 0) s.durationMs else track.durationMs
            val fraction = if (duration > 0) (s.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
            views.setProgressBar(R.id.widget_progress, 1000, (fraction * 1000).toInt(), false)

            views.setOnClickPendingIntent(R.id.widget_prev, broadcast(context, PlayerWidget.ACTION_PREV, RC_PREV))
            views.setOnClickPendingIntent(R.id.widget_next, broadcast(context, PlayerWidget.ACTION_NEXT, RC_NEXT))
            paintArt(context, views, track.artUrl, id, manager)
        }

        views.setOnClickPendingIntent(R.id.widget_toggle, broadcast(context, PlayerWidget.ACTION_TOGGLE, RC_TOGGLE))
        views.setOnClickPendingIntent(R.id.widget_art, open(context, RC_OPEN))
        views.setOnClickPendingIntent(R.id.widget_root, open(context, RC_ROOT))

        runCatching { manager.updateAppWidget(id, views) }
    }

    /** Called from [Playback] whenever the state flow emits. Cheap to call. */
    fun onPlaybackState() {
        val ctx = appContext ?: return
        scope.launch { pushAll(ctx, force = false) }
    }

    private var appContext: Context? = null

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    private fun paintArt(context: Context, views: RemoteViews, url: String?, id: Int, manager: AppWidgetManager) {
        if (url.isNullOrBlank()) {
            views.setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)
            return
        }
        val cached = art[url]
        if (cached != null) {
            views.setImageViewBitmap(R.id.widget_art, cached)
            return
        }
        views.setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)
        artJob?.cancel()
        artJob = scope.launch {
            val bitmap = withContext(Dispatchers.IO) { fetch(hq(url)) }
            if (bitmap != null) {
                art[url] = bitmap
                if (art.size > 12) art.keys.firstOrNull()?.let { art.remove(it) }
                val fresh = RemoteViews(context.packageName, R.layout.widget_player)
                // Only the artwork is replaced; the rest of the view was already set above.
                fresh.setImageViewBitmap(R.id.widget_art, bitmap)
                runCatching { manager.partiallyUpdateAppWidget(id, fresh) }
            }
        }
    }

    private fun fetch(url: String): Bitmap? = runCatching {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 12_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
        conn.inputStream.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun broadcast(context: Context, action: String, code: Int): PendingIntent {
        val intent = Intent(context, PlayerWidget::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun open(context: Context, code: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("open_player", true)
        return PendingIntent.getActivity(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
