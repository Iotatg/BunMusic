package app.bunmusic.data

enum class Source { LOCAL, YOUTUBE }

/** A playable item, either bundled in the app, downloaded, or streamed from YouTube Music. */
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

data class Lyrics(
    val lines: List<LyricLine> = emptyList(),
    val plain: String? = null,
    val provider: String = "",
) {
    val synced: Boolean get() = lines.isNotEmpty()
    val isEmpty: Boolean get() = lines.isEmpty() && plain.isNullOrBlank()
}

// ------------------------------------------------------------------ library

/** A playlist the listener built on this device. */
data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
    val createdAt: Long = 0L,
) {
    val size: Int get() = trackIds.size
}

data class HistoryItem(val track: Track, val atMs: Long)

/** A mood / genre tile from YouTube Music's Explore page. */
data class Mood(
    val title: String,
    val browseId: String,
    val params: String,
    val accent: Long = 0L,
)

data class ExplorePage(
    val newReleases: List<Collection> = emptyList(),
    val moods: List<Mood> = emptyList(),
    val trending: List<Track> = emptyList(),
)

/** Aggregated listening numbers for the stats screen. */
data class LibraryStats(
    val totalMs: Long = 0L,
    val plays: Int = 0,
    val topArtists: List<Pair<String, Int>> = emptyList(),
    val topTracks: List<Pair<String, Int>> = emptyList(),
    val days: Int = 0,
    val week: List<Int> = emptyList(),
)
