package app.bunmusic

import app.bunmusic.data.Backup
import app.bunmusic.data.Listening
import app.bunmusic.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A18: export() used to write `stats` and `history` that import() never read,
 * so restoring a backup silently erased play history and every counter.
 *
 * These exercise the pure half of the backup (serialize / parse / merge); the
 * Store boundary is deliberately thin so the data contract is provable here.
 */
class BackupRoundTripTest {

    private fun track(id: String) = Track(
        id = id, title = "T-$id", artist = "A-$id",
        durationMs = 180_000L, videoId = "v-$id",
    )

    // ------------------------------------------------------------- round trip

    @Test
    fun `serialize then parse keeps history timestamps and every counter`() {
        val snapshot = Backup.Snapshot(
            account = "me",
            exportedAt = 1_234L,
            stats = Backup.Stats(
                plays = 42,
                listeningMs = 7_000_000L,
                dayKeys = setOf("19000", "19001"),
                artists = mapOf("A" to 7),
                tracks = mapOf("X" to 9),
                playIds = mapOf("a" to 5),
                events = listOf(Listening.Event("a", 1_700_000_000_000L, 180_000L)),
            ),
            likes = listOf(track("a")),
            playlists = listOf(Backup.PlaylistBackup("Road", 1L, listOf(track("a"), track("b")))),
            history = listOf(
                Backup.HistoryEntry(track("a"), 1_000L),
                Backup.HistoryEntry(track("b"), 2_000L),
            ),
        )

        val parsed = Backup.parseSnapshot(Backup.serialize(snapshot))
        assertEquals(42, parsed.stats.plays)
        assertEquals(7_000_000L, parsed.stats.listeningMs)
        assertEquals(setOf("19000", "19001"), parsed.stats.dayKeys)
        assertEquals(2, parsed.stats.days)
        assertEquals(7, parsed.stats.artists["A"])
        assertEquals(9, parsed.stats.tracks["X"])
        assertEquals(5, parsed.stats.playIds["a"])
        assertEquals(1, parsed.stats.events.size)
        assertEquals("a", parsed.stats.events.single().trackId)

        assertEquals(listOf("a"), parsed.likes.map { it.id })
        assertEquals("v-a", parsed.likes.single().videoId)
        assertEquals(180_000L, parsed.likes.single().durationMs)
        assertEquals("Road", parsed.playlists.single().name)
        assertEquals(1L, parsed.playlists.single().createdAt)
        assertEquals(listOf("a", "b"), parsed.playlists.single().tracks.map { it.id })

        // The timestamps are the whole point: without them history() cannot
        // rebuild the recent list or the week chart in its original order.
        assertEquals(listOf("a", "b"), parsed.history.map { it.track.id })
        assertEquals(listOf(1_000L, 2_000L), parsed.history.map { it.atMs })
    }

    // --------------------------------------------------------- old format

    @Test
    fun `a format 1 backup is still readable and loses nothing it stored`() {
        val old = """{
            "app":"BunMusic","format":1,"exportedAt":10,"account":"me",
            "stats":{"plays":12,"listeningMs":60000,"days":3},
            "likes":[{"id":"a","title":"A","artist":"X"}],
            "playlists":[{"name":"Road","createdAt":5,"tracks":[{"id":"a","title":"A","artist":"X"}]}],
            "history":[{"id":"a","title":"A","artist":"X"}]
        }"""
        val parsed = Backup.parseSnapshot(old)
        assertEquals(12, parsed.stats.plays)
        assertEquals(60_000L, parsed.stats.listeningMs)
        assertEquals(1, parsed.likes.size)
        assertEquals(1, parsed.playlists.size)
        assertEquals(1, parsed.history.size)
        assertEquals("a", parsed.history.single().track.id)
        // Legacy rows carry no timestamp; they must still restore, not be dropped.
        assertEquals(0L, parsed.history.single().atMs)
    }

    // ------------------------------------------------------------- merge

    @Test
    fun `importing a backup never lowers a newer counter and is idempotent`() {
        val older = Backup.Stats(
            plays = 10, listeningMs = 1_000L, dayKeys = setOf("1"),
            artists = mapOf("A" to 2),
        )
        val newer = Backup.Stats(
            plays = 25, listeningMs = 900L, dayKeys = setOf("1", "2"),
            artists = mapOf("A" to 5, "B" to 1),
        )
        val merged = Backup.mergeStats(newer, older)
        assertEquals(25, merged.plays)
        assertEquals(1_000L, merged.listeningMs)
        assertEquals(setOf("1", "2"), merged.dayKeys)
        assertEquals(5, merged.artists["A"])
        assertEquals(1, merged.artists["B"])
        // Importing the same file a second time changes nothing.
        assertEquals(merged, Backup.mergeStats(merged, older))
    }

    @Test
    fun `mergeHistory keeps the newer play, dedups and caps at 120`() {
        val current = listOf(Backup.HistoryEntry(track("a"), 100L))
        val incoming = listOf(
            Backup.HistoryEntry(track("a"), 50L),
            Backup.HistoryEntry(track("b"), 900L),
        )
        val merged = Backup.mergeHistory(current, incoming)
        assertEquals(2, merged.size)
        assertEquals("b", merged[0].track.id) // newest first
        assertEquals(100L, merged[1].atMs)   // newer of the two "a" rows

        val many = (1..300).map { Backup.HistoryEntry(track("t$it"), it.toLong()) }
        assertEquals(120, Backup.mergeHistory(emptyList(), many).size)
    }

    @Test
    fun `mergeEvents de-duplicates and caps at the log cap`() {
        val events = (1..Listening.CAP + 50).map { Listening.Event("t$it", it.toLong(), 1_000L) }
        val merged = Backup.mergeEvents(events, events)
        assertEquals(Listening.CAP, merged.size)
        assertEquals(Listening.CAP + 50L, merged.first().atMs)
    }

    // ------------------------------------------------------------ hostile input

    @Test
    fun `a hostile or truncated file is bounded and never throws`() {
        val junk = """{
            "app":"BunMusic",
            "stats":{"plays":-5,"listeningMs":"oops",
                     "artists":{"A":"nan","B":3},
                     "events":["bad","a\u00011700000000000\u0001180000"]},
            "likes":[1,"x",null,{"id":"a","title":"A"}],
            "playlists":[{"name":"   "}],
            "history":[{"track":{}},{"atMs":"bad"}]
        }"""
        val parsed = Backup.parseSnapshot(junk)
        assertEquals(0, parsed.stats.plays)
        assertEquals(0L, parsed.stats.listeningMs)
        assertEquals(3, parsed.stats.artists["B"])
        assertFalse(parsed.stats.artists.containsKey("A"))
        assertEquals(1, parsed.likes.size)
        assertTrue(parsed.playlists.isEmpty())
        assertTrue(parsed.history.isEmpty())
    }

    @Test
    fun `a file from another app is refused`() {
        val result = runCatching { Backup.parseSnapshot("""{"app":"NotBunMusic"}""") }
        assertTrue(result.isFailure)
    }
}
