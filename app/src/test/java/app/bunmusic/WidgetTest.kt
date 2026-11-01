package app.bunmusic

import app.bunmusic.core.BunSkin
import app.bunmusic.ui.Command
import app.bunmusic.ui.CommandHost
import app.bunmusic.ui.Route
import app.bunmusic.ui.buildCommands
import app.bunmusic.ui.filterCommands
import app.bunmusic.widget.WidgetLook
import app.bunmusic.widget.WidgetShape
import app.bunmusic.widget.WidgetSkin
import app.bunmusic.widget.WidgetText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The widget's arithmetic and configuration, without a launcher to run it in. */
class WidgetTest {

    @Test
    fun everyLookSurvivesThePreferencesString() {
        for (skin in WidgetSkin.entries) {
            for (shape in WidgetShape.entries) {
                val look = WidgetLook(
                    skin = skin,
                    shape = shape,
                    progress = false,
                    times = true,
                    like = false,
                    queue = true,
                )
                assertEquals(look, WidgetLook.decode(look.encode()))
            }
        }
    }

    @Test
    fun aBrokenPreferenceFallsBackToTheDefault() {
        assertEquals(WidgetLook.Default, WidgetLook.decode(null))
        assertEquals(WidgetLook.Default, WidgetLook.decode(""))
        assertEquals(WidgetLook.Default, WidgetLook.decode("nonsense"))
        // A half-written string keeps what it can rather than throwing.
        val partial = WidgetLook.decode("PINK|PILL")
        assertEquals(WidgetSkin.PINK, partial.skin)
        assertEquals(WidgetShape.PILL, partial.shape)
        assertTrue(partial.progress)
    }

    @Test
    fun unknownSkinsAndShapesFallBack() {
        assertEquals(WidgetSkin.AUTO, WidgetSkin.of("NOT_A_SKIN"))
        assertEquals(WidgetShape.ROUND, WidgetShape.of("NOT_A_SHAPE"))
    }

    @Test
    fun everySkinAndShapeHasADrawable() {
        // Resource ids are assigned at build time; a zero here would mean a
        // missing XML file and a transparent widget on a home screen.
        for (skin in WidgetSkin.entries) {
            assertNotEquals(0, WidgetLook(skin = skin).cardRes)
            assertNotEquals(0, WidgetLook(skin = skin).playRes)
        }
        for (shape in WidgetShape.entries) {
            assertNotEquals(0, WidgetLook(shape = shape).cardRes)
        }
    }

    @Test
    fun theClockFormatsLikeAMusicPlayer() {
        assertEquals("0:00", WidgetText.clock(0))
        assertEquals("0:07", WidgetText.clock(7_400))
        assertEquals("1:01", WidgetText.clock(61_000))
        assertEquals("59:59", WidgetText.clock(3_599_000))
        assertEquals("1:01:01", WidgetText.clock(3_661_000))
        // A negative position is a seek in progress, not a reason to crash.
        assertEquals("0:00", WidgetText.clock(-5_000))
    }

    @Test
    fun progressNeverExceedsTheBar() {
        assertEquals(0f, WidgetText.fraction(0, 0), 0.0001f)
        assertEquals(0f, WidgetText.fraction(10, 0), 0.0001f)
        assertEquals(0.5f, WidgetText.fraction(30_000, 60_000), 0.0001f)
        assertEquals(1f, WidgetText.fraction(90_000, 60_000), 0.0001f)
    }

    @Test
    fun autoResolvesToWhateverBunIsWorn() {
        // AUTO is the default, and it must never be null: a widget with no
        // palette would draw invisible text.
        assertTrue(WidgetSkin.AUTO.bun() != null)
        assertEquals(BunSkin.MIDNIGHT, WidgetSkin.MIDNIGHT.bun())
        assertEquals(null, WidgetSkin.GLASS.bun())
    }
}

/** The command catalogue, checked as data. */
class CommandTest {

    private class FakeHost : CommandHost {
        override val context: android.content.Context
            get() = error("commands are not run in this test")
        override fun go(route: Route) = Unit
        override fun openPlayer() = Unit
        override fun openQueue() = Unit
        override fun toast(message: String) = Unit
        override fun close() = Unit
    }

    private val commands: List<Command> by lazy { buildCommands(FakeHost()) }

    @Test
    fun thereAreAtLeastOneHundredCommands() {
        assertEquals("the catalogue size is part of the deal", 133, commands.size)
    }

    @Test
    fun everyCommandIsAddressableAndReadable() {
        val ids = commands.map { it.id }
        assertEquals("ids must be unique", ids.size, ids.toSet().size)
        for (command in commands) {
            assertTrue("blank title on ${command.id}", command.title.isNotBlank())
            assertTrue("blank group on ${command.id}", command.group.isNotBlank())
        }
    }

    @Test
    fun theCatalogueCoversEveryPartOfTheApp() {
        val groups = commands.map { it.group }.toSet()
        listOf("Playback", "Queue", "Library", "Navigate", "Appearance", "Audio", "Data", "Widgets", "System")
            .forEach { assertTrue("missing group $it", groups.contains(it)) }
    }

    @Test
    fun anEmptyQueryKeepsEverythingInOrder() {
        assertEquals(commands, filterCommands(commands, ""))
        assertEquals(commands, filterCommands(commands, "   "))
    }

    @Test
    fun searchMatchesTitleHintGroupAndKeywords() {
        val sleep = filterCommands(commands, "sleep")
        assertTrue(sleep.isNotEmpty())
        assertTrue(sleep.all { it.title.lowercase().contains("sleep") || it.keywords.contains("sleep") || it.hint.contains("sleep") })

        // Group names are searchable, so "queue" finds the queue section even
        // where the title does not repeat the word.
        val queue = filterCommands(commands, "queue")
        assertTrue(queue.any { it.group == "Queue" })
        assertTrue(queue.any { it.id == "queue.open" })
    }

    @Test
    fun searchRanksExactAndPrefixMatchesFirst() {
        val result = filterCommands(commands, "stop")
        assertEquals("play.stop", result.first().id)
    }

    @Test
    fun nonsenseFindsNothing() {
        assertTrue(filterCommands(commands, "zzzzzz").isEmpty())
    }
}
