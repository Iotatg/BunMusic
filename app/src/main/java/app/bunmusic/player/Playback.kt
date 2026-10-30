package app.bunmusic.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.bunmusic.data.Track
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
import kotlinx.coroutines.withContext

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
    val error: String? = null,
) {
    val hasTrack: Boolean get() = track != null
    val hasNext: Boolean get() = index >= 0 && index < queue.lastIndex
    val hasPrev: Boolean get() = index > 0
}

/**
 * Single source of truth for playback. Holds one ExoPlayer that is shared with
 * [PlaybackService] so the notification / lock-screen controls stay in sync.
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
            .build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_NETWORK)
        }
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publish()
                if (isPlaying) startTicker() else stopTicker()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (_state.value.repeat == 2) {
                        p.seekTo(0)
                        p.play()
                    } else {
                        next(auto = true)
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

    fun toggle() {
        val p = _player ?: return
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == Player.STATE_IDLE) load(index, autoplay = true) else p.play()
        }
        publish()
    }

    fun next(auto: Boolean = false) {
        if (queue.isEmpty()) return
        val nextIdx = when {
            shuffleOrder.isNotEmpty() && _state.value.shuffle -> {
                val pos = shuffleOrder.indexOf(index)
                shuffleOrder.getOrNull(pos + 1) ?: if (_state.value.repeat == 1 || auto) shuffleOrder.first() else -1
            }
            index < queue.lastIndex -> index + 1
            _state.value.repeat == 1 || (auto && _state.value.repeat != 0) -> 0
            else -> -1
        }
        if (nextIdx < 0) {
            _player?.pause()
            return
        }
        index = nextIdx
        load(index, autoplay = true)
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

    fun toggleShuffle() {
        val s = !_state.value.shuffle
        rebuildShuffle()
        _state.value = _state.value.copy(shuffle = s)
    }

    fun cycleRepeat() {
        _state.value = _state.value.copy(repeat = (_state.value.repeat + 1) % 3)
    }

    fun enqueueNext(track: Track) {
        val q = queue.toMutableList()
        q.add(index + 1, track)
        queue = q
        publish()
    }

    fun addToQueue(track: Track) {
        val q = queue.toMutableList()
        q.add(track)
        queue = q
        publish()
    }

    fun jumpTo(i: Int) {
        if (i !in queue.indices) return
        index = i
        load(i, autoplay = true)
    }

    fun stop() {
        _player?.stop()
        _player?.clearMediaItems()
        queue = emptyList()
        index = -1
        _state.value = PlayerState()
    }

    // -------------------------------------------------------------- loading

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
        app.bunmusic.data.Store.pushRecent(track)

        loadJob = scope.launch {
            val uri = try {
                if (track.isLocal) track.uri() else YtStream.audioUrl(track.videoId)
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
                        .build()
                )
                .build()
            p.setMediaItem(item)
            p.prepare()
            if (autoplay) p.play()
            _state.value = _state.value.copy(buffering = false, track = track)
            publish()
        }
    }

    private fun rebuildShuffle() {
        shuffleOrder = if (queue.isEmpty()) emptyList() else queue.indices.shuffled()
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
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }
}
