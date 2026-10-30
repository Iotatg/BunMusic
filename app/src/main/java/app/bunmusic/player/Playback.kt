package app.bunmusic.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.bunmusic.data.Downloads
import app.bunmusic.data.Eq
import app.bunmusic.data.Prefs
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import app.bunmusic.data.YtMusic
import app.bunmusic.data.YtStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class PlayerState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val shuffle: Boolean = false,
    val repeat: Int = 0,
    val speed: Float = 1f,
    val sleepEndsAt: Long = 0L,
    val error: String? = null,
) {
    val hasTrack: Boolean get() = track != null
    val hasNext: Boolean get() = index >= 0 && index < queue.lastIndex
    val hasPrev: Boolean get() = index > 0
    val sleepRemainingMs: Long get() = (sleepEndsAt - System.currentTimeMillis()).coerceAtLeast(0L)
    val sleepActive: Boolean get() = sleepEndsAt > 0L
}

/**
 * Single source of truth for playback. One ExoPlayer instance is shared with
 * [PlaybackService] so notification and lock-screen controls stay in sync.
 *
 * Adds over 1.0.0: sleep timer, playback speed, queue editing and a preference
 * for locally downloaded files over network streams.
 */
object Playback {

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var _player: ExoPlayer? = null
    val player: ExoPlayer get() = _player ?: error("Playback.init() not called")

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var queue: List<Track> = emptyList()
    private var index = -1
    private var shuffleOrder: List<Int> = emptyList()
    private var ticker: Job? = null
    private var loadJob: Job? = null
    private var radioJob: Job? = null
    private var eqSession: Int = -1

