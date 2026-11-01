package app.bunmusic.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.bunmusic.data.Cache
import app.bunmusic.data.Downloads
import app.bunmusic.data.Eq
import app.bunmusic.data.Prefs
import app.bunmusic.data.Scrobble
import app.bunmusic.data.Session
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
    /** A–B repeat markers. 0 means unset. */
    val abStartMs: Long = 0L,
    val abEndMs: Long = 0L,
) {
    val hasTrack: Boolean get() = track != null
    val hasNext: Boolean get() = index >= 0 && index < queue.lastIndex
    val hasPrev: Boolean get() = index > 0
    val sleepRemainingMs: Long get() = (sleepEndsAt - System.currentTimeMillis()).coerceAtLeast(0L)
    val sleepActive: Boolean get() = sleepEndsAt > 0L
    val abActive: Boolean get() = abStartMs >= 0 && abEndMs > abStartMs
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
    /** Guards against a failing item bouncing between error and retry forever. */
    private var retrying = false
    /** Where a restored session should start once the item is prepared. */
    private var pendingSeekMs = 0L
    /** Session writes are throttled; the ticker calls publish() twice a second. */
    private var lastSaveAt = 0L
    private var playStartedAt = 0L
    private var scrobbled = false
    private var lastGain = 1f

    /** How long the sleep timer takes to fade out. */
    private val FADE_MS = 10_000L

    fun init(context: Context) {
        if (_player != null) return
        appContext = context.applicationContext
        YtStream.warmUp()

        val p = ExoPlayer.Builder(appContext)
            // Tuned for a phone on a weak connection: buffer a lot, start early, and
            // do not give up on a slow socket while audio is already flowing.
            .setLoadControl(
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        20_000,   // min buffer
                        120_000,  // max buffer
                        2_500,    // buffer needed to start
                        6_000,    // buffer needed to resume after a stall
                    )
                    .setTargetBufferBytes(48 * 1024 * 1024)
                    .build()
            )
            // Network reads pass through the stream cache; local files do not (see load()).
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(Cache.deferredFactory())
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
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
                skipSilenceEnabled = Prefs.settings.value.skipSilence
            }

        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publish()
                if (isPlaying) startTicker() else stopTicker()
            }

            /**
             * A cached track is played from a placeholder URI, because the bytes are
             * already on disk and the signed URL that produced them has expired. If
             * the cache was trimmed underneath us, that placeholder has nowhere to go
             * — so re-resolve once and carry on instead of showing an error.
             */
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val s = _state.value
                val track = s.track ?: return
                if (retrying || track.isLocal) {
                    _state.value = s.copy(isPlaying = false, buffering = false, error = "This track could not be played")
                    return
                }
                if (!Cache.hasOffline(track.videoId) && Cache.completeKey(track.videoId) == null) {
                    _state.value = s.copy(isPlaying = false, buffering = false, error = friendlyError(error))
                    return
                }
                retrying = true
                scope.launch {
                    runCatching { load(index, autoplay = true) }
                    retrying = false
                }
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
        app.bunmusic.widget.Widgets.attach(appContext)
        publish()
    }

    // --------------------------------------------------------------- session

    /**
     * Bring back the last queue, paused where it was left.
     *
     * Deliberately does *not* prepare the track: preparing resolves a stream URL,
     * and a cold start on mobile data should not quietly open a socket before the
     * listener has asked for anything. The state is restored, the artwork and the
     * queue are on screen, and the first press of play does the network work.
     */
    fun restoreSession() {
        if (_player == null || _state.value.track != null) return
        val saved = Session.load() ?: return
        val track = saved.track ?: return
        queue = saved.queue
        index = saved.index
        rebuildShuffle()
        pendingSeekMs = saved.positionMs.coerceAtLeast(0L)
        _state.value = PlayerState(
            track = track,
            isPlaying = false,
            positionMs = pendingSeekMs,
            durationMs = track.durationMs,
            queue = queue,
            index = index,
            shuffle = saved.shuffle,
            repeat = saved.repeat,
            speed = saved.speed,
        )
        runCatching { _player?.playbackParameters = PlaybackParameters(saved.speed) }
        publish()
    }

    /** True when a cold start has something to offer on the home screen. */
    fun resumable(): Session.Saved? = if (_state.value.track != null) null else Session.resumable()

    private fun saveSession(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSaveAt < 4_000) return
        lastSaveAt = now
        Session.save(_state.value)
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
        // Keep the list in its original order and let rebuildShuffle() decide the
        // playing order — that way the queue sheet still reads like the playlist,
        // and the shuffle can avoid repeating an artist.
        queue = tracks
        index = 0
        rebuildShuffle()
        _state.value = _state.value.copy(shuffle = true)
        load(0, autoplay = true)
    }

    fun toggle() {
        val p = _player ?: return
        if (p.isPlaying) {
            p.pause()
            saveSession(force = true)
        } else {
            if (p.playbackState == Player.STATE_IDLE) {
                load(index, autoplay = true)
            } else {
                p.play()
                // The service can be gone — killed for memory, or stopped when the
                // task was swiped away while paused. Resuming has to bring the
                // session back, or the notification never returns.
                ServiceLauncher.start(appContext)
            }
        }
        publish()
    }

    /** Returns true when playback actually moved to another track. */
    fun next(auto: Boolean = false): Boolean {
        if (queue.isEmpty()) return false
        // Leaving a song inside the first thirty seconds is a skip, not a listen.
        // Recorded separately so smart shuffle can learn from it without the play
        // counts, the charts or the year in review ever seeing it.
        if (!auto) {
            val p = _player
            val current = queue.getOrNull(index)
            if (p != null && current != null && p.currentPosition < 30_000L) {
                Store.recordSkip(current)
            }
        }
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

    fun applySkipSilence(on: Boolean) {
        runCatching { _player?.skipSilenceEnabled = on }
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

    /**
     * Freeze the queue into a playlist. A radio station that turned out well is
     * worth keeping, and the queue is the only place it exists.
     */
    fun saveQueueAsPlaylist(): String {
        if (queue.isEmpty()) return ""
        val p = Store.createPlaylist("Saved queue · ${queue.size}")
        queue.forEach { Store.addToPlaylist(p.id, it) }
        return p.name
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

    /** Drop everything before the current track. The past stops taking up rows. */
    fun clearPlayed() {
        if (index <= 0 || index !in queue.indices) return
        queue = queue.drop(index)
        index = 0
        rebuildShuffle()
        publish()
    }

    /** Shuffle what is left to play, leaving the current track and the past alone. */
    fun shuffleUpcoming() {
        if (index !in queue.indices) return
        queue = app.bunmusic.data.QueueMath.shuffleUpcoming(
            queue, index, (System.currentTimeMillis() / 1000).toInt(),
        )
        if (_state.value.shuffle) rebuildShuffle()
        publish()
    }

    /** Back to the top of the current track. */
    fun restart() {
        _player?.seekTo(0)
        publish()
    }

    /** Drop the track that is playing and move to whatever is next. */
    fun removeCurrent() {
        if (index in queue.indices) removeAt(index)
    }

    /** Nudge the current track up or down the queue. */
    fun moveCurrent(by: Int) {
        if (index !in queue.indices) return
        val to = (index + by).coerceIn(0, queue.lastIndex)
        moveInQueue(index, to)
    }

    /**
     * Shuffle what is *left*, keeping the current track where it is.
     *
     * Toggling shuffle re-orders the whole queue from the top, which is wrong
     * when you are three songs into a playlist and only want the rest of it
     * mixed up. This leaves everything before the cursor alone.
     */
    fun shuffleQueueOrder() {
        if (queue.size - index < 3) return
        val head = queue.take(index + 1)
        val tail = queue.drop(index + 1).shuffled()
        queue = head + tail
        rebuildShuffle()
        publish()
    }

    /** Like or unlike the current track. Returns the new state. */
    fun likeCurrent(): Boolean {
        val track = _state.value.track ?: return false
        val liked = Store.toggleLike(track.id)
        publish()
        return liked
    }

    /**
     * "Not for me": record it, and move on.
     *
     * A dislike that only wrote a flag would be a lie — the whole point is that the
     * song stops. So it also skips, which is what every streaming app does with
     * this button.
     */
    fun dislikeCurrent(): Boolean {
        val track = _state.value.track ?: return false
        val now = Store.toggleDislike(track)
        if (now) next()
        publish()
        return now
    }

    /**
     * Radio from a list rather than a single song: an artist page, a playlist, a
     * whole album. Seeded on the first track that can actually produce a station.
     */
    fun startRadioFrom(tracks: List<Track>) {
        val seed = tracks.firstOrNull { it.videoId.isNotBlank() } ?: return
        startRadio(seed)
    }

    // ------------------------------------------------------------ sleep timer

    fun sleepIn(minutes: Int) {
        if (minutes <= 0) {
            cancelSleep()
            return
        }
        _state.value = _state.value.copy(sleepEndsAt = System.currentTimeMillis() + minutes * 60_000L)
    }

    fun sleepAtEndOfTrack() {
        _state.value = _state.value.copy(sleepEndsAt = System.currentTimeMillis() + remainingMs() + 1200)
    }

    fun cancelSleep() {
        _state.value = _state.value.copy(sleepEndsAt = 0L)
        // A timer that was already fading must not leave the music quiet.
        runCatching { _player?.volume = 1f }
    }

    // ------------------------------------------------------------- A–B repeat

    /** Mark A. Marking B without an A first marks both ends at once. */
    fun markA() {
        val at = _state.value.positionMs.coerceAtLeast(0L)
        _state.value = _state.value.copy(abStartMs = at, abEndMs = 0L)
    }

    fun markB() {
        val s = _state.value
        val at = s.positionMs
        if (s.abStartMs <= 0L || at <= s.abStartMs + 1_000L) {
            // A single button press on a fresh track: loop from here to the end of
            // the song rather than refusing with a toast nobody asked for.
            val end = s.durationMs.takeIf { it > s.abStartMs + 2_000L } ?: return
            _state.value = s.copy(abEndMs = end)
        } else {
            _state.value = s.copy(abEndMs = at)
        }
        _player?.seekTo(s.abStartMs)
    }

    fun clearAb() {
        _state.value = _state.value.copy(abStartMs = 0L, abEndMs = 0L)
    }

    /** One button that walks A → B → clear, for the row under the transport. */
    fun abCycle() {
        val s = _state.value
        when {
            s.abActive -> clearAb()
            s.abStartMs <= 0L -> markA()
            else -> markB()
        }
    }

    // ----------------------------------------------------------------- speed

    fun setSpeed(value: Float) {
        val v = value.coerceIn(0.5f, 2f)
        _player?.playbackParameters = PlaybackParameters(v)
        _state.value = _state.value.copy(speed = v)
    }

    /**
     * The listener swiped the card away while it was paused.
     *
     * The service goes (otherwise it runs on with nothing able to reach it) but
     * the saved session stays, so the queue is still there on the next launch.
     */
    fun dismissCard() {
        _player?.pause()
        saveSession(force = true)
        if (::appContext.isInitialized) ServiceLauncher.stop(appContext)
    }

    fun stop() {
        _player?.stop()
        _player?.clearMediaItems()
        queue = emptyList()
        index = -1
        pendingSeekMs = 0L
        _state.value = PlayerState()
        Session.clear()
        // Nothing left to control, so the notification goes with it.
        if (::appContext.isInitialized) ServiceLauncher.stop(appContext)
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
            if (local == null && !track.isLocal && Prefs.settings.value.offlineOnly && !Cache.isComplete(track.videoId)) {
                _state.value = _state.value.copy(
                    buffering = false,
                    isPlaying = false,
                    error = "Offline mode is on — this track is not cached or downloaded yet",
                )
                return@launch
            }

            // Three ways in, in order of cost:
            //  1. a downloaded file — always local
            //  2. a fully cached stream — no network at all, so it works on a plane
            //  3. resolve a fresh URL, play it through the cache, then pull the rest
            var info: app.bunmusic.data.StreamInfo? = null
            var cacheKey: String? = null
            val uri: String = when {
                local != null -> local
                track.isLocal -> track.uri()
                Cache.isComplete(track.videoId) -> {
                    val entry = Cache.cached(track.videoId)!!
                    cacheKey = "yt:${track.videoId}:${entry.itag}"
                    // The URL is irrelevant when the bytes are on disk; the cache is
                    // keyed on videoId+itag, so even an expired one never opens.
                    "https://bunmusic.local/cache/${track.videoId}/${entry.itag}"
                }
                else -> try {
                    val resolved = YtStream.resolve(track.videoId, Prefs.settings.value.quality)
                    info = resolved
                    cacheKey = resolved.key
                    resolved.url
                } catch (t: Throwable) {
                    val cached = Cache.cached(track.videoId)
                    _state.value = _state.value.copy(
                        buffering = false,
                        isPlaying = false,
                        error = when {
                            t.message?.contains("Sign in", true) == true ->
                                "YouTube wants a sign-in for this one. Try another track."
                            cached != null -> "This track was only partly cached — connect once to finish it"
                            else -> t.message ?: "Playback failed"
                        },
                    )
                    return@launch
                }
            }

            val p = _player ?: return@launch
            val builder = MediaItem.Builder()
                .setUri(uri)
                .setMediaId(track.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setArtworkUri(track.artUrl?.let { android.net.Uri.parse(app.bunmusic.data.hq(it)) })
                        .build()
                )
            // The cache key must survive the URL changing, or every play is a miss.
            cacheKey?.let { builder.setCustomCacheKey(it) }
            val item = builder.build()

            // A downloaded track is read straight off disk — routing it through the
            // cache would store the same bytes twice for no benefit.
            if (local != null || track.isLocal) {
                p.setMediaSource(
                    androidx.media3.exoplayer.source.ProgressiveMediaSource
                        .Factory(androidx.media3.datasource.DefaultDataSource.Factory(appContext))
                        .createMediaSource(item)
                )
            } else {
                p.setMediaSource(
                    androidx.media3.exoplayer.source.ProgressiveMediaSource
                        .Factory(Cache.deferredFactory())
                        .createMediaSource(item)
                )
            }
            p.prepare()
            bindEffects(p)
            // A restored session starts where it stopped, not at 0:00.
            if (pendingSeekMs > 0) {
                p.seekTo(pendingSeekMs)
                _state.value = _state.value.copy(positionMs = pendingSeekMs)
                pendingSeekMs = 0L
            }
            if (autoplay) p.play()
            // The session lives in the service, and the session is the only thing
            // the system can see: notification, lock screen, Bluetooth buttons.
            // Started here, after play(), so the first notification Media3 builds
            // already knows it is playing instead of flashing a paused card.
            if (autoplay) ServiceLauncher.start(appContext)
            playStartedAt = System.currentTimeMillis()
            scrobbled = false
            _state.value = _state.value.copy(buffering = false, track = track)
            publish()
            saveSession(force = true)
            runCatching { Scrobble.nowPlaying(track) }

            // Keep the whole song, not just the part that played. This is what makes
            // the second play offline-capable.
            if (info != null && !Prefs.settings.value.offlineOnly) {
                Cache.prefetch(info!!)
            }
            // ...and quietly pull the next couple down as well, so the end of this
            // song does not have to wait on a resolve.
            // "Gapless" is the prefetch: the only gap between two separate streams
            // is the resolve, so having the next one already on disk is the whole
            // feature. Turning it off is what makes a gap appear on purpose.
            val settings = Prefs.settings.value
            if ((settings.gapless || settings.prefetchAhead) && !settings.offlineOnly) {
                prefetchAhead(i)
            }
        }
    }

    /**
     * Warm the cache for what comes next. Two tracks is the sweet spot: enough to
     * hide a slow handover, not so much that it competes with the song you are
     * actually listening to on a thin connection.
     */
    private fun prefetchAhead(from: Int) {
        val upcoming = queue.drop(from + 1).take(2).filter { !it.isLocal && it.videoId.isNotBlank() }
        if (upcoming.isEmpty()) return
        scope.launch {
            for (track in upcoming) {
                if (Cache.isComplete(track.videoId)) continue
                runCatching {
                    Cache.prefetch(YtStream.resolve(track.videoId, Prefs.settings.value.quality))
                }
            }
        }
    }

    private fun friendlyError(e: androidx.media3.common.PlaybackException): String = when (e.errorCode) {
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> "No connection — this track is not cached yet"
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "YouTube refused the stream. Try again in a moment."
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "The cached copy went away. Play it once online to rebuild it."
        else -> "Playback failed"
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

    /**
     * Shuffle that behaves itself: it starts on the song you are already on, and it
     * never puts two tracks by the same artist back to back when it has a choice.
     * `indices.shuffled()` does neither, and the difference is audible.
     *
     * With smart shuffle on, two more rules apply: songs skipped three times are
     * left out entirely, and songs heard in the last ten plays go behind
     * everything else. Both are read from [Store], never written here.
     */
    private fun rebuildShuffle() {
        if (queue.isEmpty()) {
            shuffleOrder = emptyList()
            return
        }
        val start = index.takeIf { it in queue.indices } ?: 0
        val smart = Prefs.settings.value.smartShuffle
        val skips = if (smart) Store.skips() else emptyMap()
        val recent = if (smart) Store.history().take(10).map { it.track.id }.toSet() else emptySet()

        val eligible = queue.indices.filter { it != start && (skips[queue[it].id] ?: 0) < 3 }
        val fallback = queue.indices.filter { it != start }
        val base = eligible.ifEmpty { fallback }
        val pool = (base.filter { queue[it].id !in recent } + base.filter { queue[it].id in recent })
            .toMutableList()

        val order = ArrayList<Int>(queue.size)
        order.add(start)
        var lastArtist = queue[start].artist.trim().lowercase()
        while (pool.isNotEmpty()) {
            val pickIndex = pool.indexOfFirst { queue[it].artist.trim().lowercase() != lastArtist }
                .takeIf { it >= 0 } ?: 0
            val pick = pool.removeAt(pickIndex)
            order.add(pick)
            lastArtist = queue[pick].artist.trim().lowercase()
        }
        shuffleOrder = order
    }

    private fun remainingMs(): Long {
        val p = _player ?: return 0L
        val dur = p.duration
        if (dur <= 0 || dur == C.TIME_UNSET) return 0L
        return (dur - p.currentPosition).coerceAtLeast(0L)
    }

    private fun publish() {
        val p = _player ?: return
        if (_state.value.track != null) saveSession()
        val duration = p.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: _state.value.track?.durationMs ?: 0L
        _state.value = _state.value.copy(
            isPlaying = p.isPlaying,
            buffering = p.playbackState == Player.STATE_BUFFERING,
            positionMs = p.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            queue = queue,
            index = index,
        )
        // Keep the home-screen bar in step. It throttles internally, so calling it
        // twice a second is cheap.
        app.bunmusic.widget.Widgets.onPlayback()
    }

    /**
     * Twice a second: publish, then work out one gain from two independent
     * reasons to lower it — the sleep timer running out, and the crossfade at a
     * track boundary. They are combined with `min` rather than by each writing
     * `player.volume`, because the last writer used to win and a fade-out could
     * resurrect the music after the timer had already silenced it.
     */
    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                publish()
                val s = _state.value
                val p = _player
                if (p != null) {
                    // A–B repeat: the loop lives here, not in the player, because
                    // ExoPlayer's repeat modes are per-item and this is per-second.
                    if (s.abActive && s.positionMs >= s.abEndMs) {
                        p.seekTo(s.abStartMs)
                    }
                    var sleepGain = 1f
                    if (s.sleepEndsAt > 0) {
                        val left = s.sleepEndsAt - System.currentTimeMillis()
                        if (left <= 0) {
                            p.pause()
                            _state.value = _state.value.copy(sleepEndsAt = 0L)
                        } else if (Prefs.settings.value.sleepFade && left <= FADE_MS) {
                            // Ease the last ten seconds down instead of clipping the
                            // song mid-note — the point of a sleep timer is that you
                            // do not notice it.
                            sleepGain = (left.toFloat() / FADE_MS).coerceIn(0f, 1f)
                        }
                    }
                    val fadeMs = Prefs.settings.value.crossfadeSec * 1_000L
                    val crossGain = if (fadeMs > 0 && s.isPlaying) {
                        Crossfade.gain(p.currentPosition, p.duration.takeIf { it > 0 } ?: 0L, fadeMs)
                    } else {
                        1f
                    }
                    val gain = minOf(sleepGain, crossGain)
                    // Only touch the player when it actually changed: a volume
                    // setter 2x a second on every tick is audible on some devices.
                    if (kotlin.math.abs(gain - lastGain) > 0.004f) {
                        runCatching { p.volume = gain }
                        lastGain = gain
                    }

                    // Scrobble once per track, at the half-way mark or four minutes
                    // in, whichever comes first — the usual rule.
                    if (!scrobbled && s.isPlaying && s.durationMs > 0) {
                        val mark = minOf(s.durationMs / 2, 240_000L)
                        if (s.positionMs >= mark) {
                            scrobbled = true
                            val track = s.track
                            val at = playStartedAt / 1000
                            if (track != null) scope.launch { runCatching { Scrobble.submit(track, at) } }
                        }
                    }
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
