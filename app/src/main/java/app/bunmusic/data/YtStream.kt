package app.bunmusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * One resolved audio stream.
 *
 * [key] is the cache identity and deliberately excludes the URL: YouTube hands
 * out a freshly signed URL on every resolve, so keying a cache on the URL
 * guarantees a miss the next time the same song is played. `videoId + itag` is
 * the content itself, and that never changes.
 */
data class StreamInfo(
    val videoId: String,
    val url: String,
    val itag: Int,
    val mime: String,
    val bitrate: Long,
    val lengthBytes: Long,
    val resolvedAt: Long = System.currentTimeMillis(),
) {
    val key: String get() = "yt:$videoId:$itag"

    /** Signed URLs die after about six hours; the bytes in the cache do not. */
    val stale: Boolean get() = System.currentTimeMillis() - resolvedAt > 5 * 60 * 60 * 1000L
}

/**
 * Resolves a playable audio URL for a YouTube video id.
 *
 * Two independent strategies so a single block never kills playback:
 *  1. the InnerTube player endpoint with a TV/VR client (plain URLs, exact itag
 *     and content length — everything the cache needs)
 *  2. the NewPipe-style extractor (handles signature deciphering properly)
 */
object YtStream {

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = ConcurrentHashMap<String, Pair<StreamInfo, Long>>()
    private var initialised = false

    fun warmUp() {
        if (initialised) return
        try {
            NewPipe.init(NetDownloader())
            initialised = true
        } catch (_: Throwable) {
        }
    }

    suspend fun audioUrl(videoId: String): String = resolve(videoId).url

    suspend fun resolve(videoId: String, quality: Prefs.Quality = Prefs.Quality.HIGH): StreamInfo =
        withContext(Dispatchers.IO) {
            cache[videoId]?.let { (info, at) ->
                if (System.currentTimeMillis() - at < 25 * 60 * 1000L && !info.stale) return@withContext info
            }
            warmUp()

            var last: Throwable? = null
            try {
                val info = viaInnerTube(videoId, quality)
                cache[videoId] = info to System.currentTimeMillis()
                return@withContext info
            } catch (t: Throwable) {
                last = t
            }
            try {
                val info = viaExtractor(videoId, quality)
                cache[videoId] = info to System.currentTimeMillis()
                return@withContext info
            } catch (t: Throwable) {
                last = t
            }
            throw IllegalStateException("Could not resolve stream: ${last?.message}", last)
        }

    /** Forget a resolved URL — used when a stream 403s because it went stale. */
    fun forget(videoId: String) {
        cache.remove(videoId)
    }

    // ------------------------------------------------------------- selection

    /**
     * Pick a format for the requested quality. Opus at the same bitrate beats
     * AAC, so it gets a small nudge; otherwise this is a straight distance
     * match, which keeps a "Low" listener off a 256 kbps stream on mobile data.
     */
    private fun pick(candidates: List<Pair<JsonObject, Long>>, quality: Prefs.Quality): Pair<JsonObject, Long>? {
        if (candidates.isEmpty()) return null
        val target = quality.bitrate
        return when (quality) {
            Prefs.Quality.HIGH -> candidates.maxByOrNull { (f, br) ->
                br + if (mimeOf(f).contains("opus", true)) 20_000L else 0L
            }
            Prefs.Quality.LOW -> candidates.filter { it.second <= 96_000L }.minByOrNull { it.second }
                ?: candidates.minByOrNull { it.second }
            Prefs.Quality.NORMAL -> candidates.minByOrNull { kotlin.math.abs(it.second - target) }
        }
    }

    private fun mimeOf(f: JsonObject) = (f["mimeType"] as? JsonPrimitive)?.contentOrNull.orEmpty()

    // ---------------------------------------------------------------- clients

    private data class Client(
        val name: String,
        val version: String,
        val extra: Map<String, String> = emptyMap(),
        val userAgent: String,
    )

    private val clients = listOf(
        Client(
            "ANDROID_VR", "1.60.19",
            mapOf(
                "androidSdkVersion" to "32",
                "osName" to "Android",
                "osVersion" to "12",
                "deviceMake" to "Oculus",
                "deviceModel" to "Quest 3",
            ),
            "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12; en_US)",
        ),
        Client(
            "ANDROID_MUSIC", "7.27.52",
            mapOf(
                "androidSdkVersion" to "33",
                "osName" to "Android",
                "osVersion" to "13",
            ),
            "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 13)",
        ),
        Client(
            "IOS", "19.45.4",
            mapOf(
                "deviceMake" to "Apple",
                "deviceModel" to "iPhone16,2",
                "osName" to "iPhone",
                "osVersion" to "18.1.0.22B83",
            ),
            "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
        ),
        Client("MWEB", "2.20241009.01.00", emptyMap(), UA),
    )

