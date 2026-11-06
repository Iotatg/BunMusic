package app.bunmusic

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import app.bunmusic.player.LogicalTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Media3 cancels the notification whenever `getCurrentTimeline().isEmpty()`.
 * A restored session is real but unprepared, so this timeline stands in for it
 * until a media source is ready.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogicalTimelineTest {

    private val item: MediaItem = MediaItem.Builder().setMediaId("track-1").build()

    @Test
    fun `a restored track is never an empty timeline`() {
        val timeline = LogicalTimeline(item, 180_000L)
        assertFalse(timeline.isEmpty)
        assertEquals(1, timeline.windowCount)
        assertEquals(1, timeline.periodCount)
    }

    @Test
    fun `the window carries the known duration and is seekable`() {
        val window = LogicalTimeline(item, 180_000L).getWindow(0, Timeline.Window())
        assertEquals(180_000L * 1_000L, window.durationUs)
        assertTrue(window.isSeekable)
        assertFalse(window.isDynamic)
        assertEquals(item.mediaId, window.mediaItem?.mediaId)
    }

    @Test
    fun `an unknown duration is reported honestly as unset`() {
        val window = LogicalTimeline(item, 0L).getWindow(0, Timeline.Window())
        assertEquals(C.TIME_UNSET, window.durationUs)
        assertFalse(window.isSeekable)
    }

    @Test
    fun `period uids round trip`() {
        val timeline = LogicalTimeline(item, 10_000L)
        val period = timeline.getPeriod(0, Timeline.Period(), true)
        assertEquals(0, period.windowIndex)
        assertEquals(0, timeline.getIndexOfPeriod(timeline.getUidOfPeriod(0)))
        assertEquals(C.INDEX_UNSET, timeline.getIndexOfPeriod("some-other-uid"))
    }
}
