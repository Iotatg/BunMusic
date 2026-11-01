package app.bunmusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import app.bunmusic.MainActivity
import app.bunmusic.R
import app.bunmusic.core.Skin
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import app.bunmusic.player.Playback
import app.bunmusic.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** The four widgets, each with its own provider class, layout and metadata. */
enum class WidgetKind(
    val cls: Class<*>,
    val layout: Int,
    val defaultWidthDp: Int,
    val defaultHeightDp: Int,
) {
    MEDIA(PlayerWidget::class.java, R.layout.widget_player, 250, 110),
    COMPACT(CompactWidget::class.java, R.layout.widget_compact, 110, 110),
    BAR(BarWidget::class.java, R.layout.widget_bar, 250, 40),
    QUEUE(QueueWidget::class.java, R.layout.widget_queue, 250, 250),
}

/**
 * Draws a widget.
 *
 * The launcher owns the pixels, so everything here has to be expressible as a
 * RemoteViews action: no Compose, no custom views, no tint lists below API 31.
 * Two consequences shape the code — the progress bar is a bitmap (a ProgressBar
 * cannot be tinted portably), and cover art is rounded before it is handed over
 * (an ImageView cannot clip).
 */
internal object WidgetRenderer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fetching = ConcurrentHashMap.newKeySet<String>()

    fun update(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        kind: WidgetKind,
        force: Boolean = false,
    ) {
        val state = Playback.state.value
        val look = WidgetStore.look(context, id)
        val options = runCatching { manager.getAppWidgetOptions(id) }.getOrNull() ?: Bundle()
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 }
            ?: kind.defaultWidthDp
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).takeIf { it > 0 }
            ?: kind.defaultHeightDp

        val artPx = when (kind) {
            WidgetKind.COMPACT -> 320
            WidgetKind.MEDIA -> 180
            WidgetKind.BAR -> 140
            WidgetKind.QUEUE -> 140
        }
        val radiusDp = if (kind == WidgetKind.COMPACT) look.shape.radiusDp else look.shape.artRadiusDp
        val radiusPx = (radiusDp * context.resources.displayMetrics.density).toInt()
        val track = state.track
        val art = track?.artUrl?.let { WidgetArt.cached(it, artPx, radiusPx) }

        val views = when (kind) {
            WidgetKind.MEDIA -> media(context, id, look, state, width, height, art)
            WidgetKind.COMPACT -> compact(context, id, look, state, width, art)
            WidgetKind.BAR -> bar(context, id, look, state, art)
            WidgetKind.QUEUE -> queue(context, id, look, state)
        }
        runCatching { manager.updateAppWidget(id, views) }

        // The first draw after a reboot has no art. Fetch it once, then redraw;
        // the guard stops a failing URL from retrying on every tick.
        val url = track?.artUrl
        if (art == null && !url.isNullOrBlank()) {
            val key = "$id|$url"
            if (fetching.add(key)) {
                scope.launch {
                    val loaded = runCatching { WidgetArt.load(context, url, artPx, radiusPx) }.getOrNull()
                    fetching.remove(key)
                    // Only redraw when there is something new to draw. Retrying
                    // on failure would spin forever on a dead URL.
                    if (loaded != null) update(context, manager, id, kind, force = true)
                }
            }
        }
    }

    // ------------------------------------------------------------------ 4x2

    private fun media(
        context: Context,
        id: Int,
        look: WidgetLook,
        state: PlayerState,
        widthDp: Int,
        heightDp: Int,
        art: Bitmap?,
    ): RemoteViews {
        val p = look.palette
        val radius = (look.shape.artRadiusDp * context.resources.displayMetrics.density).toInt()
        val v = RemoteViews(context.packageName, R.layout.widget_player)
        v.setInt(R.id.widget_root, "setBackgroundResource", look.cardRes)
        v.setTextColor(R.id.widget_title, p.text.toArgb())
        v.setTextColor(R.id.widget_artist, p.muted.toArgb())
        v.setTextColor(R.id.widget_elapsed, p.muted.toArgb())
        v.setTextColor(R.id.widget_duration, p.muted.toArgb())
        v.setInt(R.id.widget_prev, "setColorFilter", p.muted.toArgb())
        v.setInt(R.id.widget_next, "setColorFilter", p.muted.toArgb())
        v.setInt(R.id.widget_queue, "setColorFilter", p.muted.toArgb())
        v.setInt(R.id.widget_toggle, "setBackgroundResource", look.playRes)
        v.setInt(R.id.widget_toggle, "setColorFilter", p.onAccent.toArgb())

        // A short cell loses the time row before it loses the bar, and a narrow
        // one loses the outer buttons. Nothing is ever clipped mid-glyph.
        val showTimes = look.times && heightDp >= 118
        val showProgress = look.progress && heightDp >= 96
        val showLike = look.like && widthDp >= 200
        val showQueue = look.queue && widthDp >= 200
        v.setViewVisibility(R.id.widget_times, if (showTimes) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.widget_progress, if (showProgress) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.widget_like, if (showLike) View.VISIBLE else View.GONE)
        v.setViewVisibility(R.id.widget_queue, if (showQueue) View.VISIBLE else View.GONE)

        val track = state.track
        if (track == null) {
            v.setTextViewText(R.id.widget_title, "BunMusic")
            v.setTextViewText(R.id.widget_artist, "Tap to start listening")
            v.setImageViewBitmap(R.id.widget_art, WidgetArt.placeholder(p, 180, radius))
            v.setImageViewResource(R.id.widget_toggle, R.drawable.ic_w_play)
            v.setViewVisibility(R.id.widget_progress, View.GONE)
            v.setViewVisibility(R.id.widget_times, View.GONE)
            v.setViewVisibility(R.id.widget_like, View.GONE)
            v.setViewVisibility(R.id.widget_queue, View.GONE)
            v.setOnClickPendingIntent(R.id.widget_root, WidgetIntents.open(context, player = false))
            v.setOnClickPendingIntent(R.id.widget_art, WidgetIntents.open(context, player = false))
            v.setOnClickPendingIntent(R.id.widget_toggle, WidgetIntents.open(context, player = false))
            v.setOnClickPendingIntent(R.id.widget_prev, null)
            v.setOnClickPendingIntent(R.id.widget_next, null)
            return v
        }

        v.setTextViewText(R.id.widget_title, track.title.ifBlank { "Unknown track" })
        v.setTextViewText(R.id.widget_artist, track.creditLine())
        v.setImageViewBitmap(
            R.id.widget_art,
            art ?: WidgetArt.placeholder(p, 180, radius),
        )
        v.setImageViewResource(
            R.id.widget_toggle,
            if (state.isPlaying) R.drawable.ic_w_pause else R.drawable.ic_w_play,
        )
        v.setContentDescription(
            R.id.widget_toggle,
            if (state.isPlaying) "Pause" else "Play",
        )

        val duration = if (state.durationMs > 0) state.durationMs else track.durationMs
        val fraction = WidgetText.fraction(state.positionMs, duration)
        if (showProgress) {
            v.setImageViewBitmap(R.id.widget_progress, WidgetArt.progress(p, fraction))
        }
        if (showTimes) {
            v.setTextViewText(R.id.widget_elapsed, WidgetText.clock(state.positionMs))
            v.setTextViewText(R.id.widget_duration, WidgetText.clock(duration))
        }

        val liked = Store.isLiked(track.id)
        v.setImageViewResource(
            R.id.widget_like,
            if (liked) R.drawable.ic_w_heart_fill else R.drawable.ic_w_heart,
        )
        v.setInt(R.id.widget_like, "setColorFilter", (if (liked) p.rose else p.muted).toArgb())
        v.setContentDescription(R.id.widget_like, if (liked) "Remove from liked songs" else "Like")

        v.setOnClickPendingIntent(R.id.widget_root, WidgetIntents.open(context, player = true))
        v.setOnClickPendingIntent(R.id.widget_art, WidgetIntents.open(context, player = true))
        v.setOnClickPendingIntent(R.id.widget_toggle, WidgetIntents.action(context, Widgets.ACTION_TOGGLE))
        v.setOnClickPendingIntent(R.id.widget_prev, WidgetIntents.action(context, Widgets.ACTION_PREV))
        v.setOnClickPendingIntent(R.id.widget_next, WidgetIntents.action(context, Widgets.ACTION_NEXT))
        v.setOnClickPendingIntent(R.id.widget_like, WidgetIntents.action(context, Widgets.ACTION_LIKE))
        v.setOnClickPendingIntent(R.id.widget_queue, WidgetIntents.open(context, player = true, queue = true))
        return v
    }

    // ------------------------------------------------------------------ 2x2

    private fun compact(
        context: Context,
        id: Int,
        look: WidgetLook,
        state: PlayerState,
        widthDp: Int,
        art: Bitmap?,
    ): RemoteViews {
        val p = look.palette
        val radius = (look.shape.radiusDp * context.resources.displayMetrics.density).toInt()
        val v = RemoteViews(context.packageName, R.layout.widget_compact)
        v.setInt(R.id.widget_root, "setBackgroundResource", look.cardRes)
        v.setInt(R.id.widget_scrim, "setBackgroundResource", WidgetRes.scrim(look.shape))
        v.setInt(R.id.widget_toggle, "setBackgroundResource", look.playRes)
        v.setInt(R.id.widget_toggle, "setColorFilter", p.onAccent.toArgb())
        v.setTextColor(R.id.widget_title, 0xFFFFFFFF.toInt())
        v.setTextColor(R.id.widget_artist, 0xE6FFFFFF.toInt())

        // At the 110dp minimum the text column is 53dp wide. Keeping the artist
        // line there would truncate the title to "The / Last" — so the artist
        // goes and the title takes both lines. Wider cells keep both.
        v.setViewVisibility(
            R.id.widget_artist,
            if (widthDp < 140) View.GONE else View.VISIBLE,
        )

        val track = state.track
        if (track == null) {
            v.setTextViewText(R.id.widget_title, "BunMusic")
            v.setTextViewText(R.id.widget_artist, "Tap to listen")
            v.setImageViewBitmap(R.id.widget_art, WidgetArt.placeholder(p, 320, radius))
            v.setImageViewResource(R.id.widget_toggle, R.drawable.ic_w_play)
            v.setOnClickPendingIntent(R.id.widget_root, WidgetIntents.open(context, player = false))
            v.setOnClickPendingIntent(R.id.widget_toggle, WidgetIntents.open(context, player = false))
            return v
        }

        v.setTextViewText(R.id.widget_title, track.title.ifBlank { "Unknown track" })
        v.setTextViewText(R.id.widget_artist, track.artist.ifBlank { track.album })
        v.setImageViewBitmap(
            R.id.widget_art,
            art ?: WidgetArt.placeholder(p, 320, radius),
        )
        v.setImageViewResource(
            R.id.widget_toggle,
            if (state.isPlaying) R.drawable.ic_w_pause else R.drawable.ic_w_play,
        )
        v.setContentDescription(R.id.widget_toggle, if (state.isPlaying) "Pause" else "Play")
        v.setOnClickPendingIntent(R.id.widget_root, WidgetIntents.open(context, player = true))
        v.setOnClickPendingIntent(R.id.widget_toggle, WidgetIntents.action(context, Widgets.ACTION_TOGGLE))
        return v
    }

    // ------------------------------------------------------------------ 4x1

    private fun bar(
        context: Context,
        id: Int,
        look: WidgetLook,
        state: PlayerState,
        art: Bitmap?,
    ): RemoteViews {
        val p = look.palette
        val radius = (8 * context.resources.displayMetrics.density).toInt()
        val v = RemoteViews(context.packageName, R.layout.widget_bar)
        v.setInt(R.id.widget_root, "setBackgroundResource", look.cardRes)
        v.setTextColor(R.id.widget_title, p.text.toArgb())
        v.setTextColor(R.id.widget_artist, p.muted.toArgb())
        v.setInt(R.id.widget_next, "setColorFilter", p.muted.toArgb())
        v.setInt(R.id.widget_toggle, "setBackgroundResource", look.playRes)
        v.setInt(R.id.widget_toggle, "setColorFilter", p.onAccent.toArgb())

        val track = state.track
        if (track == null) {
            v.setTextViewText(R.id.widget_title, "BunMusic")
            v.setTextViewText(R.id.widget_artist, "Tap to listen")
            v.setImageViewBitmap(R.id.widget_art, WidgetArt.placeholder(p, 140, radius))
            v.setImageViewResource(R.id.widget_toggle, R.drawable.ic_w_play)
            v.setOnClickPendingIntent(R.id.widget_root, WidgetIntents.open(context, player = false))
            v.setOnClickPendingIntent(R.id.widget_toggle, WidgetIntents.open(context, player = false))
            v.setOnClickPendingIntent(R.id.widget_next, null)
            return v
        }

        v.setTextViewText(R.id.widget_title, track.title.ifBlank { "Unknown track" })
        v.setTextViewText(R.id.widget_artist, track.artist.ifBlank { track.album })
        v.setImageViewBitmap(R.id.widget_art, art ?: WidgetArt.placeholder(p, 140, radius))
        v.setImageViewResource(
            R.id.widget_toggle,
            if (state.isPlaying) R.drawable.ic_w_pause else R.drawable.ic_w_play,
        )
        v.setContentDescription(R.id.widget_toggle, if (state.isPlaying) "Pause" else "Play")
        v.setOnClickPendingIntent(R.id.widget_root, WidgetIntents.open(context, player = true))
        v.setOnClickPendingIntent(R.id.widget_toggle, WidgetIntents.action(context, Widgets.ACTION_TOGGLE))
        v.setOnClickPendingIntent(R.id.widget_next, WidgetIntents.action(context, Widgets.ACTION_NEXT))
        return v
    }

    // ------------------------------------------------------------------ 4x4

    private fun queue(
        context: Context,
        id: Int,
        look: WidgetLook,
        state: PlayerState,
    ): RemoteViews {
        val p = look.palette
        val v = RemoteViews(context.packageName, R.layout.widget_queue)
        v.setInt(R.id.widget_root, "setBackgroundResource", look.cardRes)
        v.setTextColor(R.id.widget_queue_title, p.text.toArgb())
        v.setTextColor(R.id.widget_queue_sub, p.muted.toArgb())
        v.setInt(R.id.widget_toggle, "setBackgroundResource", look.playRes)
        v.setInt(R.id.widget_toggle, "setColorFilter", p.onAccent.toArgb())

        val track = state.track
        val upcoming = if (state.index >= 0) state.queue.size - state.index - 1 else 0
        v.setTextViewText(R.id.widget_queue_title, "Up next")
        v.setTextViewText(
            R.id.widget_queue_sub,
            when {
                track == null -> "Nothing playing"
                upcoming <= 0 -> "${track.title} · queue ends here"
                else -> "${track.title} · $upcoming more"
            },
        )
        v.setImageViewResource(
            R.id.widget_toggle,
            if (state.isPlaying) R.drawable.ic_w_pause else R.drawable.ic_w_play,
        )

        val service = Intent(context, QueueWidgetService::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        v.setRemoteAdapter(R.id.widget_list, service)
        v.setPendingIntentTemplate(R.id.widget_list, WidgetIntents.jumpTemplate(context))
        v.setOnClickPendingIntent(R.id.widget_root, WidgetIntents.open(context, player = true))
        v.setOnClickPendingIntent(R.id.widget_toggle, WidgetIntents.action(context, Widgets.ACTION_TOGGLE))
        return v
    }
}

