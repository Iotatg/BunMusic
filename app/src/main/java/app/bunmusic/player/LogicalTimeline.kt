package app.bunmusic.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline

/**
 * A one-window timeline for the track BunMusic *has*, even when ExoPlayer has
 * not prepared it yet.
 *
 * ExoPlayer only ever holds one media item, and BunMusic deliberately does not
 * prepare a restored session until the listener presses play — a cold start on
 * mobile data should not silently open a socket. That leaves the real timeline
 * empty while [app.bunmusic.player.Playback.state] already carries a track.
 * Media3's notification manager sees the empty timeline and cancels the card,
 * regardless of the idle-player mode.
 *
 * This timeline is what the media session reports in that window. It carries
 * the same [MediaItem] used for media resumption, so the lock screen and the
 * system media controls get a title, an artist and an artwork URI instead of a
 * blank card. Once a real media source is prepared, [SessionBridge] returns
 * ExoPlayer's own timeline again.
 *
 * It is a plain value object: no player, no service, no Android threads.
 */
internal class LogicalTimeline(
    private val item: MediaItem,
    durationMs: Long,
) : Timeline() {

    private val windowUid: Any = "bun-window:${item.mediaId}"
    private val periodUid: Any = "bun-period:${item.mediaId}"

    // ms -> us is a plain multiply here: a track is never long enough to
    // overflow a Long, and C.TIME_UNSET marks "we only know the search length".
    private val durationUs: Long = if (durationMs > 0L) durationMs * 1_000L else C.TIME_UNSET

    override fun getWindowCount(): Int = 1

    override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window =
        window.set(
            windowUid,
            item,
            /* manifest = */ null,
            /* presentationStartTimeMs = */ C.TIME_UNSET,
            /* windowStartTimeMs = */ C.TIME_UNSET,
            /* elapsedRealtimeEpochOffsetMs = */ C.TIME_UNSET,
            /* isSeekable = */ durationUs != C.TIME_UNSET,
            /* isDynamic = */ false,
            /* liveConfiguration = */ null,
            /* defaultPositionUs = */ 0L,
            durationUs,
            /* firstPeriodIndex = */ 0,
            /* lastPeriodIndex = */ 0,
            /* positionInFirstPeriodUs = */ 0L,
        )

    override fun getPeriodCount(): Int = 1

    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period =
        period.set(if (setIds) periodUid else null, periodUid, 0, durationUs, 0L)

    override fun getIndexOfPeriod(uid: Any): Int = if (uid == periodUid) 0 else C.INDEX_UNSET

    override fun getUidOfPeriod(periodIndex: Int): Any = periodUid
}
