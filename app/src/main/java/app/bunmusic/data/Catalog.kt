package app.bunmusic.data

/**
 * Bundled catalogue. Everything here ships inside the APK: six original
 * instrumental pieces rendered for BunMusic plus their cover art.
 */
object Catalog {

    private fun local(
        slug: String,
        title: String,
        artist: String,
        album: String,
        seconds: Double,
        trackNo: Int,
    ) = Track(
        id = "local:$slug",
        title = title,
        artist = artist,
        album = album,
        artAsset = "art/$slug.png",
        artUrl = null,
        durationMs = (seconds * 1000).toLong(),
        source = Source.LOCAL,
        videoId = "",
        assetAudio = "audio/$slug.ogg",
    )

    val tracks: List<Track> = listOf(
        local("starlit_reverie", "Starlit Reverie", "Aria Vale", "Nocturne Sessions", 27.66, 1),
        local("midnight_confessions", "Midnight Confessions", "Noor", "After Hours", 31.0, 2),
        local("lost_in_the_echo", "Lost in the Echo", "Kaya", "Paper Rooms", 25.0, 3),
        local("letters_i_never_sent", "Letters I Never Sent", "Aria Vale", "Nocturne Sessions", 34.1, 4),
        local("breaking_the_silence", "Breaking the Silence", "Ren", "Static Bloom", 22.8, 5),
        local("tears_on_the_vinyl", "Tears on the Vinyl", "Noor", "After Hours", 30.1, 6),
    )

    val byId: Map<String, Track> = tracks.associateBy { it.id }

    val discoverWeekly = Collection(
        id = "local:discover",
        title = "Discover Weekly",
        subtitle = "Fresh finds, hand-picked",
        artAsset = "art/discover_weekly.png",
        kind = CollectionKind.PLAYLIST,
    )

    val newRelease = Collection(
        id = "local:new",
        title = "New Releases",
        subtitle = "Out this week",
        artAsset = "art/new_release.png",
        kind = CollectionKind.PLAYLIST,
    )

    val lateNight = Collection(
        id = "local:late",
        title = "Late Night Drive",
        subtitle = "Slow lanes, soft light",
        artAsset = "art/tears_on_the_vinyl.png",
        kind = CollectionKind.PLAYLIST,
    )

    val offlineMix = Collection(
        id = "local:offline",
        title = "Offline Mix",
        subtitle = "No signal, no problem",
        artAsset = "art/midnight_confessions.png",
        kind = CollectionKind.PLAYLIST,
    )

    val collections: List<Collection> = listOf(discoverWeekly, newRelease, lateNight, offlineMix)

    fun tracksFor(collectionId: String): List<Track> = when (collectionId) {
        "local:discover" -> listOf(tracks[0], tracks[2], tracks[4], tracks[1], tracks[5], tracks[3])
        "local:new" -> listOf(tracks[1], tracks[5], tracks[0])
        "local:late" -> listOf(tracks[5], tracks[1], tracks[3])
        "local:offline" -> tracks
        else -> tracks
    }

    fun collectionById(id: String): Collection? = collections.firstOrNull { it.id == id }

    val hero: Track get() = tracks[0]
}
