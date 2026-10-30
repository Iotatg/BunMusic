package app.bunmusic.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Downloads
import app.bunmusic.data.Playlist
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import app.bunmusic.player.Playback
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSheet(
    track: Track,
    isLiked: Boolean,
    isDownloaded: Boolean,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var liked by remember(track.id) { mutableStateOf(isLiked) }
    var saved by remember(track.id) { mutableStateOf(isDownloaded) }
    var busy by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf(Store.playlists()) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF17131E),
        contentColor = Ink.Text,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtImage(
                    url = track.artUrl,
                    asset = track.artAsset,
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        color = Ink.Text,
                        fontSize = 15.sp,
                        fontFamily = SoraFamily,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.artist,
                        color = Ink.Muted,
                        fontSize = 12.sp,
                        fontFamily = SoraFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (picker) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).clickable { picker = false },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(G.Back, Ink.Muted, 15.dp) }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Add to playlist",
                        color = Ink.Text, fontSize = 14.sp, fontFamily = SoraFamily,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (creating) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            singleLine = true,
                            placeholder = { Text("Playlist name", color = Ink.Faint, fontSize = 13.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Ink.Lime,
                                unfocusedBorderColor = Ink.Line,
                                focusedTextColor = Ink.Text,
                                unfocusedTextColor = Ink.Text,
                                cursorColor = Ink.Lime,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(if (newName.isBlank()) Ink.Line else Ink.Lime)
                                .clickable {
                                    if (newName.isNotBlank()) {
                                        val p = Store.createPlaylist(newName)
                                        Store.addToPlaylist(p.id, track)
                                        playlists = Store.playlists()
                                        creating = false
                                        newName = ""
                                        onToast("Added to ${p.name}")
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(
                                "Create",
                                color = if (newName.isBlank()) Ink.Faint else Ink.OnAccent,
                                fontSize = 12.sp, fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                } else {
                    SheetAction("New playlist", G.Plus) { creating = true }
                }

                LazyColumn(Modifier.fillMaxWidth().height(260.dp)) {
                    items(playlists) { p: Playlist ->
                        SheetAction("${p.name}  ·  ${p.size}") {
                            val added = Store.addToPlaylist(p.id, track)
                            onToast(if (added) "Added to ${p.name}" else "Already in ${p.name}")
                            picker = false
                        }
                    }
                }
            } else {
                SheetAction(if (liked) "Remove from liked" else "Add to liked", if (liked) G.HeartFill else G.Heart) {
                    liked = Store.toggleLike(track)
                    onToast(if (liked) "Added to liked songs" else "Removed from liked")
                }
                SheetAction("Play next", G.PlayCircle) {
                    Playback.enqueueNext(track)
                    onToast("Playing next")
                }
                SheetAction("Add to queue", G.Queue) {
                    Playback.addToQueue(track)
                    onToast("Added to queue")
                }
                SheetAction("Start radio", G.Wave) {
                    Playback.startRadio(track)
                    onToast("Radio started from ${track.title}")
                    onDismiss()
                }
                SheetAction("Add to playlist", G.Plus) { picker = true }
                SheetAction(
                    when {
                        saved -> "Remove download"
                        busy -> "Downloading…"
                        else -> "Download offline"
                    },
                    if (saved) G.Trash else G.Download,
                ) {
                    if (busy) return@SheetAction
                    if (saved) {
                        Downloads.remove(track.id)
                        saved = false
                        onToast("Download removed")
                    } else {
                        busy = true
                        scope.launch {
                            val result = Downloads.download(track)
                            busy = false
                            if (result.isSuccess) {
                                saved = true
                                onToast("Saved offline")
                            } else {
                                onToast(result.exceptionOrNull()?.message ?: "Download failed")
                            }
                        }
                    }
                }
                SheetAction("Share", G.Share) {
                    val text = "${track.title} — ${track.artist}\nhttps://music.youtube.com/watch?v=${track.videoId}"
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, "Share track"))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSheet(onDismiss: () -> Unit, onPick: (Int) -> Unit, onEndOfTrack: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF17131E),
        contentColor = Ink.Text,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "Sleep timer",
                color = Ink.Text,
                fontSize = 16.sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            listOf(5, 10, 15, 30, 45, 60).forEach { m ->
                SheetAction("$m minutes", G.Clock) {
                    onPick(m)
                    onDismiss()
                }
            }
            SheetAction("End of current track", G.Moon) {
                onEndOfTrack()
                onDismiss()
            }
            SheetAction("Turn off", G.Close) {
                onPick(0)
                onDismiss()
            }
        }
    }
}

@Composable
fun SheetHandle() {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 38.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.18f))
        )
    }
}

@Composable
fun SheetAction(label: String, glyph: G? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (glyph != null) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) { Glyph(glyph, Ink.Text, 16.dp) }
            Spacer(Modifier.width(14.dp))
        }
        Text(label, color = Ink.Text, fontSize = 13.5.sp, fontFamily = SoraFamily)
    }
}
