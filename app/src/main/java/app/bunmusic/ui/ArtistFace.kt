package app.bunmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.ArtistMark
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Track
import app.bunmusic.data.YtMusic
import coil3.compose.AsyncImage

/**
 * The artist's face, in the smallest space that still reads as a person.
 *
 * Before 2.14.0 the player drew the artist as a bare line of text: the model
 * carried the song's cover and a name, nothing that could become a portrait.
 * This is that missing face — a real photo when one is known, and otherwise a
 * monogram whose colour is derived from the name, so the circle is never blank
 * and the same artist is always the same colour.
 *
 * The portrait is resolved lazily and only when the track carries a channel id;
 * a track with none never triggers a request, which keeps offline playback and
 * the screenshot tests free of network work.
 */
@Composable
fun ArtistAvatar(
    name: String,
    artistId: String?,
    artUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    var url by remember(artistId, artUrl) { mutableStateOf(artUrl) }
    LaunchedEffect(artistId, artUrl) {
        if (url == null && artistId != null) {
            url = runCatching { YtMusic.artistArt(artistId) }.getOrNull()
        }
    }

    val disc = Color(ArtistMark.argb(name))
    Box(
        modifier.size(size).clip(CircleShape).background(disc),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = ArtistMark.initials(name),
                color = Color(ArtistMark.ink(name)),
                fontSize = (size.value * 0.40f).sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Avatar + name, the way the artist is credited under a song.
 *
 * Used by both player layouts so the two can never drift into different
 * typography or a different tap target. `onClick` is optional; when it is null
 * the line is inert text, exactly as before.
 */
@Composable
fun ArtistLine(
    track: Track,
    color: Color,
    size: Int,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clip(RoundedCornerShape(50)).clickable { onClick() }
            } else {
                Modifier
            }
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistAvatar(
            name = track.artist,
            artistId = track.artistId,
            artUrl = track.artistArtUrl,
            size = avatarSize,
        )
        Spacer(Modifier.width(7.dp))
        MarqueeText(
            text = track.artist,
            color = color,
            size = size,
            family = SoraFamily,
            weight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}
