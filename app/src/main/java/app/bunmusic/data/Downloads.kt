package app.bunmusic.data

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Offline downloads.
 *
 * This used to be one suspend function: resolve a URL, stream it to a file,
 * return. That was enough to make a track playable on a plane, and it was
 * invisible while it happened — a fifty-track album spawned fifty silent
 * downloads with no progress, no way to stop one, and no record of a failure
 * beyond a toast that had already faded.
 *
 * It is a queue now. Jobs move QUEUED -> RUNNING -> gone, or to FAILED with a
 * reason; two run at a time; the whole thing is cancellable per track and
 * resumable after the process dies.
 *
 * What it deliberately is *not*: a foreground service. A download that needs to
 * survive the app being swiped away would need one, and that is a notification
 * the listener did not ask for. Leaving the app cancels nothing, but the
 * process going away does — the queue is written to disk so it can be picked up
 * again, not kept alive.
 */
object Downloads {

    /** A finished file on disk. */
    data class Entry(
        val trackId: String,
        val title: String,
        val artist: String,
        val artUrl: String?,
        val path: String,
        val bytes: Long,
        val atMs: Long,
    )

    enum class State {
        /** Waiting for a worker. */
        QUEUED,

        /** Bytes are moving. */
        RUNNING,

        /** Held back because the Wi-Fi-only setting is on and this is mobile data. */
        WAITING,

        /** Gave up. [Job.error] says why. */
        FAILED,
    }

    data class Job(
        val track: Track,
        val state: State = State.QUEUED,
        val doneBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val error: String? = null,
    ) {
        /** 0..1, or 0 when the server did not send a length. */
        val fraction: Float
            get() = if (totalBytes > 0) (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

        /** True while running without a known size — the bar should spin, not sit at 0. */
        val indeterminate: Boolean get() = state == State.RUNNING && totalBytes <= 0L

        val active: Boolean get() = state == State.QUEUED || state == State.RUNNING
    }

    private const val F = '\u0001'
    private const val R = '\n'

    /** Two at a time: enough to keep a socket busy, few enough not to starve playback. */
    private const val MAX_RUNNING = 2

    /** How often the dispatcher looks for work when the queue is empty. */
    private const val IDLE_POLL_MS = 700L

    private lateinit var dir: File
    private lateinit var sp: SharedPreferences
    private lateinit var appContext: Context

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    /** Insertion-ordered so the queue reads in the order it was asked for. */
    private val jobs = LinkedHashMap<String, Job>()
    private val handles = HashMap<String, CoroutineJob>()
    private var dispatcher: CoroutineJob? = null

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _queue = MutableStateFlow<List<Job>>(emptyList())

    /** Everything not yet finished: queued, running, held back or failed. */
    val queue: StateFlow<List<Job>> = _queue.asStateFlow()

    private val _speed = MutableStateFlow(0L)

    /** Bytes per second across all running jobs, smoothed over half a second. */
    val speed: StateFlow<Long> = _speed.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    /** Kept for callers that only want "how far is this one". */
    val progress: StateFlow<Map<String, Float>>
        get() = _progress
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    fun init(context: Context) {
        if (::dir.isInitialized) return
        appContext = context.applicationContext
        dir = File(context.filesDir, "downloads").apply { mkdirs() }
        sp = context.getSharedPreferences("bunmusic_downloads", Context.MODE_PRIVATE)
        _entries.value = read()
        // A .part with no job behind it is a download that died with the process.
        runCatching { dir.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() } }
        restoreQueue()
    }

    // ------------------------------------------------------------------ store

    private fun read(): List<Entry> {
        val out = ArrayList<Entry>()
        for (line in (sp.getString("entries", null) ?: "").split(R)) {
            val p = line.split(F)
            if (p.size < 7) continue
            val path = p[5]
            if (!File(path).exists()) continue
            out.add(
                Entry(
                    trackId = p[0], title = p[1], artist = p[2],
                    artUrl = p[3].ifBlank { null }, path = path,
                    bytes = p[4].toLongOrNull() ?: 0L, atMs = p[6].toLongOrNull() ?: 0L,
                )
            )
        }
        return out.sortedByDescending { it.atMs }
    }

