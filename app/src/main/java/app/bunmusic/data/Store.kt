package app.bunmusic.data

import android.content.Context
import android.content.SharedPreferences

/** Tiny on-device store for likes, recents and the play counter. */
object Store {

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("bunmusic", Context.MODE_PRIVATE)
    }

    private const val KEY_LIKES = "likes"
    private const val KEY_RECENT = "recent"

    fun likes(): Set<String> = prefs.getStringSet(KEY_LIKES, emptySet()) ?: emptySet()

    fun isLiked(id: String): Boolean = likes().contains(id)

    fun toggleLike(id: String): Boolean {
        val current = likes().toMutableSet()
        val nowLiked = if (current.contains(id)) {
            current.remove(id); false
        } else {
            current.add(id); true
        }
        prefs.edit().putStringSet(KEY_LIKES, current).apply()
        return nowLiked
    }

    fun recent(): List<Track> {
        val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return raw.split("\n").mapNotNull { decode(it) }
    }

    fun pushRecent(track: Track) {
        val list = recent().filterNot { it.id == track.id }.toMutableList()
        list.add(0, track)
        while (list.size > 20) list.removeAt(list.size - 1)
        prefs.edit().putString(KEY_RECENT, list.joinToString("\n") { encode(it) }).apply()
    }

    private fun encode(t: Track): String = listOf(
        t.id, t.title, t.artist, t.album, t.artUrl ?: "", t.artAsset ?: "",
        t.durationMs.toString(), t.source.name, t.videoId, t.assetAudio ?: "",
    ).joinToString("\u0001")

    private fun decode(s: String): Track? {
        val p = s.split("\u0001")
        if (p.size < 10) return null
        return Track(
            id = p[0],
            title = p[1],
            artist = p[2],
            album = p[3],
            artUrl = p[4].ifBlank { null },
            artAsset = p[5].ifBlank { null },
            durationMs = p[6].toLongOrNull() ?: 0L,
            source = if (p[7] == Source.LOCAL.name) Source.LOCAL else Source.YOUTUBE,
            videoId = p[8],
            assetAudio = p[9].ifBlank { null },
        )
    }
}
