package app.bunmusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import app.bunmusic.MainActivity
import app.bunmusic.R
import app.bunmusic.core.Skin
import app.bunmusic.data.Store
import app.bunmusic.player.Playback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The one place that knows every widget exists.
 *
 * Playback calls [onPlayback] twice a second; this throttles to about one redraw
 * a second, skips redraws when nothing visible changed, and only touches widgets
 * that are actually on a home screen. A phone with no widgets placed pays one
 * `getAppWidgetIds` call per second and nothing else.
 */
object Widgets {

    const val ACTION_TOGGLE = "app.bunmusic.widget.TOGGLE"
    const val ACTION_NEXT = "app.bunmusic.widget.NEXT"
    const val ACTION_PREV = "app.bunmusic.widget.PREV"
    const val ACTION_LIKE = "app.bunmusic.widget.LIKE"
    const val ACTION_JUMP = "app.bunmusic.widget.JUMP"
    const val ACTION_REFRESH = "app.bunmusic.widget.REFRESH"

    const val EXTRA_INDEX = "app.bunmusic.widget.INDEX"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var appContext: Context? = null
    private var lastSignature = ""
    private var lastPushAt = 0L

    /**
     * False once we have looked and found no widgets on any home screen. While
     * it is false, the twice-a-second playback tick costs one boolean read
     * instead of four binder calls to the launcher.
     */
    private var anyPlaced = true

    fun attach(context: Context) {
        val app = context.applicationContext
        appContext = app
        WidgetStore.init(app)
    }

    /** Called from [Playback.publish] on every tick. Cheap by design. */
    fun onPlayback() {
        if (!anyPlaced) return
        val context = appContext ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastPushAt < 900L) return
        lastPushAt = now
        scope.launch { pushAll(context, force = false) }
    }

    /**
     * Redraw every placed widget.
     *
     * @param force redraw even when the signature says nothing changed — used
     *              after a tap, a config change, or a skin switch.
     */
    fun pushAll(context: Context, force: Boolean = true) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val state = Playback.state.value
        val signature = buildString {
            append(state.track?.id).append('|')
            append(state.isPlaying).append('|')
            append(state.positionMs / 1000L).append('|')
            append(state.queue.size).append('|')
            append(state.index).append('|')
            append(state.track?.let { Store.isLiked(it.id) }).append('|')
            append(Skin.current.id)
        }
        if (!force && signature == lastSignature) return
        lastSignature = signature

        var total = 0
        for (kind in WidgetKind.entries) {
            val ids = manager.getAppWidgetIds(ComponentName(context, kind.cls))
            total += ids.size
            for (id in ids) WidgetRenderer.update(context, manager, id, kind, force)
        }
        anyPlaced = total > 0

        val queueIds = manager.getAppWidgetIds(ComponentName(context, QueueWidget::class.java))
        if (queueIds.isNotEmpty()) {
            runCatching { manager.notifyAppWidgetViewDataChanged(queueIds, R.id.widget_list) }
        }
    }

    fun refresh(context: Context) {
        lastSignature = ""
        anyPlaced = true
        pushAll(context, force = true)
    }

    /** A provider has just been asked to draw, so a widget exists. */
    fun notePlaced() {
        anyPlaced = true
    }

    /** Ask the launcher to pin a widget. Returns false when it will not. */
    fun pin(context: Context, kind: WidgetKind): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!manager.isRequestPinAppWidgetSupported) return false
        val provider = ComponentName(context, kind.cls)
        val success: PendingIntent? = null
        return runCatching { manager.requestPinAppWidget(provider, null, success) }.getOrDefault(false)
    }

    fun placed(context: Context): Map<WidgetKind, Int> {
        val manager = AppWidgetManager.getInstance(context) ?: return emptyMap()
        return WidgetKind.entries.associateWith {
            manager.getAppWidgetIds(ComponentName(context, it.cls)).size
        }
    }

    /** Bring the app forward. Used when a widget has nothing loaded to control. */
    fun openApp(context: Context, player: Boolean = false, queue: Boolean = false) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("open_player", player)
            .putExtra("open_queue", queue)
        runCatching { context.startActivity(intent) }
    }
}
