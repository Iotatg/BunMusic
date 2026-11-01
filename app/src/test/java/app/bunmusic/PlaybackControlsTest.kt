package app.bunmusic

import app.bunmusic.system.PlaybackControls
import app.bunmusic.system.PlaybackControls.Fix
import app.bunmusic.system.PlaybackControls.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The diagnostics screen, tested as a truth table.
 *
 * The point of the four checks is that they are independent, and that only two of
 * them can actually stop the notification. Everything below pins that
 * distinction: a denied permission or a muted channel is a failure, a stopped
 * service or a non-exempt battery setting is a warning. Getting that wrong is how
 * a settings screen ends up crying wolf at a phone that works perfectly.
 */
class PlaybackControlsTest {

    private fun run(
        notifications: Boolean = true,
        channel: Boolean = false,
        service: Boolean = true,
        battery: Boolean = false,
    ) = PlaybackControls.checks(
        notificationsEnabled = notifications,
        channelBlocked = channel,
        serviceRunning = service,
        batteryRestricted = battery,
    )

    private fun List<PlaybackControls.Check>.id(id: String) = first { it.id == id }

    @Test
    fun `all four checks are always reported in a fixed order`() {
        val ids = run().map { it.id }
        assertEquals(
            listOf(
                PlaybackControls.PERMISSION,
                PlaybackControls.CHANNEL,
                PlaybackControls.SERVICE,
                PlaybackControls.BATTERY,
            ),
            ids,
        )
    }

    @Test
    fun `a denied permission is the first thing said`() {
        val checks = run(notifications = false)
        val permission = checks.id(PlaybackControls.PERMISSION)
        assertEquals(State.FAIL, permission.state)
        assertEquals(Fix.NOTIFICATIONS, permission.fix)
    }

    @Test
    fun `a denied permission makes the channel moot rather than broken`() {
        // Without the permission the channel cannot matter, and a second red row
        // for the same cause would send the listener to the wrong page.
        val checks = run(notifications = false, channel = true)
        assertEquals(State.WARN, checks.id(PlaybackControls.CHANNEL).state)
        assertNull(checks.id(PlaybackControls.CHANNEL).fix)
    }

    @Test
    fun `a muted channel is a failure with its own page`() {
        val channel = run(channel = true).id(PlaybackControls.CHANNEL)
        assertEquals(State.FAIL, channel.state)
        assertEquals(Fix.CHANNEL, channel.fix)
    }

    @Test
    fun `a stopped service is normal, not broken`() {
        // Nothing is playing; the service starts with the first track. A red row
        // here would be a false alarm on every cold start.
        val service = run(service = false).id(PlaybackControls.SERVICE)
        assertEquals(State.WARN, service.state)
        assertNull(service.fix)
    }

    @Test
    fun `a restricted battery rule is a warning that points at the list`() {
        val battery = run(battery = true).id(PlaybackControls.BATTERY)
        assertEquals(State.WARN, battery.state)
        assertEquals(Fix.BATTERY, battery.fix)
    }

    @Test
    fun `an exempt battery rule is silent`() {
        val battery = run(battery = false).id(PlaybackControls.BATTERY)
        assertEquals(State.OK, battery.state)
        assertNull(battery.fix)
    }

    @Test
    fun `a healthy phone says so in one line`() {
        val checks = run(service = true, battery = false)
        assertTrue(checks.all { it.ok })
        assertEquals("Everything the lock screen needs is in place", PlaybackControls.headline(checks))
    }

    @Test
    fun `warnings alone do not claim the controls are blocked`() {
        val checks = run(service = false, battery = true)
        assertTrue(checks.none { it.state == State.FAIL })
        val headline = PlaybackControls.headline(checks)
        assertTrue(headline.startsWith("Controls work"))
        // Only the battery rule has a page to send the listener to; the idle
        // service is normal and is not counted as a setting to fix.
        assertTrue(headline.contains("1 setting"))
    }

    @Test
    fun `an idle service is not a complaint`() {
        // Opening Settings before playing anything must not look like a fault.
        assertEquals(
            "Everything the lock screen needs is in place",
            PlaybackControls.headline(run(service = false)),
        )
    }

    @Test
    fun `one failure is named rather than counted`() {
        // "1 check blocks the controls" leaves the listener hunting through four
        // rows; naming the row is the whole point of a diagnostics screen.
        assertEquals(
            "Blocked — notification permission needs fixing",
            PlaybackControls.headline(run(notifications = false)),
        )
        assertEquals(
            "Blocked — playback channel needs fixing",
            PlaybackControls.headline(run(channel = true)),
        )
    }

    @Test
    fun `every failing check carries a fix, and no healthy one does`() {
        val checks = run(notifications = false, channel = true, service = false, battery = true)
        checks.forEach { check ->
            when (check.state) {
                State.FAIL -> assertTrue("${check.id} fails without a way out", check.fix != null)
                State.OK -> assertNull("${check.id} offers a fix while healthy", check.fix)
                // A warning may or may not have a page: the service has none.
                State.WARN -> Unit
            }
        }
    }

    @Test
    fun `the detail text never claims success while failing`() {
        // Cheap guard against the copy drifting away from the state: a row that
        // reads "Granted" with a red dot is worse than no row at all.
        val denied = run(notifications = false).id(PlaybackControls.PERMISSION)
        assertTrue(denied.detail.startsWith("Denied"))
        val muted = run(channel = true).id(PlaybackControls.CHANNEL)
        assertTrue(muted.detail.startsWith("Muted"))
    }
}
