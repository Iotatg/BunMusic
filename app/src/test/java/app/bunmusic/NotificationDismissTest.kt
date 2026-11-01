package app.bunmusic

import app.bunmusic.player.BunNotificationProvider
import app.bunmusic.player.Dismiss
import app.bunmusic.player.TestCard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two small rules around the card that are easy to get wrong and impossible
 * to see in a screenshot.
 */
class NotificationDismissTest {

    @Test
    fun `a playing card is not dismissible, so a swipe means nothing`() {
        // While playing the notification is ongoing: the system will not deliver
        // the delete intent at all. If it ever did, stopping the music would be
        // the wrong answer to "I tidied my shade".
        assertFalse(Dismiss.shouldStop(isPlaying = true))
    }

    @Test
    fun `a paused card stops the service when swiped`() {
        assertTrue(Dismiss.shouldStop(isPlaying = false))
    }

    @Test
    fun `the test card never takes the playback card's slot`() {
        // Same id would mean posting a test silently replaced a playing track's
        // notification, and the controls would vanish while the music went on.
        assertNotEquals(BunNotificationProvider.NOTIFICATION_ID, TestCard.ID)
    }
}
