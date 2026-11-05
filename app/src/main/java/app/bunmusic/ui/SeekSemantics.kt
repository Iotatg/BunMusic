package app.bunmusic.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress

/**
 * The accessibility contract every seek bar shares.
 *
 * Three of the five buns draw their progress on a `Canvas` with `pointerInput`.
 * To a sighted thumb that is a perfect scrub bar; to TalkBack it used to be
 * empty space — no name, no range, no way to move it. The Material `Slider`
 * (Midnight, the only dark non-Nebula bun) gets all of this from the framework,
 * which is why the same gesture worked for a screen reader on one bun and did
 * nothing on the other four.
 *
 * One modifier closes that gap for the canvas bars, giving each of them the
 * same four things a screen reader needs:
 *
 *  - a `contentDescription` that says the current and total time;
 *  - a [ProgressBarRangeInfo] so TalkBack announces a percentage and marks the
 *    control as adjustable;
 *  - a `SetProgress` action so a screen reader can scrub to a value directly,
 *    without synthesising a drag;
 *  - two custom actions — "Back 10 seconds" and "Forward 10 seconds" — the
 *    coarse jumps a sighted user makes by tapping either side of the bar.
 *
 * Every value is read from the current parameters on each recomposition, so the
 * spoken time and the ±10 s targets always follow the real playback position.
 * The `SetProgress` fraction and the jumps are clamped to the track, and the
 * actions report `false` (not handled) when the duration is unknown, so a
 * screen reader never announces a scrub that silently did nothing.
 */
fun Modifier.seekSemantics(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
): Modifier = semantics {
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    contentDescription = "Seek, ${fmtTime(positionMs)} of ${fmtTime(durationMs)}"
    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
    setProgress { fraction ->
        if (durationMs <= 0L) {
            false
        } else {
            onSeek((fraction.coerceIn(0f, 1f) * durationMs).toLong())
            true
        }
    }
    if (durationMs > 0L) {
        customActions = listOf(
            CustomAccessibilityAction("Back 10 seconds") {
                onSeek((positionMs - 10_000L).coerceIn(0L, durationMs))
                true
            },
            CustomAccessibilityAction("Forward 10 seconds") {
                onSeek((positionMs + 10_000L).coerceIn(0L, durationMs))
                true
            },
        )
    }
}