    fun init(context: Context) {
        if (_player != null) return
        appContext = context.applicationContext
        YtStream.warmUp()

        val dataSource = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(25000)
            .setAllowCrossProtocolRedirects(true)

        val p = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSource))
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true,
                )
                setWakeMode(C.WAKE_MODE_NETWORK)
                setPlaybackParameters(PlaybackParameters(_state.value.speed))
            }

        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publish()
                if (isPlaying) startTicker() else stopTicker()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    when {
                        _state.value.repeat == 2 -> {
                            p.seekTo(0)
                            p.play()
                        }
                        !next(auto = true) -> extendWithRadio()
                    }
                }
                publish()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                publish()
            }
        })
        _player = p
        publish()
    }

    // ------------------------------------------------------------- commands

    fun play(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        queue = tracks
        index = startIndex.coerceIn(0, tracks.lastIndex)
        rebuildShuffle()
        load(index, autoplay = true)
    }

    fun playSingle(track: Track) = play(listOf(track), 0)

    /** Play a list immediately but shuffled. */
    fun shufflePlay(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        queue = tracks.shuffled()
        index = 0
        shuffleOrder = queue.indices.toList()
        _state.value = _state.value.copy(shuffle = true)
        load(0, autoplay = true)
    }

    fun toggle() {
        val p = _player ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            if (p.playbackState == Player.STATE_IDLE) load(index, autoplay = true) else p.play()
        }
        publish()
    }

    /** Returns true when playback actually moved to another track. */
    fun next(auto: Boolean = false): Boolean {
        if (queue.isEmpty()) return false
        val nextIdx = when {
            shuffleOrder.isNotEmpty() && _state.value.shuffle -> {
                val pos = shuffleOrder.indexOf(index)
                shuffleOrder.getOrNull(pos + 1) ?: if (_state.value.repeat == 1 || auto) shuffleOrder.first() else -1
            }
            index < queue.lastIndex -> index + 1
            _state.value.repeat == 1 -> 0
            else -> -1
        }
        if (nextIdx < 0) {
            _player?.pause()
            return false
        }
        index = nextIdx
        load(index, autoplay = true)
        return true
    }

    /**
     * Queue ran out and autoplay is on: pull the next twenty or so tracks from the
     * current song's YouTube radio and keep going. Fails silently — a dead network
     * should never interrupt a listener, it should just stop.
     */
    private fun extendWithRadio() {
        val current = _state.value.track ?: return
        if (!Prefs.settings.value.autoplay) return
        if (current.videoId.isBlank()) return
        if (radioJob?.isActive == true) return
        radioJob = scope.launch {
            val extra = runCatching { YtMusic.radio(current.videoId, 25) }.getOrNull().orEmpty()
            if (extra.isEmpty()) return@launch
            val known = queue.map { it.id }.toSet()
            val fresh = extra.filterNot { known.contains(it.id) }
            if (fresh.isEmpty()) return@launch
            val startAt = queue.size
            queue = queue + fresh
            rebuildShuffle()
            load(startAt, autoplay = true)
        }
    }

    /** Seed a never-ending station from a single track. */
    fun startRadio(track: Track) {
        scope.launch {
            val station = runCatching { YtMusic.radio(track.videoId, 40) }.getOrNull().orEmpty()
            val rest = station.filterNot { it.id == track.id }
            if (rest.isEmpty()) {
                playSingle(track)
            } else {
                play(listOf(track) + rest, 0)
            }
        }
    }

    fun previous() {
        val p = _player ?: return
        if (p.currentPosition > 4000) {
            p.seekTo(0)
            return
        }
        if (queue.isEmpty()) return
        val prevIdx = when {
            shuffleOrder.isNotEmpty() && _state.value.shuffle -> {
                val pos = shuffleOrder.indexOf(index)
                shuffleOrder.getOrNull(pos - 1) ?: shuffleOrder.last()
            }
            index > 0 -> index - 1
            else -> queue.lastIndex
        }
        index = prevIdx
        load(index, autoplay = true)
    }

    fun seekTo(ms: Long) {
        _player?.seekTo(ms.coerceAtLeast(0))
        publish()
    }

    fun seekBy(deltaMs: Long) {
        val p = _player ?: return
        val target = (p.currentPosition + deltaMs).coerceIn(0L, if (p.duration > 0) p.duration else Long.MAX_VALUE)
        p.seekTo(target)
        publish()
    }

    fun toggleShuffle() {
        val s = !_state.value.shuffle
        if (s) rebuildShuffle()
        _state.value = _state.value.copy(shuffle = s)
    }

    fun cycleRepeat() {
        _state.value = _state.value.copy(repeat = (_state.value.repeat + 1) % 3)
    }

    fun enqueueNext(track: Track) {
        val q = queue.toMutableList()
        val at = (index + 1).coerceIn(0, q.size)
        q.add(at, track)
        queue = q
        rebuildShuffle()
        publish()
    }

    fun addToQueue(track: Track) {
        val q = queue.toMutableList()
        q.add(track)
        queue = q
        rebuildShuffle()
        publish()
    }

    fun jumpTo(i: Int) {
        if (i !in queue.indices) return
        index = i
        load(i, autoplay = true)
    }

    fun moveInQueue(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val q = queue.toMutableList()
        val item = q.removeAt(from)
        q.add(to, item)
        queue = q
        if (index == from) index = to
        else if (from < index && to >= index) index -= 1
        else if (from > index && to <= index) index += 1
        rebuildShuffle()
        publish()
    }

    fun removeAt(i: Int) {
        if (i !in queue.indices) return
        val q = queue.toMutableList()
        q.removeAt(i)
        queue = q
        if (q.isEmpty()) {
            stop()
            return
        }
        when {
            i < index -> index -= 1
            i == index -> {
                index = index.coerceAtMost(q.lastIndex)
                load(index, autoplay = _state.value.isPlaying)
                rebuildShuffle()
                return
            }
        }
        rebuildShuffle()
        publish()
    }

    fun clearQueue() {
        val current = _state.value.track
        if (current == null) {
            stop()
            return
        }
        queue = listOf(current)
        index = 0
        rebuildShuffle()
        publish()
    }

    // ------------------------------------------------------------ sleep timer

    fun sleepIn(minutes: Int) {
        if (minutes <= 0) {
            _state.value = _state.value.copy(sleepEndsAt = 0L)
            return
        }
        _state.value = _state.value.copy(sleepEndsAt = System.currentTimeMillis() + minutes * 60_000L)
    }

    fun sleepAtEndOfTrack() {
        _state.value = _state.value.copy(sleepEndsAt = System.currentTimeMillis() + remainingMs() + 1200)
    }

    fun cancelSleep() {
        _state.value = _state.value.copy(sleepEndsAt = 0L)
    }

    // ----------------------------------------------------------------- speed

    fun setSpeed(value: Float) {
        val v = value.coerceIn(0.5f, 2f)
        _player?.playbackParameters = PlaybackParameters(v)
        _state.value = _state.value.copy(speed = v)
    }

    fun stop() {
        _player?.stop()
        _player?.clearMediaItems()
        queue = emptyList()
        index = -1
        _state.value = PlayerState()
    }

    // --------------------------------------------------------------- loading

    private fun load(i: Int, autoplay: Boolean) {
        val track = queue.getOrNull(i) ?: return
        loadJob?.cancel()
        _state.value = _state.value.copy(
            track = track,
            index = i,
            queue = queue,
            buffering = true,
            positionMs = 0,
            durationMs = track.durationMs,
            error = null,
        )
        Store.pushRecent(track)
        Store.recordPlay(track)

        loadJob = scope.launch {
            val local = localUri(track)
            if (local == null && !track.isLocal && Prefs.settings.value.offlineOnly) {
                _state.value = _state.value.copy(
                    buffering = false,
                    isPlaying = false,
                    error = "Offline mode is on — download this track to play it",
                )
                return@launch
            }
            val uri = try {
                local ?: if (track.isLocal) track.uri() else YtStream.audioUrl(track.videoId)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    buffering = false,
                    isPlaying = false,
                    error = t.message ?: "Playback failed",
                )
                return@launch
            }
            val p = _player ?: return@launch
            val item = MediaItem.Builder()
                .setUri(uri)
                .setMediaId(track.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(track.artUrl?.let { android.net.Uri.parse(it) })
                        .build()
                )
                .build()
            p.setMediaItem(item)
            p.prepare()
            bindEffects(p)
            if (autoplay) p.play()
            _state.value = _state.value.copy(buffering = false, track = track)
            publish()
        }
    }

    /** The audio session only exists once something has been prepared. */
    private fun bindEffects(p: ExoPlayer) {
        val session = runCatching { p.audioSessionId }.getOrDefault(-1)
        if (session <= 0 || session == eqSession) return
        eqSession = session
        Eq.attach(session)
        Eq.restore(Prefs.settings.value)
    }

    private fun localUri(track: Track): String? {
        val file: File = Downloads.fileFor(track.id) ?: return null
        return file.toURI().toString()
    }

    private fun rebuildShuffle() {
        shuffleOrder = if (queue.isEmpty()) emptyList() else queue.indices.shuffled()
    }

    private fun remainingMs(): Long {
        val p = _player ?: return 0L
        val dur = p.duration
        if (dur <= 0 || dur == C.TIME_UNSET) return 0L
        return (dur - p.currentPosition).coerceAtLeast(0L)
    }

    private fun publish() {
        val p = _player ?: return
        val duration = p.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: _state.value.track?.durationMs ?: 0L
        _state.value = _state.value.copy(
            isPlaying = p.isPlaying,
            buffering = p.playbackState == Player.STATE_BUFFERING,
            positionMs = p.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            queue = queue,
            index = index,
        )
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                publish()
                val s = _state.value
                if (s.sleepEndsAt > 0 && System.currentTimeMillis() >= s.sleepEndsAt) {
                    _player?.pause()
                    _state.value = _state.value.copy(sleepEndsAt = 0L)
                }
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }
}
