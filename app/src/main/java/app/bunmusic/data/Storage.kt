package app.bunmusic.data

/**
 * Where the megabytes went.
 *
 * A music app accumulates four kinds of bytes and only one of them is precious:
 * downloads you asked for, streams it cached while you listened, artwork it
 * fetched to draw a 48 dp square, and the library itself. The settings screen
 * used to show a single number and a "clear cache" button, which is the storage
 * equivalent of "your car is making a noise".
 */
object Storage {

    enum class Action { NONE, CLEAR_STREAM, CLEAR_IMAGES, CLEAR_DOWNLOADS }

    data class Bucket(
        val id: String,
        val label: String,
        val bytes: Long,
        val note: String,
        val action: Action,
        val removable: Boolean,
    )

    fun buckets(
        downloadsBytes: Long,
        downloadCount: Int,
        streamBytes: Long,
        imageBytes: Long,
        libraryBytes: Long,
    ): List<Bucket> = listOf(
        Bucket(
            "downloads", "Downloads", downloadsBytes,
            if (downloadCount == 0) "Nothing saved for offline" else "$downloadCount tracks you chose to keep",
            Action.CLEAR_DOWNLOADS, removable = false,
        ),
        Bucket(
            "streams", "Stream cache", streamBytes,
            "Songs heard online, kept so a replay is instant",
            Action.CLEAR_STREAM, removable = true,
        ),
        Bucket(
            "images", "Artwork", imageBytes,
            "Covers, cropped and cached for the lists",
            Action.CLEAR_IMAGES, removable = true,
        ),
        Bucket(
            "library", "Library", libraryBytes,
            "Likes, playlists, history and the play log",
            Action.NONE, removable = false,
        ),
    )

    fun total(buckets: List<Bucket>): Long = buckets.sumOf { it.bytes }

    /** Cache first, artwork second — downloads are never in the plan. */
    fun cleanupPlan(buckets: List<Bucket>, limitBytes: Long): List<Bucket> {
        var over = total(buckets) - limitBytes
        if (over <= 0) return emptyList()
        val out = ArrayList<Bucket>()
        buckets.filter { it.removable }
            .sortedByDescending { it.bytes }
            .forEach { b ->
                if (over <= 0) return@forEach
                if (b.bytes <= 0) return@forEach
                out += b
                over -= b.bytes
            }
        return out
    }

    fun verdict(totalBytes: Long, limitBytes: Long): String = when {
        totalBytes <= 0 -> "Nothing stored yet"
        totalBytes <= limitBytes -> "${human(totalBytes)} used · ${human(limitBytes - totalBytes)} under your limit"
        else -> "${human(totalBytes - limitBytes)} over your limit"
    }

    fun human(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format("%.0f MB", bytes / 1_048_576.0)
        bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
