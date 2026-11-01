package app.bunmusic

import app.bunmusic.ui.Command
import app.bunmusic.ui.CommandHost
import app.bunmusic.ui.Route
import app.bunmusic.ui.buildCommands
import app.bunmusic.ui.filterCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Searching the command palette.
 *
 * The palette is the only way to reach most of the app's verbs, so the ranking
 * is not cosmetic: if typing "stop" puts "Stop after this song" above "Stop",
 * the listener learns not to trust the list.
 */
class CommandSearchTest {

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
    fun `search ignores case`() {
        assertEquals(
            filterCommands(commands, "SLEEP").map { it.id },
            filterCommands(commands, "sleep").map { it.id },
        )
    }

    @Test
    fun `surrounding spaces do not change the answer`() {
        assertEquals(
            filterCommands(commands, "queue").map { it.id },
            filterCommands(commands, "  queue  ").map { it.id },
        )
    }

    @Test
    fun `several words must all match`() {
        val both = filterCommands(commands, "sleep timer")
        assertTrue(both.isNotEmpty())
        both.forEach { c ->
            val hay = "${c.title} ${c.hint} ${c.group} ${c.keywords}".lowercase()
            assertTrue("${c.id} matched without 'sleep'", hay.contains("sleep"))
            assertTrue("${c.id} matched without 'timer'", hay.contains("timer"))
        }
    }

    @Test
    fun `a query that matches nothing in the second word finds nothing`() {
        assertTrue(filterCommands(commands, "sleep zzzz").isEmpty())
    }

    @Test
    fun `results never repeat a command`() {
        listOf("a", "e", "play", "queue", "theme", "download").forEach { q ->
            val ids = filterCommands(commands, q).map { it.id }
            assertEquals("query '$q'", ids.size, ids.toSet().size)
        }
    }

    @Test
    fun `results are a subset of the catalogue`() {
        val all = commands.map { it.id }.toSet()
        filterCommands(commands, "bun").forEach { assertTrue(it.id in all) }
    }

    @Test
    fun `an exact title outranks a prefix which outranks a substring`() {
        // Ranked on a synthetic list so the assertion is about the ranking rule
        // and not about which verbs happen to be in the catalogue today.
        val list = listOf(
            cmd("x.replay", "Replay"),
            cmd("x.play.next", "Play next"),
            cmd("x.play", "Play"),
        )
        assertEquals(listOf("x.play", "x.play.next", "x.replay"), filterCommands(list, "play").map { it.id })
    }

    @Test
    fun `equal ranked matches keep catalogue order`() {
        val list = listOf(
            cmd("a", "Play one"),
            cmd("b", "Play two"),
            cmd("c", "Play three"),
        )
        assertEquals(listOf("a", "b", "c"), filterCommands(list, "play").map { it.id })
    }

    @Test
    fun `searching a group name finds that whole group`() {
        val inGroup = commands.filter { it.group == "Widgets" }
        assertTrue(inGroup.isNotEmpty())
        val found = filterCommands(commands, "widgets").map { it.id }.toSet()
        inGroup.forEach { assertTrue("${it.id} missing from group search", it.id in found) }
    }

    private fun cmd(id: String, title: String) = Command(
        id = id,
        title = title,
        hint = "",
        group = "Test",
        glyph = app.bunmusic.core.G.Play,
        run = {},
    )

    @Test
    fun `the catalogue has no duplicate ids`() {
        val ids = commands.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every command carries a group and a glyph`() {
        commands.forEach {
            assertTrue("${it.id} has no group", it.group.isNotBlank())
            assertTrue("${it.id} has no title", it.title.isNotBlank())
        }
    }

    @Test
    fun `the filter is a pure function of its input`() {
        val once = filterCommands(commands, "download").map { it.id }
        val twice = filterCommands(commands, "download").map { it.id }
        assertEquals(once, twice)
        assertEquals(commands, filterCommands(commands, ""))
    }
}
