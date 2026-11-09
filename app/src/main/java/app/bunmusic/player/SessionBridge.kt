package app.bunmusic.player

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import app.bunmusic.data.Store
import app.bunmusic.data.Track

/**
 * ExoPlayer, as the media session is allowed to see it.
 *
 * BunMusic hands ExoPlayer exactly one track at a time — the next stream URL is
 * resolved when it is needed, and a URL resolved ten minutes ago is dead anyway,
 * so a real playlist timeline is not possible. That decision has a visible cost:
 * the session sees a one-item timeline, decides there is no next and no
 * previous, and the lock screen loses its skip buttons. The queue is real; only
 * the player's view of it was missing.
 *
 * This wrapper adds the two skip commands back and forwards them to [Playback],
 * which owns the queue. Everything else is ExoPlayer's own answer, unchanged.
 */
class SessionBridge(base: Player) : ForwardingPlayer(base) {

    private val state: PlayerState get() = Playback.state.value

    override fun getAvailableCommands(): Player.Commands {
        val s = state
        val builder = Player.Commands.Builder().addAll(super.getAvailableCommands())
        if (SessionControls.hasNext(s)) {
            builder.addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        if (SessionControls.hasPrev(s)) {
            builder.addAll(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        }
        return builder.build()
    }

    /** Must agree with [getAvailableCommands], or the session advertises dead buttons. */
    override fun isCommandAvailable(command: Int): Boolean = getAvailableCommands().contains(command)

    override fun hasNextMediaItem(): Boolean = SessionControls.hasNext(state)

    override fun hasPreviousMediaItem(): Boolean = SessionControls.hasPrev(state)

    override fun seekToNext() {
        if (SessionControls.hasNext(state)) Playback.next()
    }

    override fun seekToNextMediaItem() = seekToNext()

    override fun seekToPrevious() {
        // Playback.previous() restarts the current track when it is already past
        // the first few seconds, which is the behaviour the button promises.
        if (SessionControls.hasPrev(state)) Playback.previous()
    }

    override fun seekToPreviousMediaItem() = seekToPrevious()

    /**
     * Before the stream is prepared ExoPlayer reports an unknown duration, and
     * the lock screen draws an empty bar with no scrubber. The track's own length
     * is already known from the search result, so report that instead — the
     * position it is paired with is real either way.
     */
    override fun getDuration(): Long {
        val real = super.getDuration()
        if (real != C.TIME_UNSET && real > 0L) return real
        return state.durationMs
    }

    // ------------------------------------------------------- logical timeline
    //
    // Media3 cancels the playback notification whenever the controller sees an
    // empty timeline — it does not ask the idle-player mode first. A restored
    // session, a track swap while the next URL resolves, or Android 13 media
    // resumption all leave ExoPlayer's real timeline empty for a moment while
    // Playback.state already has a track. That is the "the card appears, then
    // goes away" failure. The answers below describe the track BunMusic has,
    // not the source ExoPlayer happens to have prepared, and step aside as soon
    // as a real timeline exists.

    override fun getCurrentTimeline(): Timeline {
        val real = super.getCurrentTimeline()
        if (!real.isEmpty) return real
        val track = state.track ?: return real
        return LogicalTimeline(item(track), track.durationMs)
    }

    override fun getMediaItemCount(): Int {
        val real = super.getMediaItemCount()
        if (real > 0) return real
        return if (state.track != null) 1 else 0
    }

    override fun getMediaItemAt(index: Int): MediaItem {
        if (super.getMediaItemCount() > 0) return super.getMediaItemAt(index)
        val track = state.track ?: return super.getMediaItemAt(index)
        return item(track)
    }

    override fun getCurrentMediaItemIndex(): Int {
        val real = super.getCurrentMediaItemIndex()
        if (real != C.INDEX_UNSET) return real
        return if (state.track != null) 0 else real
    }

    // ------------------------------------------------------------- resumption

    /**
     * The system can ask a session to pick up where it left off (Android 13 media
     * resumption, and after a reboot). Media3 answers that by setting media items
     * on this player — but this player only ever holds one item, so the items are
     * decoded back into the queue and handed to [Playback] instead.
     *
     * Items without our tag are not ours: they are passed straight through, so a
     * future caller that really wants the raw player still gets it.
     */
    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        val tracks = mediaItems.map { item ->
            item.mediaMetadata.extras?.getString(TAG_TRACK)?.let(Store::decodeTrack)
        }
        if (tracks.isEmpty() || tracks.any { it == null }) {
            super.setMediaItems(mediaItems, startIndex, startPositionMs)
            return
        }
        adopt(tracks.filterNotNull(), startIndex, startPositionMs)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) =
        setMediaItems(mediaItems, 0, C.TIME_UNSET)

    override fun setMediaItem(mediaItem: MediaItem) =
        setMediaItems(listOf(mediaItem), 0, C.TIME_UNSET)

    private fun adopt(tracks: List<Track>, startIndex: Int, startPositionMs: Long) {
        // Seeking right after play() used to hit an empty timeline: play()
        // starts an async load, so the seek was discarded when the media source
        // was finally prepared. Pass the position through instead — Playback
        // holds it in pendingSeekMs and applies it immediately after prepare().
        Playback.play(
            tracks,
            startIndex.coerceIn(0, tracks.lastIndex),
            startPositionMs,
        )
    }

    companion object {
        /** Where a [MediaItem] keeps the whole track, so resumption needs no lookup. */
        const val TAG_TRACK = "app.bunmusic.track"

        fun item(track: Track): MediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri("bun://${track.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    // The whole track travels with the item: resumption hands these
                    // back after the process died, so there is nothing to look up.
                    .setExtras(android.os.Bundle().apply { putString(TAG_TRACK, Store.encodeTrack(track)) })
                    .build()
            )
            .build()
    }
}