    private fun write(list: List<Entry>) {
        sp.edit().putString(
            "entries",
            list.joinToString(R.toString()) {
                listOf(it.trackId, it.title, it.artist, it.artUrl ?: "", it.bytes.toString(), it.path, it.atMs.toString())
                    .joinToString(F.toString())
            },
        ).apply()
        _entries.value = list
    }

    /**
     * The queue survives the process, not the file system.
     *
     * A half-written .part is thrown away on init, so what comes back is the
     * *list* of things asked for, not the bytes already fetched. Promising a
     * resume-from-byte would mean trusting a partial file that no longer has a
     * matching server range — this is the honest version.
     */
    private fun saveQueue() {
        val pending = synchronized(lock) {
            jobs.values.filter { it.state != State.FAILED }.map { Store.encodeTrack(it.track) }
        }
        sp.edit().putString("queue", pending.joinToString(R.toString())).apply()
    }

    private fun restoreQueue() {
        val saved = sp.getString("queue", null) ?: return
        val restored = saved.split(R).mapNotNull { Store.decodeTrack(it) }
            .filterNot { isDownloaded(it.id) }
        if (restored.isEmpty()) return
        synchronized(lock) {
            restored.forEach { jobs.putIfAbsent(it.id, Job(it)) }
        }
        publish()
        ensureDispatcher()
    }

    // ------------------------------------------------------------------ reads

    fun isDownloaded(trackId: String): Boolean = _entries.value.any { it.trackId == trackId }

    fun fileFor(trackId: String): File? =
        _entries.value.firstOrNull { it.trackId == trackId }?.let { File(it.path) }?.takeIf { it.exists() }

    fun totalBytes(): Long = _entries.value.sumOf { it.bytes }

    fun jobFor(trackId: String): Job? = synchronized(lock) { jobs[trackId] }

    fun isQueued(trackId: String): Boolean = jobFor(trackId) != null

    /** Queued + running + held back. Failures are counted separately. */
    fun pendingCount(): Int = synchronized(lock) { jobs.values.count { it.state != State.FAILED } }

    fun failedCount(): Int = synchronized(lock) { jobs.values.count { it.state == State.FAILED } }

    // ----------------------------------------------------------------- writes

    /**
     * Add a track to the queue.
     *
     * Returns false when there is nothing to do — already downloaded, already
     * queued, or a local file that is offline by definition.
     */
    fun enqueue(track: Track): Boolean {
        if (track.isLocal || isDownloaded(track.id)) return false
        val accepted = synchronized(lock) {
            val existing = jobs[track.id]
            when {
                existing == null -> {
                    jobs[track.id] = Job(track)
                    true
                }
                // A failed job is a reason to try again, not a reason to refuse:
                // tapping "Download" a second time used to return the first
                // failure without ever touching the network again.
                existing.state == State.FAILED -> {
                    jobs[track.id] = existing.copy(
                        state = State.QUEUED, doneBytes = 0, totalBytes = 0, error = null,
                    )
                    true
                }
                else -> false
            }
        }
        if (!accepted) return false
        publish()
        saveQueue()
        ensureDispatcher()
        return true
    }

    /** Queue a whole selection, skipping what is already there. */
    fun enqueueAll(tracks: List<Track>): Int {
        var added = 0
        synchronized(lock) {
            tracks.forEach { t ->
                if (t.isLocal || isDownloaded(t.id) || jobs.containsKey(t.id)) return@forEach
                jobs[t.id] = Job(t)
                added++
            }
        }
        if (added > 0) {
            publish()
            saveQueue()
            ensureDispatcher()
        }
        return added
    }

    /** Drop one job. A running one is stopped mid-stream and its partial file deleted. */
    fun cancel(trackId: String) {
        val handle: CoroutineJob? = synchronized(lock) {
            jobs.remove(trackId)
            handles.remove(trackId)
        }
        handle?.cancel()
        runCatching { File(dir, partName(trackId)).delete() }
        publish()
        saveQueue()
    }

