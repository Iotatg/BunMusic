package app.bunmusic

import app.bunmusic.data.YtMusic
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pulling the artist's channel id out of a search row.
 *
 * The row's second flex column is the subtitle, and its runs are the only place
 * a track carries the artist's identity. Get this wrong and the player either
 * shows no portrait or, worse, follows an album id as if it were a person.
 */
class ArtistSourceTest {

    private fun row(subtitleRuns: String): JsonObject =
        Json.parseToJsonElement(
            """
            {"flexColumns":[
              {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song"}]}}},
              {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":$subtitleRuns}}}
            ]}
            """.trimIndent(),
        ) as JsonObject

    @Test
    fun `the artist run carries its channel id`() {
        val item = row(
            """
            [
              {"text":"Arijit Singh","navigationEndpoint":{"browseEndpoint":{"browseId":"UCxyz789"}}},
              {"text":" • "},
              {"text":"Album","navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb123"}}}
            ]
            """.trimIndent(),
        )
        assertEquals("UCxyz789", YtMusic.artistBrowseId(item))
    }

    @Test
    fun `an album id is never mistaken for an artist`() {
        val item = row(
            """
            [
              {"text":"Album","navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb123"}}}
            ]
            """.trimIndent(),
        )
        assertNull(YtMusic.artistBrowseId(item))
    }

    @Test
    fun `a row with no subtitle has no artist id`() {
        val item = Json.parseToJsonElement("""{"flexColumns":[]}""") as JsonObject
        assertNull(YtMusic.artistBrowseId(item))
    }

    @Test
    fun `a subtitle without any browse id yields null`() {
        val item = row("""[{"text":"Unknown artist"},{"text":" • "},{"text":"3:42"}]""")
        assertNull(YtMusic.artistBrowseId(item))
    }

    @Test
    fun `the first channel id wins when several appear`() {
        val item = row(
            """
            [
              {"text":"First","navigationEndpoint":{"browseEndpoint":{"browseId":"UCaaa"}}},
              {"text":"Second","navigationEndpoint":{"browseEndpoint":{"browseId":"UCbbb"}}}
            ]
            """.trimIndent(),
        )
        assertEquals("UCaaa", YtMusic.artistBrowseId(item))
    }
}
