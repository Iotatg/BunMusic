package app.bunmusic.player

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Starting and stopping [PlaybackService].
 *
 * This is the piece that was missing. Everything the system can draw — the
 * notification, the lock-screen controls, the Android 13 media player in quick
 * settings, the Bluetooth play button — comes from the `MediaSession` that only
 * exists inside the service. Playback itself ran in the app process on an
 * ExoPlayer the system had never heard of, and nothing ever started the service,
 * so none of it appeared. Not "broken": never created.
 *
 * Two ways to start a service, and picking the wrong one crashes the app:
 *
 *  - `startService` is only allowed while the app is on screen. It is the safe
 *    choice, because the service may take its time to become a foreground
 *    service.
 *  - `startForegroundService` is allowed from the background (Android 12+ blocks
 *    the plain call), but it *obliges* the service to call `startForeground`
 *    within five seconds or the process is killed. That is only acceptable when
 *    playback is already running or about to, which is exactly when Media3 posts
 *    the notification.
 *
 * The widget and the Quick Settings tile can start playback while the app is in
 * the background, so the choice has to be made at runtime rather than hard-coded.
 */
object ServiceLauncher {

    enum class Mode {
        /** The app is on screen — a plain `startService` is allowed. */
        PLAIN,

        /** The app is in the background — Android 12+ only allows a foreground start. */
        FOREGROUND,
    }

    /**
     * Which call to make, decided from one fact so it can be tested without a
     * Context, a service or a device.
     */
    fun modeFor(appInForeground: Boolean): Mode =
        if (appInForeground) Mode.PLAIN else Mode.FOREGROUND

    /**
     * Make sure the service exists. Cheap and idempotent: a started service that
     * is already running just receives another start command, and Media3 ignores
     * it.
     */
    fun start(context: Context) {
        val intent = Intent(context, PlaybackService::class.java)
        when (modeFor(isAppForeground(context))) {
            Mode.PLAIN ->
                runCatching { context.startService(intent) }
                    // Background start restrictions also fire for a process that
                    // lost its visible state between the check and the call.
                    .onFailure { runCatching { ContextCompat.startForegroundService(context, intent) } }

            Mode.FOREGROUND ->
                runCatching { ContextCompat.startForegroundService(context, intent) }
                    // Rare, but a device that refuses the foreground start is
                    // better off with a plain one than with no session at all.
                    .onFailure { runCatching { context.startService(intent) } }
        }
    }

    /**
     * Tear the service down with the playback. A notification that outlives the
     * music is a notification the listener has to swipe away by hand.
     */
    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, PlaybackService::class.java)) }
    }

    /**
     * Is the service alive right now? Only used by the diagnostics screen — the
     * answer is the difference between "the notification is blocked" and "there
     * is nothing to notify about".
     */
    fun isRunning(context: Context): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val running = manager.getRunningServices(Int.MAX_VALUE) ?: return false
        return running.any { it.service?.className == PlaybackService::class.java.name }
    }

    /**
     * Foreground *or* visible. A translucent activity or a paused-but-visible
     * one still counts as foreground for the purpose of starting a service.
     */
    private fun isAppForeground(context: Context): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }
}