/** PendingIntents for the launcher. Cached by the system; request codes are stable. */
internal object WidgetIntents {

    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun action(context: Context, action: String): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, FLAGS)
    }

    fun open(context: Context, player: Boolean, queue: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("open_player", player)
            .putExtra("open_queue", queue)
            .setData(Uri.parse("bunmusic://widget/open?player=$player&queue=$queue"))
        return PendingIntent.getActivity(context, if (player) 4101 else 4102, intent, FLAGS)
    }

    fun jumpTemplate(context: Context): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).setAction(Widgets.ACTION_JUMP)
        // Mutable on purpose: the launcher merges each row's fill-in intent into
        // this template, and an immutable PendingIntent silently drops it — the
        // list would render and every row would do nothing.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(context, 4103, intent, flags)
    }
}

/** Shared action handling, so a stale PendingIntent from 1.0.12 still works. */
internal object WidgetActions {

    fun handle(context: Context, action: String?, index: Int = -1): Boolean {
        val state = Playback.state.value
        when (action) {
            Widgets.ACTION_TOGGLE -> {
                if (state.hasTrack) Playback.toggle() else Widgets.openApp(context, player = true)
            }
            Widgets.ACTION_NEXT -> {
                if (state.hasTrack) Playback.next() else Widgets.openApp(context, player = true)
            }
            Widgets.ACTION_PREV -> {
                if (state.hasTrack) Playback.previous() else Widgets.openApp(context, player = true)
            }
            Widgets.ACTION_LIKE -> {
                state.track?.let { Store.toggleLike(it.id) }
            }
            Widgets.ACTION_JUMP -> {
                if (index in state.queue.indices) Playback.jumpTo(index)
            }
            Widgets.ACTION_REFRESH -> Unit
            else -> return false
        }
        Widgets.pushAll(context, force = true)
        return true
    }
}