    private fun viaInnerTube(videoId: String, quality: Prefs.Quality): StreamInfo {
        var lastError: Throwable? = null
        for (client in clients) {
            try {
                return viaClient(videoId, client, quality)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("no client produced a stream")
    }

    private fun viaClient(videoId: String, client: Client, quality: Prefs.Quality): StreamInfo {
        val payload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", client.name)
                    put("clientVersion", client.version)
                    put("hl", "en")
                    put("gl", "US")
                    put("userAgent", client.userAgent)
                    client.extra.forEach { (k, v) -> put(k, v) }
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }.toString()

        val conn = URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", client.userAgent)
        conn.setRequestProperty("X-Goog-Api-Format-Version", "1")
        conn.outputStream.use { it.write(payload.toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) error("${client.name}: HTTP $code")

        val root = json.parseToJsonElement(text) as? JsonObject ?: error("${client.name}: bad response")
        val status = (root["playabilityStatus"] as? JsonObject)
            ?.get("status")?.let { (it as? JsonPrimitive)?.contentOrNull }
        if (status != null && status != "OK") {
            val reason = (root["playabilityStatus"] as? JsonObject)
                ?.get("reason")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: status
            error("${client.name}: $reason")
        }
        val formats = (root["streamingData"] as? JsonObject)?.get("adaptiveFormats") as? JsonArray
            ?: error("${client.name}: no adaptive formats")

        val candidates = formats.mapNotNull { it as? JsonObject }
            .filter { f ->
                val mime = mimeOf(f)
                mime.startsWith("audio") && (f["url"] as? JsonPrimitive)?.contentOrNull != null
            }
            .map { f -> f to ((f["bitrate"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L) }

        val chosen = pick(candidates, quality) ?: error("${client.name}: no audio format")
        val f = chosen.first
        return StreamInfo(
            videoId = videoId,
            url = (f["url"] as? JsonPrimitive)?.contentOrNull ?: error("${client.name}: empty url"),
            itag = (f["itag"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0,
            mime = mimeOf(f).substringBefore(";"),
            bitrate = chosen.second,
            lengthBytes = (f["contentLength"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: -1L,
        )
    }

    private fun viaExtractor(videoId: String, quality: Prefs.Quality): StreamInfo {
        val service = NewPipe.getService(0)
        val link = YoutubeStreamLinkHandlerFactory.getInstance().fromId(videoId)
        val extractor = YoutubeStreamExtractor(service, link)
        extractor.fetchPage()
        val audios = extractor.audioStreams.filter { it.content?.isNotBlank() == true }
        if (audios.isEmpty()) error("no audio streams")
        val target = quality.bitrate
        val best = when (quality) {
            Prefs.Quality.HIGH -> audios.maxByOrNull { it.averageBitrate }
            Prefs.Quality.LOW -> audios.minByOrNull { it.averageBitrate }
            Prefs.Quality.NORMAL -> audios.minByOrNull { kotlin.math.abs(it.averageBitrate - target) }
        } ?: error("no audio streams")
        return StreamInfo(
            videoId = videoId,
            url = best.content ?: error("empty stream url"),
            itag = best.itagItem?.id ?: 0,
            mime = best.format?.mimeType ?: "audio/mp4",
            bitrate = best.averageBitrate.toLong(),
            lengthBytes = -1L,
        )
    }

    private class NetDownloader : Downloader() {
        override fun execute(request: Request): Response {
            val conn = URL(request.url()).openConnection() as HttpURLConnection
            conn.requestMethod = request.httpMethod()
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            request.headers().forEach { (k, v) ->
                if (!k.equals("user-agent", true) && !k.equals("content-length", true)) {
                    conn.setRequestProperty(k, v.joinToString(","))
                }
            }
            request.dataToSend()?.let { data ->
                conn.doOutput = true
                conn.outputStream.use { it.write(data) }
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            val headers = conn.headerFields
                .filterKeys { it != null }
                .mapValues { entry -> entry.value ?: emptyList() }
            val message = conn.responseMessage ?: ""
            conn.disconnect()
            return Response(code, message, headers, body, request.url())
        }
    }
}
