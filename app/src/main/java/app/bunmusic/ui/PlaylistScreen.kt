package app.bunmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.Ambient
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SerifFamily
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import app.bunmusic.player.Playback
import app.bunmusic.player.PlayerState

@Composable
fun PlaylistScreen(
    playlistId: String,
    state: PlayerState,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onMore: (Track) -> Unit,
) {
    var playlist by remember(playlistId) { mutableStateOf(Store.playlist(playlistId)) }
    var tracks by remember(playlistId) { mutableStateOf(Store.playlistTracks(playlistId)) }
    var renaming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(playlist?.name ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    fun reload() {
        playlist = Store.playlist(playlistId)
        tracks = Store.playlistTracks(playlistId)
    }

    val tint = Ambient.colorFor(playlistId)

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .background(
                            Brush.verticalGradient(listOf(tint.copy(alpha = 0.38f), Ink.Void.copy(alpha = 0f)))
                        )
                )
                Column {
                    BackBar("", onBack, trailing = {
                        IconBtn(G.More, Ink.Text, 40.dp, 18.dp) { renaming = true }
                    })
                    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(132.dp).clip(RoundedCornerShape(20.dp)).background(Ink.Card),
                            contentAlignment = Alignment.Center,
                        ) {
                            val first = tracks.firstOrNull()
                            if (first != null) {
                                ArtImage(first.artUrl, first.artAsset, Modifier.fillMaxSize(), RoundedCornerShape(20.dp))
                            } else {
                                Glyph(G.List, Ink.Faint, 30.dp)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "PLAYLIST",
                                color = Ink.Lime, fontSize = 9.sp, fontFamily = SoraFamily,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                playlist?.name ?: "Playlist",
                                color = Ink.Text, fontSize = 23.sp, fontFamily = SerifFamily,
                                lineHeight = 26.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${tracks.size} tracks · ${fmtDuration(tracks.sumOf { it.durationMs })}",
                                color = Ink.Muted, fontSize = 11.5.sp, fontFamily = SoraFamily,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (tracks.isNotEmpty()) {
                        Row(Modifier.padding(horizontal = 20.dp)) {
                            FilledBtn("Play all", G.Play) { onPlay(tracks, 0) }
                            Spacer(Modifier.width(10.dp))
                            GhostBtn("Shuffle", G.Shuffle) { Playback.shufflePlay(tracks) }
                        }
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                EmptyState(
                    G.List, "Empty playlist",
                    "Open any track's menu and choose Add to playlist.",
                )
            }
        }

        itemsIndexed(tracks) { i, t ->
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    TrackRow(
                        track = t,
                        index = i + 1,
                        playing = state.isPlaying,
                        isCurrent = state.track?.id == t.id,
                        onPlay = { onPlay(tracks, i) },
                        onMore = { onMore(t) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).clickable {
                            Store.moveInPlaylist(playlistId, i, i - 1)
                            reload()
                        },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.rotate(-90f)) { Glyph(G.Forward, Ink.Faint, 11.dp) } }
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).clickable {
                            Store.moveInPlaylist(playlistId, i, i + 1)
                            reload()
                        },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.rotate(90f)) { Glyph(G.Forward, Ink.Faint, 11.dp) } }
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).clickable {
                            Store.removeFromPlaylist(playlistId, t.id)
                            reload()
                        },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(G.Close, Ink.Faint, 12.dp) }
                }
                Spacer(Modifier.width(6.dp))
            }
        }

        if (tracks.isNotEmpty()) {
            item {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.padding(horizontal = 20.dp)) {
                    GhostBtn("Delete playlist", G.Trash) { confirmDelete = true }
                }
            }
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = Color(0xFF1C1823),
            title = { Text("Rename playlist", color = Ink.Text, fontFamily = SoraFamily, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Ink.Lime,
                        unfocusedBorderColor = Ink.Line,
                        focusedTextColor = Ink.Text,
                        unfocusedTextColor = Ink.Text,
                        cursorColor = Ink.Lime,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) Store.renamePlaylist(playlistId, name)
                    renaming = false
                    reload()
                }) { Text("Save", color = Ink.Lime) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) { Text("Cancel", color = Ink.Muted) }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Color(0xFF1C1823),
            title = { Text("Delete playlist?", color = Ink.Text, fontFamily = SoraFamily, fontSize = 16.sp) },
            text = { Text("This only removes the list. Your liked songs stay.", color = Ink.Muted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    Store.deletePlaylist(playlistId)
                    confirmDelete = false
                    onBack()
                }) { Text("Delete", color = Ink.Rose) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep", color = Ink.Muted) }
            },
        )
    }
}
