package app.bunmusic

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.bunmusic.data.Prefs
import app.bunmusic.data.Store
import app.bunmusic.ui.seekSemantics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The edge cases the shared seek contract promises but a happy-path gesture
 * test never reaches: an unknown duration, and out-of-range scrub fractions.
 *
 * A screen reader must never be told "scrubbed to 60%" when the app quietly did
 * nothing, and a fraction outside 0..1 must not seek past the ends of a song.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SeekSemanticsTest {

    @get:Rule val compose = createComposeRule()

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        Prefs.init(ctx)
        Store.init(ctx)
    }

    private val adjustable = SemanticsMatcher("an adjustable seek control") { node ->
        node.config.contains(SemanticsProperties.ProgressBarRangeInfo)
    }

    private fun mount(positionMs: Long, durationMs: Long, seeks: MutableList<Long>) {
        compose.setContent {
            Box(Modifier.size(120.dp).seekSemantics(positionMs, durationMs, { seeks += it }))
        }
        compose.waitForIdle()
    }

    private fun config() = compose.onAllNodes(adjustable).fetchSemanticsNodes().single().config

    @Test
    fun unknownDurationIsNotAdjustableAndReportsNotHandled() {
        val seeks = mutableListOf<Long>()
        mount(positionMs = 0L, durationMs = 0L, seeks)

        val cfg = config()
        val range = cfg[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals("progress must read 0 when the duration is unknown", 0f, range.current, 0f)

        // The ±10 s jumps must not be advertised for a track with no length.
        val jumps = cfg.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertTrue("no custom jumps should be offered without a duration", jumps.isEmpty())

        val action = checkNotNull(cfg[SemanticsActions.SetProgress].action) {
            "a SetProgress action must still be present so TalkBack can try"
        }
        val handled = action(0.60f)
        assertFalse("SetProgress must report 'not handled' when duration is unknown", handled)
        assertTrue("no seek may be issued for an unknown duration", seeks.isEmpty())
    }

    @Test
    fun scrubFractionsAndJumpsClampToTheTrack() {
        val duration = 240_000L
        val seeks = mutableListOf<Long>()
        mount(positionMs = 5_000L, durationMs = duration, seeks)

        val cfg = config()
        val action = checkNotNull(cfg[SemanticsActions.SetProgress].action)
        assertTrue(action(2.0f))
        assertEquals("a fraction above 1 clamps to the end", duration, seeks.last())
        assertTrue(action(-1.0f))
        assertEquals("a fraction below 0 clamps to the start", 0L, seeks.last())

        val jumps = cfg.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertEquals("both coarse jumps are offered", 2, jumps.size)
        // position 5 s, so "Back 10 seconds" must clamp at 0 rather than go negative.
        jumps.single { it.label == "Back 10 seconds" }.action()
        assertEquals("rewind clamps at the start of the track", 0L, seeks.last())
    }
}
