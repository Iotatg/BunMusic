package app.bunmusic

import app.bunmusic.player.ServiceLauncher
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one decision that made the lock screen work, or crashed the app.
 *
 * `startService` is only legal while the app is visible. `startForegroundService`
 * is legal anywhere but obliges the service to post a notification within five
 * seconds or the process is killed. Picking wrong gives either a
 * `BackgroundServiceStartNotAllowedException` or a
 * "did not then call Service.startForeground()" crash — both of which are
 * invisible to every other test in this suite, and both of which the listener
 * would experience as "the app is broken".
 */
class ServiceLauncherTest {

    @Test
    fun `on screen the plain start is used`() {
        assertEquals(ServiceLauncher.Mode.PLAIN, ServiceLauncher.modeFor(appInForeground = true))
    }

    @Test
    fun `in the background the foreground start is used`() {
        // A widget tap or a Quick Settings tile: the app is not visible, and a
        // plain startService would throw on Android 8 and later.
        assertEquals(ServiceLauncher.Mode.FOREGROUND, ServiceLauncher.modeFor(appInForeground = false))
    }

    @Test
    fun `the two modes are distinct`() {
        // Guards against a refactor that collapses the enum into a boolean and
        // silently sends every background start down the crashing path.
        assertEquals(2, ServiceLauncher.Mode.entries.size)
    }
}
