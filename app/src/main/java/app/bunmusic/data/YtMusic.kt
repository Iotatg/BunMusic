package app.bunmusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * BunTube — a hand-written client for the YouTube Music web API ("InnerTube").
 * Search, the home feed, albums and artists all come from here; playable URLs
 * are resolved separately by [YtStream].
 */
object YtMusic {

    private const val BASE = "https://music.youtube.com/youtubei/v1"
    private const val CLIENT_VERSION = "1.20241009.01.00"
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun context(): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", "WEB_REMIX")
            put("clientVersion", CLIENT_VERSION)
            put("hl", "en")
            put("gl", "US")
            put("userAgent", UA)
        }
        putJsonObject("user") { put("lockedSafetyMode", false) }
        put("contentCheckOk", true)
        put("racyCheckOk", true)
    }

    private suspend fun post(path: String, extra: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("context", context())
            extra.forEach { (k, v) -> put(k, v) }
        }.toString()

        val conn = URL("$BASE/$path?prettyPrint=false").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        conn.setRequestProperty("Origin", "https://music.youtube.com")
        conn.setRequestProperty("Referer", "https://music.youtube.com/")
        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) error("YouTube Music returned HTTP $code")
        json.parseToJsonElement(text) as? JsonObject ?: error("Malformed response")
    }

    // ---------------------------------------------------------------- search

    suspend fun search(query: String): SearchResults {
        if (query.isBlank()) return SearchResults()
        val root = post("search", buildJsonObject { put("query", query.trim()) })

        val songs = LinkedHashMap<String, Track>()
        val albums = LinkedHashMap<String, Collection>()
        val artists = LinkedHashMap<String, Collection>()
        val playlists = LinkedHashMap<String, Collection>()

        for (item in responsiveItems(root)) {
            val type = typeOf(item)
            when {
                type == "song" || type == "video" || type == "episode" -> {
                    toTrack(item)?.let { songs.putIfAbsent(it.videoId, it) }
                }
                type == "artist" || type == "profile" -> {
                    toCollection(item)?.let { artists.putIfAbsent(it.browseId, it) }
                }
                type == "album" || type == "ep" || type == "single" -> {
                    toCollection(item)?.let { albums.putIfAbsent(it.browseId, it) }
                }
                type == "playlist" -> {
                    toCollection(item)?.let { playlists.putIfAbsent(it.browseId, it) }
                }
                else -> {
                    val track = toTrack(item)
                    val col = toCollection(item)
                    when {
                        track != null -> songs.putIfAbsent(track.videoId, track)
                        col?.kind == CollectionKind.ARTIST -> artists.putIfAbsent(col.browseId, col)
                        col?.kind == CollectionKind.ALBUM -> albums.putIfAbsent(col.browseId, col)
                        col != null -> playlists.putIfAbsent(col.browseId, col)
                    }
                }
            }
        }

        return SearchResults(
            songs = songs.values.toList(),
            albums = albums.values.toList(),
            artists = artists.values.toList(),
            playlists = playlists.values.toList(),
        )
    }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = "https://suggestqueries-clients6.youtube.com/complete/search?client=youtube&ds=yt&q=$q&xssi=t"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("User-Agent", UA)
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val start = raw.indexOf('[')
            if (start < 0) return@withContext emptyList()
            val arr = json.parseToJsonElement(raw.substring(start).trim().removeSuffix(")")) as? JsonArray
                ?: return@withContext emptyList()
            arr.getOrNull(1)?.let { it as? JsonArray }?.mapNotNull { e ->
                (e as? JsonArray)?.getOrNull(0)?.let { (it as? JsonPrimitive)?.contentOrNull }
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ------------------------------------------------------------------ home

    suspend fun home(): List<Shelf> {
        val root = post("browse", buildJsonObject { put("browseId", "FEmusic_home") })
        val out = ArrayList<Shelf>()

        val loose = ArrayList<Track>()
        for (item in responsiveItems(root)) {
            if (isInsideCarousel(root, item)) continue
            val t = toTrack(item) ?: continue
            if (loose.none { it.videoId == t.videoId }) loose.add(t)
        }
        if (loose.isNotEmpty()) {
            out.add(Shelf(title = "Quick picks", tracks = loose.take(12)))
        }

        for (c in carousels(root)) {
            val cols = c.second.mapNotNull { toCollection(it) }.distinctBy { it.browseId }
            if (cols.isNotEmpty()) out.add(Shelf(title = c.first, items = cols, wide = true))
        }
        return out
    }

    // --------------------------------------------------------------- details

    suspend fun collection(browseId: String): CollectionDetail {
        val root = post("browse", buildJsonObject { put("browseId", browseId) })

        val head = header(root)
        val title = head?.let { runsText(it["title"] as? JsonObject) } ?: "Unknown"
        val subtitle = head?.let { runsText(it["subtitle"] as? JsonObject) } ?: ""
        val art = head?.let { thumbnails(it).lastOrNull() }
        val desc = head?.let { runsText(it["description"] as? JsonObject) } ?: ""

        val kind = when {
            browseId.startsWith("MPREb") || browseId.startsWith("MPRE") -> CollectionKind.ALBUM
            browseId.startsWith("UC") -> CollectionKind.ARTIST
            else -> CollectionKind.PLAYLIST
        }
        val collection = Collection(
            id = browseId,
            title = title,
            subtitle = subtitle,
            artUrl = art,
            kind = kind,
            browseId = browseId,
            round = kind == CollectionKind.ARTIST,
        )

        val tracks = ArrayList<Track>()
        for (item in responsiveItems(root)) {
            val t = toTrack(item) ?: continue
            if (tracks.none { it.videoId == t.videoId }) tracks.add(t)
        }

        val subShelves = carousels(root).mapNotNull { c ->
            val cols = c.second.mapNotNull { toCollection(it) }.distinctBy { it.browseId }
            if (cols.isEmpty()) null else Shelf(title = c.first, items = cols, wide = true)
        }

        return CollectionDetail(collection, tracks, subShelves, desc)
    }

    // ------------------------------------------------------------- internals

    private fun responsiveItems(root: JsonElement): List<JsonObject> {
        val out = ArrayList<JsonObject>()
        fun walk(e: JsonElement) {
            if (e is JsonObject) {
                val r = e["musicResponsiveListItemRenderer"] as? JsonObject
                if (r != null) {
                    out.add(r)
                    return
                }
                for ((_, v) in e) walk(v)
            } else if (e is JsonArray) {
                for (v in e) walk(v)
            }
        }
        walk(root)
        return out
    }

    private fun carousels(root: JsonElement): List<Pair<String, List<JsonObject>>> {
        val out = ArrayList<Pair<String, List<JsonObject>>>()
        fun walk(e: JsonElement) {
            if (e is JsonObject) {
                val r = e["musicCarouselShelfRenderer"] as? JsonObject
                if (r != null) {
                    val head = r["header"] as? JsonObject
                    val basic = head?.get("musicCarouselShelfBasicHeaderRenderer") as? JsonObject
                    val title = runsText(basic?.get("title") as? JsonObject)
                        ?: runsText(head?.get("title") as? JsonObject)
                        ?: ""
                    val items = ArrayList<JsonObject>()
                    val contents = r["contents"] as? JsonArray ?: JsonArray(emptyList())
                    for (c in contents) {
                        val wrapper = c as? JsonObject ?: continue
                        for ((_, v) in wrapper) {
                            (v as? JsonObject)?.let { items.add(it) }
                        }
                    }
                    out.add(title to items)
                    return
                }
                for ((_, v) in e) walk(v)
            } else if (e is JsonArray) {
                for (v in e) walk(v)
            }
        }
        walk(root)
        return out
    }

    private fun isInsideCarousel(root: JsonElement, item: JsonObject): Boolean {
        // Cheap check: an item that also appears under a carousel is a carousel entry.
        val id = findString(item, "videoId") ?: findString(item, "browseId") ?: return false
        var inside = false
        fun walk(e: JsonElement) {
            if (inside) return
            if (e is JsonObject) {
                val r = e["musicCarouselShelfRenderer"] as? JsonObject
                if (r != null) {
                    if (findString(r, "videoId") == id || findString(r, "browseId") == id) inside = true
                    return
                }
                for ((_, v) in e) walk(v)
            } else if (e is JsonArray) {
                for (v in e) walk(v)
            }
        }
        walk(root)
        return inside
    }

    private fun header(root: JsonElement): JsonObject? {
        val keys = listOf(
            "musicResponsiveHeaderRenderer",
            "musicDetailHeaderRenderer",
            "musicImmersiveHeaderRenderer",
            "musicVisualHeaderRenderer",
        )
        fun walk(e: JsonElement): JsonObject? {
            if (e is JsonObject) {
                for (k in keys) (e[k] as? JsonObject)?.let { return it }
                for ((_, v) in e) walk(v)?.let { return it }
            } else if (e is JsonArray) {
                for (v in e) walk(v)?.let { return it }
            }
            return null
        }
        return walk(root)
    }

    private fun typeOf(item: JsonObject): String {
        val sub = flexTexts(item).getOrNull(1) ?: return ""
        return sub.split("•").firstOrNull()?.trim()?.lowercase() ?: ""
    }

    private fun toTrack(item: JsonObject): Track? {
        val videoId = findString(item, "videoId") ?: return null
        if (videoId.length < 6) return null
        val flex = flexTexts(item)
        val title = flex.getOrNull(0) ?: return null
        val sub = flex.getOrNull(1) ?: ""
        val parts = sub.split("•").map { it.trim() }.filter { it.isNotEmpty() }
        val duration = parseDuration(parts.lastOrNull())
        val body = if (duration > 0) parts.dropLast(1) else parts
        val artist = body.getOrNull(1) ?: "Unknown artist"
        val album = body.getOrNull(2) ?: ""
        return Track(
            id = "yt:$videoId",
            title = title,
            artist = artist,
            album = album,
            artUrl = thumbnails(item).lastOrNull(),
            durationMs = duration,
            source = Source.YOUTUBE,
            videoId = videoId,
        )
    }

    private fun toCollection(item: JsonObject): Collection? {
        val browseId = findString(item, "browseId") ?: return null
        if (browseId.isBlank()) return null
        if (findString(item, "videoId") != null) return null
        val flex = flexTexts(item)
        val title = flex.getOrNull(0) ?: runsText(item["title"] as? JsonObject) ?: return null
        val sub = flex.getOrNull(1) ?: runsText(item["subtitle"] as? JsonObject) ?: ""
        val parts = sub.split("•").map { it.trim() }.filter { it.isNotEmpty() }
        val type = parts.firstOrNull()?.lowercase() ?: ""
        val cleanSub = parts.drop(1).joinToString(" · ")
        val kind = when {
            type == "artist" || type == "profile" -> CollectionKind.ARTIST
            type == "album" || type == "ep" || type == "single" -> CollectionKind.ALBUM
            browseId.startsWith("MPREb") || browseId.startsWith("MPRE") -> CollectionKind.ALBUM
            browseId.startsWith("UC") && type.isEmpty() -> CollectionKind.ARTIST
            else -> CollectionKind.PLAYLIST
        }
        return Collection(
            id = browseId,
            title = title,
            subtitle = cleanSub,
            artUrl = thumbnails(item).lastOrNull(),
            kind = kind,
            browseId = browseId,
            round = kind == CollectionKind.ARTIST,
        )
    }

    private fun flexTexts(o: JsonObject): List<String> {
        val cols = o["flexColumns"] as? JsonArray ?: return emptyList()
        return cols.mapNotNull { c ->
            val r = (c as? JsonObject)?.get("musicResponsiveListItemFlexColumnRenderer") as? JsonObject
            runsText(r?.get("text"))
        }
    }

    private fun runsText(e: JsonElement?): String? {
        val o = e as? JsonObject ?: return null
        (o["simpleText"] as? JsonPrimitive)?.contentOrNull?.let { if (it.isNotBlank()) return it }
        val runs = o["runs"] as? JsonArray ?: return null
        val text = runs.joinToString("") {
            ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull ?: ""
        }
        return text.ifBlank { null }
    }

    private fun thumbnails(o: JsonElement?): List<String> {
        val found = ArrayList<String>()
        fun walk(e: JsonElement) {
            if (e is JsonObject) {
                val arr = e["thumbnails"] as? JsonArray
                if (arr != null) {
                    for (t in arr) {
                        val url = ((t as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull
                        if (url != null) found.add(url)
                    }
                }
                for ((_, v) in e) walk(v)
            } else if (e is JsonArray) for (v in e) walk(v)
        }
        if (o != null) walk(o)
        return found.distinct()
    }

    private fun findString(e: JsonElement?, key: String): String? {
        when (e) {
            is JsonObject -> {
                (e[key] as? JsonPrimitive)?.contentOrNull?.let { return it }
                for ((_, v) in e) findString(v, key)?.let { return it }
            }
            is JsonArray -> for (v in e) findString(v, key)?.let { return it }
            else -> {}
        }
        return null
    }

    private fun parseDuration(raw: String?): Long {
        if (raw == null) return 0L
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(raw.trim()) ?: return 0L
        return (m.groupValues[1].toLong() * 60 + m.groupValues[2].toLong()) * 1000
    }
}
