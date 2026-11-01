package app.bunmusic.system

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.bunmusic.MainActivity
import app.bunmusic.R
import app.bunmusic.player.Playback

/**
 * Play/pause from the Quick Settings shade.
 *
 * The tile is a mirror, not a remote control with its own state: it reads the
 * same [Playback] singleton the notification does, so the two can never disagree
 * about whether something is playing.
 */
class PlaybackTileService : TileService() {

    override fun onStartListening() {
        refresh()
    }

    override fun onTileAdded() {
        refresh()
    }

    override fun onClick() {
        if (Playback.state.value.hasTrack) {
            Playback.toggle()
            refresh()
        } else {
            open()
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val state = Playback.state.value
        runCatching {
            tile.state = if (state.isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = "BunMusic"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_w_play)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = state.track?.title ?: "Nothing playing"
            }
            tile.updateTile()
        }
    }

    private fun open() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("open_player", true)
        val pending = PendingIntent.getActivity(
            this,
            7100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
