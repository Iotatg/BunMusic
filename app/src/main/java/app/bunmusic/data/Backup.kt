package app.bunmusic.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Plain-JSON backup of everything the account owns: likes, playlists, play history
 * and the listening counters. Deliberately readable — a backup you cannot inspect
 * is a backup you cannot trust.
 */
object Backup {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; isLenient = true }

    fun export(accountLabel: String): String {
        val liked = Store.likedTracks()
        val playlists = Store.playlists()
        val history = Store.history()

        val root = buildJsonObject {
            put("app", "BunMusic")
            put("format", 1)
            put("exportedAt", System.currentTimeMillis())
            put("account", accountLabel)
            put("stats", buildJsonObject {
                val s = Store.stats()
                put("plays", s.plays)
                put("listeningMs", s.totalMs)
                put("days", s.days)
            })
            put("likes", buildJsonArray { liked.forEach { add(trackJson(it)) } })
            put("playlists", buildJsonArray {
                playlists.forEach { p ->
                    add(buildJsonObject {
                        put("name", p.name)
                        put("createdAt", p.createdAt)
                        put("tracks", buildJsonArray {
                            Store.playlistTracks(p.id).forEach { add(trackJson(it)) }
                        })
                    })
                }
            })
            put("history", buildJsonArray { history.take(120).forEach { add(trackJson(it.track)) } })
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /** Returns a human summary such as "12 likes · 2 playlists". */
    fun import(text: String): Result<String> = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        if (root["app"]?.jsonPrimitive?.contentOrNull != "BunMusic") {
            error("This file was not exported by BunMusic")
        }

        var likes = 0
        var tracks = 0
        var playlists = 0

        root["likes"]?.jsonArray?.forEach { element ->
            val track = trackFrom(element.jsonObject) ?: return@forEach
            Store.remember(track)
            tracks++
            if (!Store.isLiked(track.id)) {
                Store.toggleLike(track.id)
                likes++
            }
        }

        root["playlists"]?.jsonArray?.forEach { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (name.isBlank()) return@forEach
            val existing = Store.playlists().firstOrNull { it.name.equals(name, true) }
                ?: Store.createPlaylist(name)
            obj["tracks"]?.jsonArray?.forEach { t ->
                val track = trackFrom(t.jsonObject) ?: return@forEach
                Store.remember(track)
                Store.addToPlaylist(existing.id, track)
            }
            playlists++
        }

        val summary = buildString {
            append("$likes likes")
            append(" · $playlists playlists")
            append(" · $tracks tracks")
        }
        summary
    }

    private fun trackJson(t: Track): JsonObject = buildJsonObject {
        put("id", t.id)
        put("title", t.title)
        put("artist", t.artist)
        put("album", t.album)
        put("artUrl", t.artUrl ?: "")
        put("artAsset", t.artAsset ?: "")
        put("durationMs", t.durationMs)
        put("source", t.source.name)
        put("videoId", t.videoId)
        put("assetAudio", t.assetAudio ?: "")
    }

    private fun trackFrom(o: JsonObject): Track? {
        val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = o["title"]?.jsonPrimitive?.contentOrNull ?: return null
        return Track(
            id = id,
            title = title,
            artist = o["artist"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            album = o["album"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            artUrl = o["artUrl"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
            artAsset = o["artAsset"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
            durationMs = o["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L,
            source = if (o["source"]?.jsonPrimitive?.contentOrNull == Source.LOCAL.name) Source.LOCAL else Source.YOUTUBE,
            videoId = o["videoId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            assetAudio = o["assetAudio"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
        )
    }

    @Suppress("unused")
    private fun JsonArray.count() = size

    @Suppress("unused")
    private fun JsonObject.int(key: String): Int = this[key]?.jsonPrimitive?.intOrNull ?: 0
}
