package app.bunmusic

import app.bunmusic.core.CrashGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard's pure half.
 *
 * The handler itself cannot be tested here — it needs a process to kill — but
 * everything that decides *what happens next* is arithmetic on a list of
 * timestamps and strings, and those are the parts that go wrong silently.
 */
class CrashGuardTest {

    @Test
    fun `one crash is bad luck and gets a restart`() {
        assertTrue(CrashGuard.shouldRestart(listOf(1_000L), now = 2_000L))
    }

    @Test
    fun `two crashes inside the window stop the restart`() {
        val now = 100_000L
        assertFalse(CrashGuard.shouldRestart(listOf(now - 1_000, now - 2_000), now = now))
    }

    @Test
    fun `two crashes far apart are still bad luck`() {
        val now = 500_000L
        assertTrue(CrashGuard.shouldRestart(listOf(now - 200_000, now), now = now))
    }

    @Test
    fun `a timestamp in the future is ignored rather than counted`() {
        val now = 10_000L
        assertTrue(CrashGuard.shouldRestart(listOf(now + 5_000), now = now))
    }

    @Test
    fun `an entry survives the round trip through disk`() {
        val entry = CrashGuard.summarise(
            IllegalStateException("boom"),
            thread = "main",
            where = "uncaught · screen:Library",
            at = 1_700_000_000_000L,
        )
        val parsed = CrashGuard.parse(CrashGuard.format(entry))
        assertNotNull(parsed)
        assertEquals(entry.at, parsed!!.at)
        assertEquals("main", parsed.thread)
        assertEquals("uncaught · screen:Library", parsed.where)
        assertTrue(parsed.message.contains("boom"))
        assertTrue(parsed.stack.contains("CrashGuardTest"))
    }

    @Test
    fun `newlines in a stack trace do not break the line format`() {
        val entry = CrashGuard.summarise(RuntimeException("multi\nline"), "main", "test", 5L)
        val line = CrashGuard.format(entry)
        assertFalse(line.contains('\n'))
        assertEquals(entry.stack, CrashGuard.parse(line)!!.stack)
    }

    @Test
    fun `a corrupt line is skipped, not fatal`() {
        assertEquals(null, CrashGuard.parse(""))
        assertEquals(null, CrashGuard.parse("not a crash"))
        assertEquals(null, CrashGuard.parse("123\u0001only-two-fields"))
    }

    @Test
    fun `the log keeps the newest entries`() {
        val lines = (1..30).map { "line $it" }
        val kept = CrashGuard.trim(lines, 24)
        assertEquals(24, kept.size)
        assertEquals("line 30", kept.last())
        assertEquals("line 7", kept.first())
    }

    @Test
    fun `a short log is left alone`() {
        val lines = listOf("a", "b")
        assertEquals(lines, CrashGuard.trim(lines, 24))
    }

    @Test
    fun `the headline names the failure and where it happened`() {
        val entry = CrashGuard.summarise(NullPointerException("null album"), "main", "screen:Player", 0L)
        assertTrue(entry.headline.contains("NullPointerException"))
        assertTrue(entry.headline.contains("screen:Player"))
    }

    @Test
    fun `a throwable with no message still gets a name`() {
        val entry = CrashGuard.summarise(RuntimeException(), "main", "test", 0L)
        assertEquals("RuntimeException", entry.message)
    }

    @Test
    fun `an enormous stack is trimmed`() {
        val deep = generateSequence(1) { it + 1 }.take(400).fold(Throwable("deep")) { acc, _ ->
            RuntimeException(acc)
        }
        val entry = CrashGuard.summarise(deep, "main", "test", 0L, maxStack = 400)
        assertTrue(entry.stack.length <= 410)
        assertTrue(entry.stack.endsWith("…"))
    }
}
