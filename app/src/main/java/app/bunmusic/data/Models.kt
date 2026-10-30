package app.bunmusic.data

enum class Source { LOCAL, YOUTUBE }

/** A playable item, either bundled in the app or streamed from YouTube Music. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val artUrl: String? = null,
    val artAsset: String? = null,
    val durationMs: Long = 0L,
    val source: Source = Source.YOUTUBE,
    val videoId: String = "",
    val assetAudio: String? = null,
) {
    val isLocal: Boolean get() = source == Source.LOCAL

    fun uri(): String = if (isLocal) "asset:///${assetAudio ?: ""}" else "yt://$videoId"
}

enum class CollectionKind { ALBUM, ARTIST, PLAYLIST, MIX }

data class Collection(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val artUrl: String? = null,
    val artAsset: String? = null,
    val kind: CollectionKind = CollectionKind.PLAYLIST,
    val browseId: String = "",
    val round: Boolean = false,
)

data class Shelf(
    val title: String,
    val items: List<Collection> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val wide: Boolean = false,
)

data class SearchResults(
    val songs: List<Track> = emptyList(),
    val albums: List<Collection> = emptyList(),
    val artists: List<Collection> = emptyList(),
    val playlists: List<Collection> = emptyList(),
) {
    val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

data class CollectionDetail(
    val collection: Collection,
    val tracks: List<Track> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
    val description: String = "",
)

data class LyricLine(val atMs: Long, val text: String)

data class Lyrics(val lines: List<LyricLine> = emptyList(), val plain: String? = null) {
    val synced: Boolean get() = lines.isNotEmpty()
    val isEmpty: Boolean get() = lines.isEmpty() && plain.isNullOrBlank()
}
