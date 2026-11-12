package app.bunmusic.player

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.bunmusic.R

/**
 * One notification, posted by hand, to answer a single question: does this phone
 * show BunMusic's notifications at all?
 *
 * The playback card cannot answer that, because it needs the service, the
 * session, a resolved stream and a playing track before it exists. When nothing
 * appears there are five possible reasons and no way to tell them apart from the
 * outside. This card has none of those dependencies: if it shows, the permission
 * and the channel are fine and the problem is further down; if it does not, no
 * amount of session wiring will help.
 *
 * It deliberately uses the same channel as playback, so it also proves the
 * channel itself is not muted — a different channel would prove nothing.
 */
object TestCard {

    const val ID = 1002

    /** Returns false when the phone refuses to post anything at all. */
    fun post(context: Context): Boolean {
        BunNotificationProvider.ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        // API 33+ makes POST_NOTIFICATIONS a runtime permission. Checking it
        // explicitly (rather than relying on notify() throwing) is both what
        // Lint needs to see and the honest answer to "will this card show?".
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return false

        val open = PendingIntent.getActivity(
            context,
            3,
            Intent(context, app.bunmusic.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = "If you can see this, the phone allows the card and the channel " +
            "is not muted — so the problem is the session, not the permission."

        val card = NotificationCompat.Builder(context, BunNotificationProvider.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("BunMusic test card")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        return runCatching {
            manager.notify(ID, card)
            true
        }.getOrDefault(false)
    }

    fun clear(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID) }
    }
}