    /** Put a failed job back in line, keeping its position in the list. */
    fun retry(trackId: String) {
        synchronized(lock) {
            val job = jobs[trackId] ?: return
            jobs[trackId] = job.copy(state = State.QUEUED, doneBytes = 0, totalBytes = 0, error = null)
        }
        publish()
        saveQueue()
        ensureDispatcher()
    }

    fun retryAllFailed() {
        synchronized(lock) {
            jobs.keys.toList().forEach { id ->
                val job = jobs[id] ?: return@forEach
                if (job.state == State.FAILED) {
                    jobs[id] = job.copy(state = State.QUEUED, doneBytes = 0, totalBytes = 0, error = null)
                }
            }
        }
        publish()
        saveQueue()
        ensureDispatcher()
    }

    fun clearFailed() {
        synchronized(lock) { jobs.values.removeAll { it.state == State.FAILED } }
        publish()
        saveQueue()
    }

    /**
     * Stop starting new work.
     *
     * Running jobs are cancelled rather than frozen: a paused socket still holds
     * a connection, and there is no resume-from-byte to come back to. They go
     * back to the front of the queue, which means a paused download restarts
     * from zero when it is resumed. That is stated here because it is the kind
     * of thing a pause button usually lies about.
     */
    fun pauseAll() {
        _paused.value = true
        val running = synchronized(lock) {
            val ids = jobs.values.filter { it.state == State.RUNNING }.map { it.track.id }
            ids.forEach { id ->
                handles.remove(id)?.cancel()
                jobs[id] = jobs[id]?.copy(state = State.QUEUED, doneBytes = 0, totalBytes = 0) ?: return@forEach
            }
            ids
        }
        running.forEach { runCatching { File(dir, partName(it)).delete() } }
        publish()
    }

    fun resumeAll() {
        _paused.value = false
        ensureDispatcher()
    }

    fun remove(trackId: String) {
        val entry = _entries.value.firstOrNull { it.trackId == trackId }
        if (entry != null) runCatching { File(entry.path).delete() }
        write(_entries.value.filterNot { it.trackId == trackId })
        cancel(trackId)
    }

    fun clearAll() {
        _entries.value.forEach { runCatching { File(it.path).delete() } }
        write(emptyList())
        synchronized(lock) { jobs.keys.toList() }.forEach { cancel(it) }
    }

    // --------------------------------------------------------------- blocking

    /**
     * Enqueue and wait. Kept for the track menu, which shows a result inline.
     *
     * Everything else should use [enqueue] and let the queue screen do the
     * reporting — this is the one caller that has somewhere to put a spinner.
     */
    suspend fun download(track: Track): Result<Unit> {
        if (track.isLocal || isDownloaded(track.id)) return Result.success(Unit)
        enqueue(track)
        return suspendCancellableCoroutine { cont ->
            val watcher = scope.launch {
                while (isActive) {
                    val job = jobFor(track.id)
                    if (job == null) {
                        // Gone from the queue: either it finished, or it was cancelled.
                        val ok = isDownloaded(track.id)
                        cont.resume(
                            if (ok) Result.success(Unit)
                            else Result.failure(IllegalStateException("Download cancelled"))
                        )
                        return@launch
                    }
                    if (job.state == State.FAILED) {
                        cont.resume(Result.failure(IllegalStateException(job.error ?: "Download failed")))
                        return@launch
                    }
                    delay(250)
                }
            }
            cont.invokeOnCancellation { watcher.cancel() }
        }
    }

    // -------------------------------------------------------------- machinery

