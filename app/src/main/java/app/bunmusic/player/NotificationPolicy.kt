package app.bunmusic.player

/**
 * When the playback card exists, and when it has to be redrawn.
 *
 * Media3 re-checks `shouldShowNotification()` on *every* notification update.
 * It cancels the card when the controller connected to the session sees an
 * empty timeline, or an idle player under
 * `SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER`. That is what "the bar shows for a
 * couple of seconds and then disappears" was: the first post landed, the next
 * redraw failed that check, and `removeNotification()` cancelled it.
 *
 * Two fixes were needed on the service side. The session now reports a
 * non-empty logical timeline whenever BunMusic has a track (see
 * [SessionBridge] and [LogicalTimeline]), and the idle-player mode no longer
 * hides an unprepared-but-real track. This file holds the *other* half: the
 * rules for when we ask Media3 to republish, kept as pure data so they can be
 * tested on the JVM without a service, a session or a phone.
 */
object NotificationPolicy {

    /**
     * The card lives as long as there is a track — playing, paused, buffering,
     * or restored from disk but not yet prepared. An empty state is not a
     * "Nothing playing" card; it is no card at all.
     */
    fun shouldBeLive(hasTrack: Boolean): Boolean = hasTrack

    /** The whole-second bucket the progress bar is redrawn on. */
    fun secondOf(positionMs: Long): Long = positionMs.coerceAtLeast(0L) / 1_000L

    /**
     * Everything the card draws except the elapsed second. A change in any of
     * these needs an immediate redraw: a like, a pause, a track change, a
     * shuffle or repeat toggle, a queue edit, a duration that finally resolved,
     * or buffering starting and stopping.
     *
     * Before this, only `isPlaying && secondChanged` caused a republish, so a
     * tap on the heart or a pause drew nothing until the next second of audio.
     */
    data class Shape(
        val trackId: String?,
        val playing: Boolean,
        val buffering: Boolean,
        val shuffle: Boolean,
        val repeat: Int,
        val queueSize: Int,
        val index: Int,
        val durationMs: Long,
        val liked: Boolean,
    ) {
        companion object {
            fun of(s: PlayerState, liked: Boolean): Shape = Shape(
                trackId = s.track?.id,
                playing = s.isPlaying,
                buffering = s.buffering,
                shuffle = s.shuffle,
                repeat = s.repeat,
                queueSize = s.queue.size,
                index = s.index,
                durationMs = s.durationMs,
                liked = liked,
            )
        }
    }

    /**
     * Redraw on any shape change, and once a second while audio is moving.
     * Nothing is republished when there is no track: Media3 takes the card
     * down, which is the only honest answer to an empty player.
     */
    fun shouldRepublish(
        live: Boolean,
        shapeChanged: Boolean,
        playing: Boolean,
        secondChanged: Boolean,
    ): Boolean = live && (shapeChanged || (playing && secondChanged))
}
