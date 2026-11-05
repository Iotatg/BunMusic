package app.bunmusic

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import app.bunmusic.core.BunMotion
import app.bunmusic.core.BunSkin
import app.bunmusic.core.BunTheme
import app.bunmusic.core.BunWindowProvider
import app.bunmusic.core.Ink
import app.bunmusic.core.Skin
import app.bunmusic.data.Prefs
import app.bunmusic.data.Store
import app.bunmusic.ui.AuroraWave
import app.bunmusic.ui.LineSeekBar
import app.bunmusic.ui.PlayerScreen
import app.bunmusic.ui.WaveSeekBar
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The checks a phone would have caught.
 *
 * 2.8.0 shipped with a standing caveat: no device or emulator was available, so
 * TalkBack labels, thumb-sized scrubbing and back inside a sheet had never been
 * exercised. A screenshot harness answers "does it look right"; this one answers
 * "can it be used". It renders the real composables with Robolectric's native
 * graphics, walks the merged semantics tree the way a screen reader does, and
 * injects real taps, drags, accessibility actions and back presses.
 *
 * The seek bars are the interesting case. Three of the five buns draw theirs on
 * a `Canvas` with `pointerInput` — to a sighted thumb that is a perfect scrub
 * bar, to TalkBack it is empty space: no name, no range, no way to adjust it.
 *
 * `AndroidComposeTestRule.setContent` may only be called once per test, so all
 * variants are mounted once and switched with `key(generation)`, which disposes
 * and rebuilds the whole subtree — exactly like Skin.apply() restarting a process.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class InteractionTest {

    @get:Rule val compose = createComposeRule()

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        Prefs.init(ctx)
        Store.init(ctx)
        Sample.seed()
    }

    /** One seek control, with a range a screen reader can announce and adjust. */
    private val seek = SemanticsMatcher("a seek control") { node ->
        node.config.contains(SemanticsProperties.ProgressBarRangeInfo)
    }

    private fun readable(node: SemanticsNode): String {
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString(" ").orEmpty()
        val text = node.config.getOrNull(SemanticsProperties.Text)
            ?.joinToString(" ").orEmpty()
        val state = node.config.getOrNull(SemanticsProperties.StateDescription).orEmpty()
        return listOf(description, text, state).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** Mount once. `content` receives the generation so the caller can switch. */
    private fun mount(endless: Boolean, content: @Composable (Int) -> Unit): MutableIntState {
        val generation = mutableIntStateOf(0)
        if (endless) compose.mainClock.autoAdvance = false
        compose.setContent {
            val g = generation.intValue
            key(g) {
                BunTheme {
                    BunMotion {
                        BunWindowProvider {
                            Box(Modifier.fillMaxSize().background(Ink.Void)) { content(g) }
                        }
                    }
                }
            }
        }
        settle(endless)
        return generation
    }

    private fun settle(endless: Boolean) {
        // The player glows and turns forever: waitForIdle never returns on it.
        if (endless) compose.mainClock.advanceTimeBy(700) else compose.waitForIdle()
    }

    private fun switch(generation: MutableIntState, to: Int, skin: BunSkin, endless: Boolean) {
        Skin.preview(skin)
        generation.intValue = to
        settle(endless)
    }

    private fun writeReport(name: String, body: String) {
        File("/workspace/shots").mkdirs()
        File("/workspace/shots/$name").writeText(body)
        println(body)
    }

    // ------------------------------------------------------- semantics audit

    @Test
    fun everyBunExposesOneAdjustableSeekBar() {
        val report = StringBuilder("SEEK BAR AUDIT — merged semantics tree\n\n")
        val failures = mutableListOf<String>()
        Skin.preview(BunSkin.MIDNIGHT)
        val generation = mount(endless = true) {
            PlayerScreen(state = Sample.playing, onClose = {}, onMore = {})
        }
        for ((i, skin) in BunSkin.entries.withIndex()) {
            if (i > 0) switch(generation, i, skin, endless = true)
            val nodes = compose.onAllNodes(seek).fetchSemanticsNodes()
            val adjustable = nodes.count { it.config.contains(SemanticsActions.SetProgress) }
            report.appendLine("${skin.name.padEnd(9)}  seek controls=${nodes.size}  adjustable=$adjustable")
            if (nodes.size != 1) failures += "$skin: expected 1 seek control, found ${nodes.size}"
            if (adjustable != 1) failures += "$skin: seek control has no SetProgress action for TalkBack"
        }
        report.appendLine()
        report.appendLine(if (failures.isEmpty()) "RESULT: all five buns pass" else "FAILURES:\n" + failures.joinToString("\n"))
        writeReport("a11y-seek-audit.txt", report.toString())
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun everyClickableControlHasSomethingToSay() {
        val report = StringBuilder("CLICKABLE LABEL AUDIT\n\n")
        val unnamed = mutableListOf<String>()
        Skin.preview(BunSkin.MIDNIGHT)
        val generation = mount(endless = true) {
            PlayerScreen(state = Sample.playing, onClose = {}, onMore = {})
        }
        for ((i, skin) in BunSkin.entries.withIndex()) {
            if (i > 0) switch(generation, i, skin, endless = true)
            val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            report.appendLine("${skin.name.padEnd(9)}  clickable controls=${nodes.size}")
            nodes.forEachIndexed { n, node ->
                if (readable(node).isBlank()) {
                    unnamed += "$skin clickable #$n at ${node.boundsInRoot} says nothing"
                }
            }
        }
        report.appendLine()
        report.appendLine(if (unnamed.isEmpty()) "RESULT: every clickable control is named" else "UNNAMED:\n" + unnamed.joinToString("\n"))
        writeReport("a11y-label-audit.txt", report.toString())
        assertTrue(unnamed.joinToString("\n"), unnamed.isEmpty())
    }

    // ---------------------------------------------------------- the three bars

    private enum class Bar { CHARCOAL_WAVE, CLOUD_LINE, NEBULA_AURORA }

    private val DURATION = 240_000L
    private val POSITION = 60_000L

    private fun skinFor(bar: Bar) = when (bar) {
        Bar.CHARCOAL_WAVE -> BunSkin.CHARCOAL
        Bar.CLOUD_LINE -> BunSkin.CLOUD
        Bar.NEBULA_AURORA -> BunSkin.NEBULA
    }

    /** The host carries the test tag so gestures work even before semantics exist. */
    private fun bar(seeks: MutableList<Long>): MutableIntState {
        Skin.preview(BunSkin.CHARCOAL)
        return mount(endless = false) { g ->
            Box(Modifier.fillMaxWidth().testTag("seek-host")) {
                when (Bar.entries[g]) {
                    Bar.CHARCOAL_WAVE -> WaveSeekBar(POSITION, DURATION, { seeks += it })
                    Bar.CLOUD_LINE -> LineSeekBar(POSITION, DURATION, { seeks += it })
                    Bar.NEBULA_AURORA -> AuroraWave(
                        positionMs = POSITION,
                        durationMs = DURATION,
                        playing = false,
                        onSeek = { seeks += it },
                    )
                }
            }
        }
    }

    private fun seekNodeOrNull(): SemanticsNode? =
        compose.onAllNodes(seek).fetchSemanticsNodes().firstOrNull()

    /** Runs the block once per canvas seek bar, keeping the assertions accumulated. */
    private fun forEachBar(block: (Bar, MutableList<Long>) -> Unit): List<String> {
        val seeks = mutableListOf<Long>()
        val generation = bar(seeks)
        val failures = mutableListOf<String>()
        for ((i, b) in Bar.entries.withIndex()) {
            seeks.clear()
            if (i > 0) switch(generation, i, skinFor(b), endless = false)
            val before = failures.size
            try {
                block(b, seeks)
            } catch (t: Throwable) {
                failures += "$b: threw ${t::class.java.simpleName}: ${t.message}"
            }
            if (failures.size == before) continue
        }
        return failures
    }

    @Test
    fun tappingASeekBarGoesToThatFractionOfTheSong() {
        val failures = forEachBar { b, seeks ->
            compose.onNodeWithTag("seek-host").performTouchInput {
                click(percentOffset(0.25f, 0.5f))
            }
            val got = seeks.lastOrNull()
            val expected = (DURATION * 0.25f).toLong()
            if (got == null || kotlin.math.abs(got - expected) > 3_000L) {
                throw AssertionError("$b: tap at 25% sought to $got, expected about $expected")
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun draggingCommitsExactlyOneSeekAtTheFinger() {
        val failures = forEachBar { b, seeks ->
            compose.onNodeWithTag("seek-host").performTouchInput {
                swipe(percentOffset(0.10f, 0.5f), percentOffset(0.75f, 0.5f), 240L)
            }
            val got = seeks.lastOrNull()
            val expected = (DURATION * 0.75f).toLong()
            if (got == null || kotlin.math.abs(got - expected) > 3_000L) {
                throw AssertionError("$b: drag ended at $got, expected about $expected")
            }
            if (seeks.size != 1) {
                throw AssertionError("$b: one drag produced ${seeks.size} seek calls — a seek per frame stutters ExoPlayer")
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun aScreenReaderCanSetProgressDirectly() {
        val failures = forEachBar { b, seeks ->
            val node = seekNodeOrNull()
            if (node == null || !node.config.contains(SemanticsActions.SetProgress)) {
                throw AssertionError("$b: no SetProgress action — TalkBack cannot scrub")
            }
            compose.onNode(seek).performSemanticsAction(SemanticsActions.SetProgress) { it(0.60f) }
            val got = seeks.lastOrNull()
            val expected = (DURATION * 0.60f).toLong()
            if (got == null || kotlin.math.abs(got - expected) > 1_000L) {
                throw AssertionError("$b: SetProgress(0.60) sought to $got, expected $expected")
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun aScreenReaderCanJumpTenSecondsEitherWay() {
        val failures = forEachBar { b, seeks ->
            if (seekNodeOrNull() == null) {
                throw AssertionError("$b: no seek semantics at all")
            }
            compose.onNode(seek).performCustomAccessibilityActionWithLabel("Back 10 seconds")
            compose.onNode(seek).performCustomAccessibilityActionWithLabel("Forward 10 seconds")
            if (seeks.size != 2 || seeks[0] != POSITION - 10_000L || seeks[1] != POSITION + 10_000L) {
                throw AssertionError("$b: rewind/forward produced $seeks, expected [${POSITION - 10_000L}, ${POSITION + 10_000L}]")
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    // ---------------------------------------------------------------- back

    /**
     * The queue sheet is a plain composable over the player, so the framework
     * gives it no back gesture. `PlayerScreen` registers one ordered handler;
     * the harness registers Root's player-closing handler *before* it so the
     * dispatcher order mirrors the real app. First back: sheet. Second: player.
     *
     * Since activity-compose 1.12, `BackHandler` prefers
     * `LocalNavigationEventDispatcherOwner` (ComponentActivity implements it)
     * over the legacy `LocalOnBackPressedDispatcherOwner`, and registers through
     * `LifecycleStartEffect`. Supplying a hand-built dispatcher via only the
     * legacy local therefore registers nothing at all — the earlier harness
     * passed its first two assertions vacuously because no back ever fired.
     * Drive the host's real dispatcher, the one a physical back gesture reaches,
     * and assert the sheet actually disappears so a no-op can never pass again.
     */
    @Test
    fun backClosesTheSheetBeforeThePlayer() {
        var hostDispatcher: OnBackPressedDispatcher? = null
        var rootBacks = 0
        var playerCloses = 0

        Skin.preview(BunSkin.MIDNIGHT)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            hostDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            BunTheme {
                BunMotion {
                    BunWindowProvider {
                        BackHandler { rootBacks++ }
                        Box(Modifier.fillMaxSize().background(Ink.Void)) {
                            PlayerScreen(
                                state = Sample.playing,
                                onClose = { playerCloses++ },
                                onMore = {},
                                openQueueOnStart = true,
                            )
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        val dispatcher = checkNotNull(hostDispatcher) { "the host must provide a back dispatcher" }
        // "Find in queue" is in the sheet's always-composed header. "Up next" is
        // a LazyColumn item further down and may not have been composed yet.
        compose.onNode(hasText("Find in queue")).assertExists()

        dispatcher.onBackPressed()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("the player must not close while a sheet is open", 0, playerCloses)
        assertEquals("Root must not receive the first back", 0, rootBacks)
        // Not just "nothing happened": the sheet really closed on the first back.
        assertTrue(
            "the first back must actually dismiss the queue sheet",
            compose.onAllNodes(hasText("Find in queue")).fetchSemanticsNodes().isEmpty(),
        )

        dispatcher.onBackPressed()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("the second back reaches Root and closes the player", 1, rootBacks)
    }
}
