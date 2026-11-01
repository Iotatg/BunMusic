package app.bunmusic.system

import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import app.bunmusic.player.BunNotificationProvider
import app.bunmusic.player.ServiceLauncher

/**
 * Why the lock screen is empty, in four questions.
 *
 * A notification is not one feature that either works or does not. Four
 * independent things have to be true at once, and when one of them is false the
 * result looks identical from the outside: nothing appears, and the app looks
 * broken rather than blocked. This turns those four into something a listener can
 * read on the screen and act on:
 *
 *  1. the permission (Android 13+ drops the notification silently without it);
 *  2. the channel (a muted channel is the same silence with a different cause);
 *  3. the service (no service means no session, and no session means no card);
 *  4. the battery rule (some phones kill the service the moment the screen goes
 *     off — the card appears and then disappears, which is worse than never
 *     appearing, because it looks like a crash).
 *
 * Only the first two can make the card impossible. A stopped service is normal
 * while nothing is playing, and an app that is not battery-exempt still works on
 * most phones — those are [State.WARN], not failures, because a diagnostics
 * screen that shouts about a healthy default is a screen nobody reads.
 *
 * [checks] is pure: it takes booleans and returns the list, so the wording and
 * the ordering are testable without a device. [read] is the only part that
 * touches Android.
 */
object PlaybackControls {

    /** Where a failing check can send the listener. */
    enum class Fix {
        /** The app's own notification page. */
        NOTIFICATIONS,

        /** The channel's page — a different screen on some OEM skins. */
        CHANNEL,

        /** Battery optimisation, which is what kills the service at night. */
        BATTERY,
    }

    enum class State { OK, WARN, FAIL }

    data class Check(
        val id: String,
        val title: String,
        val detail: String,
        val state: State,
        val fix: Fix?,
    ) {
        val ok: Boolean get() = state == State.OK
    }

    const val PERMISSION = "permission"
    const val CHANNEL = "channel"
    const val SERVICE = "service"
    const val BATTERY = "battery"

    /**
     * The four checks, in the order they fail in real life: permission first,
     * because without it nothing else matters.
     */
    fun checks(
        notificationsEnabled: Boolean,
        channelBlocked: Boolean,
        serviceRunning: Boolean,
        batteryRestricted: Boolean,
    ): List<Check> = listOf(
        Check(
            PERMISSION,
            "Notification permission",
            if (notificationsEnabled) {
                "Granted — the controls and the bar are allowed to appear"
            } else {
                "Denied — Android 13 and later drop the notification without a word"
            },
            if (notificationsEnabled) State.OK else State.FAIL,
            if (notificationsEnabled) null else Fix.NOTIFICATIONS,
        ),
        Check(
            CHANNEL,
            "Playback channel",
            when {
                !notificationsEnabled -> "Waiting for the permission above"
                channelBlocked -> "Muted in system settings — the channel is off"
                else -> "On — the media channel is not muted"
            },
            when {
                !notificationsEnabled -> State.WARN
                channelBlocked -> State.FAIL
                else -> State.OK
            },
            if (notificationsEnabled && channelBlocked) Fix.CHANNEL else null,
        ),
        Check(
            SERVICE,
            "Playback service",
            if (serviceRunning) {
                "Running — the system has a session to draw controls from"
            } else {
                "Idle — it starts with the first track and stays while music plays"
            },
            if (serviceRunning) State.OK else State.WARN,
            null,
        ),
        Check(
            BATTERY,
            "Battery rule",
            if (batteryRestricted) {
                "Restricted — some phones stop playback when the screen goes off"
            } else {
                "BunMusic is exempt — playback survives a locked screen"
            },
            if (batteryRestricted) State.WARN else State.OK,
            if (batteryRestricted) Fix.BATTERY else null,
        ),
    )

    /**
     * One line for the section header.
     *
     * Names the blocking check instead of counting it: "1 check blocks the
     * controls" still leaves the listener hunting, and the permission and the
     * channel are the only two that can block, so the name always fits.
     */
    fun headline(checks: List<Check>): String {
        val blocker = checks.firstOrNull { it.state == State.FAIL }
        if (blocker != null) return "Blocked — ${blocker.title.lowercase()} needs fixing"
        // Only warnings that have a page count: an idle service is the normal
        // state of an app that is not playing, and calling it a problem would
        // teach the listener to ignore this line.
        val warned = checks.count { it.state == State.WARN && it.fix != null }
        return when (warned) {
            0 -> "Everything the lock screen needs is in place"
            1 -> "Controls work · 1 setting could still cut playback short"
            else -> "Controls work · $warned settings could still cut playback short"
        }
    }

    // ------------------------------------------------------------------ probe

    /** Read the real state of the phone. */
    fun read(context: Context): List<Check> {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = runCatching {
            manager?.getNotificationChannel(BunNotificationProvider.CHANNEL_ID)
        }.getOrNull()
        val power = context.getSystemService(PowerManager::class.java)

        return checks(
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            channelBlocked = channel?.importance == NotificationManager.IMPORTANCE_NONE,
            serviceRunning = ServiceLauncher.isRunning(context),
            batteryRestricted = power?.isIgnoringBatteryOptimizations(context.packageName) != true,
        )
    }
}
