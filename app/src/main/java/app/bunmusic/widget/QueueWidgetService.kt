package app.bunmusic.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import app.bunmusic.R
import app.bunmusic.data.Track
import app.bunmusic.player.Playback
import androidx.compose.ui.graphics.toArgb

/**
 * Backs the 4x4 queue widget's list.
 *
 * The factory runs on a binder thread and is rebuilt whenever
 * `notifyAppWidgetViewDataChanged` fires, so it always reads the live queue
 * rather than keeping a copy that can drift.
 */
class QueueWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        QueueFactory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
}

private class QueueFactory(
    private val context: Context,
    private val widgetId: Int,
) : RemoteViewsService.RemoteViewsFactory {

    private var rows: List<Track> = emptyList()
    private var start = 0
    private var look: WidgetLook = WidgetLook.Default

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        look = WidgetStore.look(context, widgetId)
        val state = Playback.state.value
        start = (state.index + 1).coerceAtLeast(0)
        rows = if (start in state.queue.indices) state.queue.drop(start) else emptyList()
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val p = look.palette
        val view = RemoteViews(context.packageName, R.layout.widget_queue_item)
        val track = rows.getOrNull(position)
        if (track == null) {
            // A row can be asked for while the queue is being trimmed.
            view.setTextViewText(R.id.row_title, "")
            view.setTextViewText(R.id.row_artist, "")
            return view
        }
        val index = start + position
        view.setTextViewText(R.id.row_index, "${index + 1}")
        view.setTextViewText(R.id.row_title, track.title.ifBlank { "Unknown track" })
        view.setTextViewText(R.id.row_artist, track.artist.ifBlank { track.album })
        view.setTextViewText(R.id.row_time, WidgetText.clock(track.durationMs))
        view.setTextColor(R.id.row_index, p.muted.toArgb())
        view.setTextColor(R.id.row_title, p.text.toArgb())
        view.setTextColor(R.id.row_artist, p.muted.toArgb())
        view.setTextColor(R.id.row_time, p.muted.toArgb())

        // Disk only: this runs on a binder thread with a deadline, and ten
        // network fetches would make the list arrive blank.
        val art = WidgetArt.cachedOnDisk(context, track.artUrl, 96, 20)
            ?: WidgetArt.placeholder(p, 96, 20)
        view.setImageViewBitmap(R.id.row_art, art)

        val fill = Intent()
            .setData(Uri.parse("bunmusic://widget/jump/$index"))
            .putExtra(Widgets.EXTRA_INDEX, index)
        view.setOnClickFillInIntent(R.id.row_root, fill)
        return view
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