    private fun partName(trackId: String) =
        trackId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60) + ".part"

    private fun fileName(trackId: String) =
        trackId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60) + ".m4a"

    private fun publish() {
        val snapshot = synchronized(lock) { jobs.values.toList() }
        _queue.value = snapshot
        _progress.value = snapshot.filter { it.state == State.RUNNING }
            .associate { it.track.id to it.fraction }
    }

    private fun update(id: String, block: (Job) -> Job) {
        synchronized(lock) {
            val job = jobs[id] ?: return
            jobs[id] = block(job)
        }
        publish()
    }

    private fun ensureDispatcher() {
        if (dispatcher?.isActive == true) return
        dispatcher = scope.launch {
            var lastBytes = 0L
            var lastAt = System.currentTimeMillis()
            while (isActive) {
                // Speed is measured here rather than in the workers so that two
                // downloads are reported as one number, which is what the eye
                // is actually asking for.
                val now = System.currentTimeMillis()
                val done = synchronized(lock) {
                    jobs.values.filter { it.state == State.RUNNING }.sumOf { it.doneBytes }
                }
                if (now - lastAt >= 500) {
                    val rate = ((done - lastBytes) * 1000L) / (now - lastAt).coerceAtLeast(1)
                    _speed.value = rate.coerceAtLeast(0L)
                    lastBytes = done
                    lastAt = now
                }

                if (_paused.value) {
                    _speed.value = 0L
                    delay(400)
                    continue
                }

                val next = synchronized(lock) { jobs.values.firstOrNull { it.state == State.QUEUED } }
                if (next == null) {
                    // Nothing waiting. If the gate opened since the last pass,
                    // anything parked in WAITING goes back in line.
                    if (wifiGateOpen()) {
                        val parked = synchronized(lock) {
                            jobs.keys.toList().filter { jobs[it]?.state == State.WAITING }
                        }
                        parked.forEach { id -> update(id) { it.copy(state = State.QUEUED) } }
                    }
                    _speed.value = 0L
                    delay(IDLE_POLL_MS)
                    continue
                }

                if (!wifiGateOpen()) {
                    update(next.track.id) { it.copy(state = State.WAITING) }
                    delay(1_500)
                    continue
                }

                val running = synchronized(lock) { jobs.values.count { it.state == State.RUNNING } }
                if (running >= MAX_RUNNING) {
                    delay(250)
                    continue
                }
                start(next.track)
            }
        }
    }

    private fun start(track: Track) {
        update(track.id) { it.copy(state = State.RUNNING, doneBytes = 0, totalBytes = 0, error = null) }
        val handle = scope.launch { run(track) }
        synchronized(lock) { handles[track.id] = handle }
    }

    /**
     * "Wi-Fi only" was a preference with no reader before this. It is the gate
     * now, and a download held back by it says so instead of failing.
     */
    private fun wifiGateOpen(): Boolean {
        if (!Prefs.settings.value.wifiOnly) return true
        return runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }.getOrDefault(true)
    }

    private suspend fun run(track: Track) {
        val target = File(dir, fileName(track.id))
        val temp = File(dir, partName(track.id))
        var conn: HttpURLConnection? = null
        try {
            val url = YtStream.audioUrl(track.videoId)
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 40_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                connect()
            }
            val total = conn.contentLengthLong
            update(track.id) { it.copy(totalBytes = total.coerceAtLeast(0L)) }

            var read = 0L
            var lastEmit = 0L
            conn.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        // Cancelling the coroutine is how "stop this download" works;
                        // this is where the read loop notices.
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        read += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 120) {
                            lastEmit = now
                            update(track.id) { it.copy(doneBytes = read) }
                        }
                    }
                }
            }
            update(track.id) { it.copy(doneBytes = read) }

            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
            write(
                _entries.value + Entry(
                    trackId = track.id, title = track.title, artist = track.artist,
                    artUrl = track.artUrl, path = target.absolutePath,
                    bytes = target.length(), atMs = System.currentTimeMillis(),
                )
            )
            Store.remember(track)
            synchronized(lock) { jobs.remove(track.id); handles.remove(track.id) }
            publish()
            saveQueue()
        } catch (c: CancellationException) {
            runCatching { temp.delete() }
            throw c
        } catch (t: Throwable) {
            runCatching { temp.delete() }
            val reason = when {
                t.message?.contains("Sign in", true) == true -> "YouTube wants a sign-in for this one"
                t is java.net.UnknownHostException -> "No connection"
                t is java.net.SocketTimeoutException -> "The server stopped responding"
                else -> t.message?.take(120) ?: "Download failed"
            }
            synchronized(lock) {
                handles.remove(track.id)
                jobs[track.id] = jobs[track.id]?.copy(state = State.FAILED, error = reason)
                    ?: Job(track, State.FAILED, error = reason)
            }
            publish()
            saveQueue()
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}
