package app.bunmusic.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Four providers, one implementation.
 *
 * They have to be separate classes because `AppWidgetProviderInfo` points at a
 * receiver, and the launcher identifies a widget by that receiver. The bodies
 * are identical, so they all delegate here.
 */
abstract class BunWidgetProvider(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widgets.notePlaced()
        ids.forEach { WidgetRenderer.update(context, manager, it, kind, force = true) }
    }

    override fun onEnabled(context: Context) {
        Widgets.notePlaced()
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle,
    ) {
        // Resizing is the moment the layout has to change its mind about how
        // much it can show, so this is a full redraw rather than a nudge.
        Widgets.notePlaced()
        WidgetRenderer.update(context, manager, id, kind, force = true)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        WidgetStore.forget(context, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Old installs still carry PendingIntents aimed at PlayerWidget with the
        // 1.0.12 action names. They land here, and are handled rather than
        // ignored, so an upgraded widget keeps working before its first redraw.
        if (WidgetActions.handle(context, intent.action, intent.getIntExtra(Widgets.EXTRA_INDEX, -1))) return
        super.onReceive(context, intent)
    }
}

class PlayerWidget : BunWidgetProvider(WidgetKind.MEDIA)

class CompactWidget : BunWidgetProvider(WidgetKind.COMPACT)

class BarWidget : BunWidgetProvider(WidgetKind.BAR)

class QueueWidget : BunWidgetProvider(WidgetKind.QUEUE)
