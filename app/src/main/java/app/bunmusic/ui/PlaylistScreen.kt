package app.bunmusic.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
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
import app.bunmusic.data.PlaylistIo
import app.bunmusic.data.Sorting
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
    onToast: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var playlist by remember(playlistId) { mutableStateOf(Store.playlist(playlistId)) }
    var tracks by remember(playlistId) { mutableStateOf(Store.playlistTracks(playlistId)) }
    var renaming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(playlist?.name ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var noting by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf(Store.note(playlistId)) }
    var dupesOpen by remember { mutableStateOf(false) }

    // Export through the system document picker: no FileProvider, no storage
    // permission, and the file lands wherever the listener actually wants it.
    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(PlaylistIo.export(playlist?.name ?: "Playlist", tracks).toByteArray())
            }
        }.onSuccess { onToast("Exported ${tracks.size} tracks") }
            .onFailure { onToast("Could not write the file") }
    }

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
                                color = Ink.Lime, fontSize = 10.sp, fontFamily = SoraFamily,
                                fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp,
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
                            val note = Store.note(playlistId)
                            if (note.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    note,
                                    color = Ink.Faint,
                                    fontSize = 10.5.sp,
                                    fontFamily = SoraFamily,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
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
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GhostBtn(if (Store.note(playlistId).isBlank()) "Add note" else "Edit note", G.Edit) {
                            noteText = Store.note(playlistId)
                            noting = true
                        }
                        GhostBtn("Export", G.Share) {
                            export.launch(PlaylistIo.fileName(playlist?.name ?: "playlist"))
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
                val dupes = Sorting.duplicates(tracks)
                if (dupes.isNotEmpty()) {
                    Row(
                        Modifier
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ink.Lime.copy(alpha = 0.10f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(G.Copy, Ink.Lime, 15.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${dupes.size} duplicate ${if (dupes.size == 1) "song" else "songs"}",
                            color = Ink.Text,
                            fontSize = 12.sp,
                            fontFamily = SoraFamily,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Review",
                            color = Ink.Lime,
                            fontSize = 11.sp,
                            fontFamily = SoraFamily,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { dupesOpen = true },
                        )
                    }
                }
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    GhostBtn("Delete playlist", G.Trash) { confirmDelete = true }
                }
            }
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = Ink.Sheet,
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
            containerColor = Ink.Sheet,
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

    if (noting) {
        AlertDialog(
            onDismissRequest = { noting = false },
            containerColor = Ink.Sheet,
            title = { Text("Playlist note", color = Ink.Text, fontFamily = SoraFamily, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    minLines = 3,
                    placeholder = { Text("Why this list exists…", color = Ink.Faint, fontSize = 13.sp) },
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
                    Store.setNote(playlistId, noteText)
                    noting = false
                }) { Text("Save", color = Ink.Lime) }
            },
            dismissButton = {
                TextButton(onClick = { noting = false }) { Text("Cancel", color = Ink.Muted) }
            },
        )
    }

    if (dupesOpen) {
        val groups = Sorting.duplicates(tracks)
        AlertDialog(
            onDismissRequest = { dupesOpen = false },
            containerColor = Ink.Sheet,
            title = { Text("Duplicates", color = Ink.Text, fontFamily = SoraFamily, fontSize = 16.sp) },
            text = {
                Column {
                    Text(
                        "Same title and artist. Keeping the first of each and removing the rest.",
                        color = Ink.Muted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    groups.take(6).forEach { g ->
                        Text(
                            "${g.first().title} · ${g.size}×",
                            color = Ink.Text,
                            fontSize = 12.sp,
                            fontFamily = SoraFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (groups.size > 6) {
                        Text("+${groups.size - 6} more", color = Ink.Faint, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    groups.forEach { g -> g.drop(1).forEach { Store.removeFromPlaylist(playlistId, it.id) } }
                    dupesOpen = false
                    reload()
                    onToast("Removed ${groups.sumOf { it.size - 1 }} duplicates")
                }) { Text("Remove extras", color = Ink.Lime) }
            },
            dismissButton = {
                TextButton(onClick = { dupesOpen = false }) { Text("Keep", color = Ink.Muted) }
            },
        )
    }
}
