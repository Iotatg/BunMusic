package app.bunmusic.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Plain-JSON backup of everything the account owns: likes, playlists, play
 * history and the listening counters. Deliberately readable — a backup you
 * cannot inspect is a backup you cannot trust.
 *
 * The file is built and read through pure functions ([serialize],
 * [parseSnapshot], [mergeStats], [mergeHistory]) so the round trip is testable
 * without a device. Only [export] and [import] touch [Store].
 */
object Backup {

    /** Hard ceilings so a hostile file cannot allocate unbounded lists. */
    private const val MAX_LIKES = 10_000
    private const val MAX_PLAYLISTS = 1_000
    private const val MAX_TRACKS_PER_PLAYLIST = 5_000
    private const val MAX_HISTORY = 120
    private const val MAX_COUNTER_ENTRIES = 10_000

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; isLenient = true }

    data class Stats(
        val plays: Int = 0,
        val listeningMs: Long = 0L,
        /** Epoch-day strings backing [Store.stats].days. */
        val dayKeys: Set<String> = emptySet(),
        val artists: Map<String, Int> = emptyMap(),
        val tracks: Map<String, Int> = emptyMap(),
        val playIds: Map<String, Int> = emptyMap(),
        val events: List<Listening.Event> = emptyList(),
    ) {
        val days: Int get() = dayKeys.size
    }

    data class HistoryEntry(val track: Track, val atMs: Long)

    data class PlaylistBackup(val name: String, val createdAt: Long, val tracks: List<Track>)

    data class Snapshot(
        val app: String = "BunMusic",
        val format: Int = 2,
        val exportedAt: Long = 0L,
        val account: String = "guest",
        val stats: Stats = Stats(),
        val likes: List<Track> = emptyList(),
        val playlists: List<PlaylistBackup> = emptyList(),
        val history: List<HistoryEntry> = emptyList(),
    )

    // ------------------------------------------------------------- merge (pure)

    /**
     * Import is a merge, not a replace: a backup taken yesterday must never
     * lower a counter that has grown since. Taking the max per key avoids the
     * double-counting a sum would introduce when the same backup is imported twice.
     */
    fun mergeStats(current: Stats, incoming: Stats): Stats = Stats(
        plays = maxOf(current.plays, incoming.plays),
        listeningMs = maxOf(current.listeningMs, incoming.listeningMs),
        dayKeys = current.dayKeys + incoming.dayKeys,
        artists = mergeCounts(current.artists, incoming.artists),
        tracks = mergeCounts(current.tracks, incoming.tracks),
        playIds = mergeCounts(current.playIds, incoming.playIds),
        events = mergeEvents(current.events, incoming.events),
    )

    private fun mergeCounts(a: Map<String, Int>, b: Map<String, Int>): Map<String, Int> {
        val out = LinkedHashMap(a)
        for ((key, value) in b) out[key] = maxOf(out[key] ?: 0, value)
        return out
    }

    /** Newest first, de-duplicated by the whole event, capped at the log cap. */
    fun mergeEvents(a: List<Listening.Event>, b: List<Listening.Event>): List<Listening.Event> {
        val seen = HashSet<String>()
        return (a + b)
            .filter { seen.add("${it.trackId}\u0001${it.atMs}\u0001${it.ms}") }
            .sortedByDescending { it.atMs }
            .take(Listening.CAP)
    }

    /** One row per track, keeping the more recent play, newest first. */
    fun mergeHistory(current: List<HistoryEntry>, incoming: List<HistoryEntry>): List<HistoryEntry> {
        val best = LinkedHashMap<String, HistoryEntry>()
        for (e in current + incoming) {
            val prev = best[e.track.id]
            if (prev == null || e.atMs > prev.atMs) best[e.track.id] = e
        }
        return best.values.sortedByDescending { it.atMs }.take(MAX_HISTORY)
    }

    // -------------------------------------------------------- serialize (pure)

    private fun counterJson(map: Map<String, Int>) = buildJsonObject {
        for ((key, value) in map.entries.take(MAX_COUNTER_ENTRIES)) put(key, value)
    }

    fun serialize(s: Snapshot): String {
        val root = buildJsonObject {
            put("app", s.app)
            put("format", s.format)
            put("exportedAt", s.exportedAt)
            put("account", s.account)
            put("stats", buildJsonObject {
                put("plays", s.stats.plays)
                put("listeningMs", s.stats.listeningMs)
                put("days", s.stats.days) // legacy aggregate, kept for old readers
                put("dayKeys", buildJsonArray {
                    s.stats.dayKeys.sorted().forEach { add(JsonPrimitive(it)) }
                })
                put("artists", counterJson(s.stats.artists))
                put("tracks", counterJson(s.stats.tracks))
                put("playIds", counterJson(s.stats.playIds))
                put("events", buildJsonArray {
                    s.stats.events.take(Listening.CAP).forEach { add(JsonPrimitive(Listening.encode(it))) }
                })
            })
            put("likes", buildJsonArray {
                s.likes.take(MAX_LIKES).forEach { add(trackJson(it)) }
            })
            put("playlists", buildJsonArray {
                s.playlists.take(MAX_PLAYLISTS).forEach { p ->
                    add(buildJsonObject {
                        put("name", p.name)
                        put("createdAt", p.createdAt)
                        put("tracks", buildJsonArray {
                            p.tracks.take(MAX_TRACKS_PER_PLAYLIST).forEach { add(trackJson(it)) }
                        })
                    })
                }
            })
            put("history", buildJsonArray {
                s.history.take(MAX_HISTORY).forEach { h ->
                    add(buildJsonObject {
                        put("atMs", h.atMs)
                        put("track", trackJson(h.track))
                    })
                }
            })
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    // ----------------------------------------------------------- parse (pure)

    private fun objOf(el: JsonElement?): JsonObject? = el as? JsonObject
    private fun arrayOf(el: JsonElement?): JsonArray? = el as? JsonArray
    private fun primitive(el: JsonElement?): JsonPrimitive? = el as? JsonPrimitive

    private fun parseCounter(el: JsonElement?): Map<String, Int> {
        val obj = objOf(el) ?: return emptyMap()
        val out = LinkedHashMap<String, Int>()
        for ((key, value) in obj.entries.take(MAX_COUNTER_ENTRIES)) {
            val n = primitive(value)?.intOrNull ?: continue
            out[key] = n.coerceAtLeast(0)
        }
        return out
    }

    /**
     * Reads both the current format and format 1 backups (which carried only the
     * aggregate stats and a bare track array under `history`). Every field is
     * optional and type-checked, so a truncated or hand-edited file yields an
     * empty value instead of an exception.
     */
    fun parseSnapshot(text: String): Snapshot {
        val root = json.parseToJsonElement(text).jsonObject
        if (primitive(root["app"])?.contentOrNull != "BunMusic") {
            error("This file was not exported by BunMusic")
        }
        val st = objOf(root["stats"])
        val stats = Stats(
            plays = (primitive(st?.get("plays"))?.intOrNull ?: 0).coerceAtLeast(0),
            listeningMs = (primitive(st?.get("listeningMs"))?.longOrNull ?: 0L).coerceAtLeast(0L),
            dayKeys = arrayOf(st?.get("dayKeys"))
                ?.mapNotNull { primitive(it)?.contentOrNull }
                ?.filter { it.toLongOrNull() != null }
                ?.toSet() ?: emptySet(),
            artists = parseCounter(st?.get("artists")),
            tracks = parseCounter(st?.get("tracks")),
            playIds = parseCounter(st?.get("playIds")),
            events = arrayOf(st?.get("events"))?.take(Listening.CAP)
                ?.mapNotNull { primitive(it)?.contentOrNull?.let(Listening::decode) } ?: emptyList(),
        )
        val likes = arrayOf(root["likes"])?.take(MAX_LIKES)
            ?.mapNotNull { objOf(it)?.let(::trackFrom) } ?: emptyList()
        val playlists = arrayOf(root["playlists"])?.take(MAX_PLAYLISTS)?.mapNotNull { el ->
            val o = objOf(el) ?: return@mapNotNull null
            val name = primitive(o["name"])?.contentOrNull?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            PlaylistBackup(
                name = name,
                createdAt = primitive(o["createdAt"])?.longOrNull ?: 0L,
                tracks = arrayOf(o["tracks"])?.take(MAX_TRACKS_PER_PLAYLIST)
                    ?.mapNotNull { objOf(it)?.let(::trackFrom) } ?: emptyList(),
            )
        } ?: emptyList()
        val history = arrayOf(root["history"])?.take(MAX_HISTORY)?.mapNotNull { el ->
            val o = objOf(el) ?: return@mapNotNull null
            // New format wraps the track under "track"; format 1 stored it bare.
            val track = trackFrom(objOf(o["track"]) ?: o) ?: return@mapNotNull null
            HistoryEntry(track, primitive(o["atMs"])?.longOrNull ?: 0L)
        } ?: emptyList()
        return Snapshot(
            format = primitive(root["format"])?.intOrNull ?: 1,
            exportedAt = primitive(root["exportedAt"])?.longOrNull ?: 0L,
            account = primitive(root["account"])?.contentOrNull.orEmpty(),
            stats = stats,
            likes = likes,
            playlists = playlists,
            history = history,
        )
    }

    // --------------------------------------------------------- store boundary

    fun export(accountLabel: String): String = serialize(
        Snapshot(
            exportedAt = System.currentTimeMillis(),
            account = accountLabel,
            stats = Store.exportStats(),
            likes = Store.likedTracks(),
            playlists = Store.playlists().map { p ->
                PlaylistBackup(p.name, p.createdAt, Store.playlistTracks(p.id))
            },
            history = Store.exportHistory(),
        ),
    )

    /** Returns a human summary such as "12 likes · 2 playlists". */
    fun import(text: String): Result<String> = runCatching {
        val snap = parseSnapshot(text)

        var likes = 0
        var tracks = 0
        var playlists = 0

        for (track in snap.likes) {
            Store.remember(track)
            tracks++
            if (!Store.isLiked(track.id)) {
                Store.toggleLike(track.id)
                likes++
            }
        }

        for (p in snap.playlists) {
            val existing = Store.playlists().firstOrNull { it.name.equals(p.name, true) }
                ?: Store.createPlaylist(p.name)
            for (track in p.tracks) {
                Store.remember(track)
                Store.addToPlaylist(existing.id, track)
            }
            playlists++
        }

        // Format 1 wrote `stats` and `history` but import never read them, so
        // restoring a backup silently erased play history and every counter.
        Store.restoreStats(snap.stats)
        Store.restoreHistory(snap.history)

        buildString {
            append("$likes likes")
            append(" · $playlists playlists")
            append(" · $tracks tracks")
            if (snap.history.isNotEmpty()) append(" · ${snap.history.size} recent")
        }
    }

    // --------------------------------------------------------------- track io

    internal fun trackJson(t: Track): JsonObject = buildJsonObject {
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

    internal fun trackFrom(o: JsonObject): Track? {
        val id = primitive(o["id"])?.contentOrNull ?: return null
        val title = primitive(o["title"])?.contentOrNull ?: return null
        return Track(
            id = id,
            title = title,
            artist = primitive(o["artist"])?.contentOrNull.orEmpty(),
            album = primitive(o["album"])?.contentOrNull.orEmpty(),
            artUrl = primitive(o["artUrl"])?.contentOrNull?.ifBlank { null },
            artAsset = primitive(o["artAsset"])?.contentOrNull?.ifBlank { null },
            durationMs = (primitive(o["durationMs"])?.longOrNull ?: 0L).coerceAtLeast(0L),
            source = if (primitive(o["source"])?.contentOrNull == Source.LOCAL.name) Source.LOCAL else Source.YOUTUBE,
            videoId = primitive(o["videoId"])?.contentOrNull.orEmpty(),
            assetAudio = primitive(o["assetAudio"])?.contentOrNull?.ifBlank { null },
        )
    }
}
