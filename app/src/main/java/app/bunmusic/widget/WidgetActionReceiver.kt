package app.bunmusic.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Every button on every widget lands here.
 *
 * A receiver rather than the provider itself, so the same action can be sent
 * from the widget, a notification or a future shortcut without each of them
 * knowing which provider is on screen.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val index = intent.getIntExtra(Widgets.EXTRA_INDEX, -1)
        WidgetActions.handle(context, intent.action, index)
    }
}
