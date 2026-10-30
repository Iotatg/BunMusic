package app.bunmusic.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/**
 * On-device library: liked songs, playlists, history and listening stats.
 *
 * Everything is a plain string in SharedPreferences — no database, no migration
 * headaches, and it survives app updates. Track metadata is kept in a small
 * registry so playlists and likes only have to store ids.
 */
object Store {

    private const val F = '\u0001'
    private const val R = '\n'
    private const val L = '\u0003'

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("bunmusic", Context.MODE_PRIVATE)
    }

    // ---------------------------------------------------------------- registry

    private const val KEY_REG = "registry"
    private const val KEY_LIKES = "liked_ids"
    private const val KEY_LEGACY_LIKES = "likes"
    private const val KEY_HISTORY = "history"
    private const val KEY_RECENT = "recent"
    private const val KEY_PLAYLISTS = "playlists"
    private const val KEY_TOTAL_MS = "stats_total_ms"
    private const val KEY_PLAYS = "stats_plays"
    private const val KEY_ARTISTS = "stats_artists"
    private const val KEY_TRACKS = "stats_tracks"
    private const val KEY_DAYS = "stats_days"

    private fun regRaw(): String = prefs.getString(KEY_REG, null) ?: ""

    private fun registry(): LinkedHashMap<String, Track> {
        val map = LinkedHashMap<String, Track>()
        for (line in regRaw().split(R)) {
            val t = decodeTrack(line) ?: continue
            map[t.id] = t
        }
        return map
    }

    @Synchronized
    fun remember(track: Track) {
        val map = registry()
        if (map[track.id] == track) return
        map[track.id] = track
        while (map.size > 600) {
            val first = map.keys.first()
            map.remove(first)
        }
        prefs.edit().putString(KEY_REG, map.values.joinToString(R.toString()) { encodeTrack(it) }).apply()
    }

    fun track(id: String): Track? = registry()[id]

    // ------------------------------------------------------------------ likes

    private fun likeIds(): List<String> {
        val raw = prefs.getString(KEY_LIKES, null)
        if (raw != null) return raw.split(R).filter { it.isNotBlank() }
        // migrate the 1.0.0 set-based storage
        val legacy = prefs.getStringSet(KEY_LEGACY_LIKES, emptySet()) ?: emptySet()
        return legacy.toList()
    }

    fun likes(): Set<String> = likeIds().toSet()

    fun isLiked(id: String): Boolean = likeIds().contains(id)

    @Synchronized
    fun toggleLike(id: String): Boolean {
        val ids = likeIds().toMutableList()
        val liked = if (ids.contains(id)) {
            ids.remove(id); false
        } else {
            ids.add(0, id); true
        }
        prefs.edit().putString(KEY_LIKES, ids.joinToString(R.toString())).apply()
        return liked
    }

    fun toggleLike(track: Track): Boolean {
        remember(track)
        return toggleLike(track.id)
    }

    fun likedTracks(): List<Track> {
        val map = registry()
        return likeIds().mapNotNull { map[it] }
    }

    // ---------------------------------------------------------------- history

    fun history(): List<HistoryItem> {
        val map = registry()
        val out = ArrayList<HistoryItem>()
        for (line in (prefs.getString(KEY_HISTORY, null) ?: "").split(R)) {
            val p = line.split(F)
            if (p.size < 2) continue
            val t = map[p[0]] ?: continue
            out.add(HistoryItem(t, p[1].toLongOrNull() ?: 0L))
        }
        return out
    }

    fun recent(limit: Int = 40): List<Track> {
        val h = history()
        if (h.isEmpty()) {
            // 1.0.0 migration
            val legacy = prefs.getString(KEY_RECENT, null) ?: return emptyList()
            return legacy.split(R).mapNotNull { decodeTrack(it) }.take(limit)
        }
        return h.map { it.track }.distinctBy { it.id }.take(limit)
    }

    @Synchronized
    fun pushRecent(track: Track) {
        remember(track)
        val now = System.currentTimeMillis()
        val items = history().filterNot { it.track.id == track.id }.toMutableList()
        items.add(0, HistoryItem(track, now))
        while (items.size > 120) items.removeAt(items.size - 1)
        prefs.edit()
            .putString(KEY_HISTORY, items.joinToString(R.toString()) { "${it.track.id}$F${it.atMs}" })
            .apply()
    }

    @Synchronized
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).remove(KEY_RECENT).apply()
    }

    // -------------------------------------------------------------- playlists

    fun playlists(): List<Playlist> {
        val out = ArrayList<Playlist>()
        for (line in (prefs.getString(KEY_PLAYLISTS, null) ?: "").split(R)) {
            val p = line.split(F)
            if (p.size < 4) continue
            out.add(
                Playlist(
                    id = p[0],
                    name = p[1],
                    createdAt = p[2].toLongOrNull() ?: 0L,
                    trackIds = p[3].split(L).filter { it.isNotBlank() },
                )
            )
        }
        return out.sortedByDescending { it.createdAt }
    }

    fun playlist(id: String): Playlist? = playlists().firstOrNull { it.id == id }

    private fun writePlaylists(list: List<Playlist>) {
        prefs.edit().putString(
            KEY_PLAYLISTS,
            list.joinToString(R.toString()) { "${it.id}$F${it.name}$F${it.createdAt}$F${it.trackIds.joinToString(L.toString())}" },
        ).apply()
    }

    @Synchronized
    fun createPlaylist(name: String): Playlist {
        val p = Playlist(
            id = "pl_" + System.currentTimeMillis().toString(36),
            name = name.trim().ifBlank { "New playlist" },
            createdAt = System.currentTimeMillis(),
        )
        writePlaylists(playlists() + p)
        return p
    }

    @Synchronized
    fun renamePlaylist(id: String, name: String) {
        writePlaylists(playlists().map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    @Synchronized
    fun deletePlaylist(id: String) {
        writePlaylists(playlists().filterNot { it.id == id })
    }

    @Synchronized
    fun addToPlaylist(playlistId: String, track: Track): Boolean {
        remember(track)
        var added = false
        writePlaylists(
            playlists().map {
                if (it.id == playlistId) {
                    if (it.trackIds.contains(track.id)) it
                    else { added = true; it.copy(trackIds = it.trackIds + track.id) }
                } else it
            }
        )
        return added
    }

    @Synchronized
    fun removeFromPlaylist(playlistId: String, trackId: String) {
        writePlaylists(
            playlists().map {
                if (it.id == playlistId) it.copy(trackIds = it.trackIds.filterNot { t -> t == trackId }) else it
            }
        )
    }

    @Synchronized
    fun moveInPlaylist(playlistId: String, from: Int, to: Int) {
        writePlaylists(
            playlists().map { pl ->
                if (pl.id != playlistId) return@map pl
                val ids = pl.trackIds.toMutableList()
                if (from !in ids.indices || to !in ids.indices) return@map pl
                val item = ids.removeAt(from)
                ids.add(to, item)
                pl.copy(trackIds = ids)
            }
        )
    }

    fun playlistTracks(playlistId: String): List<Track> {
        val map = registry()
        return playlist(playlistId)?.trackIds?.mapNotNull { map[it] } ?: emptyList()
    }

    // --------------------------------------------------------- search history

    private const val KEY_QUERIES = "queries"

    fun queries(): List<String> =
        (prefs.getString(KEY_QUERIES, null) ?: "").split(R).filter { it.isNotBlank() }.take(10)

    @Synchronized
    fun pushQuery(q: String) {
        val text = q.trim()
        if (text.isBlank()) return
        val list = queries().filterNot { it.equals(text, true) }.toMutableList()
        list.add(0, text)
        prefs.edit().putString(KEY_QUERIES, list.take(10).joinToString(R.toString())).apply()
    }

    fun clearQueries() {
        prefs.edit().remove(KEY_QUERIES).apply()
    }

    // ------------------------------------------------------------------ stats
    @Synchronized
    fun recordPlay(track: Track) {
        remember(track)
        val plays = prefs.getInt(KEY_PLAYS, 0) + 1
        val totalMs = prefs.getLong(KEY_TOTAL_MS, 0L) + track.durationMs.coerceAtLeast(0L)

        val artists = counter(KEY_ARTISTS).toMutableMap()
        artists[track.artist] = (artists[track.artist] ?: 0) + 1
        val tracks = counter(KEY_TRACKS).toMutableMap()
        tracks[track.title] = (tracks[track.title] ?: 0) + 1

        val days = (prefs.getStringSet(KEY_DAYS, emptySet()) ?: emptySet()).toMutableSet()
        days.add(epochDay().toString())

        prefs.edit()
            .putInt(KEY_PLAYS, plays)
            .putLong(KEY_TOTAL_MS, totalMs)
            .putString(KEY_ARTISTS, writeCounter(artists))
            .putString(KEY_TRACKS, writeCounter(tracks))
            .putStringSet(KEY_DAYS, days)
            .apply()
    }

    fun stats(): LibraryStats {
        val days = prefs.getStringSet(KEY_DAYS, emptySet()) ?: emptySet()
        return LibraryStats(
            totalMs = prefs.getLong(KEY_TOTAL_MS, 0L),
            plays = prefs.getInt(KEY_PLAYS, 0),
            topArtists = counter(KEY_ARTISTS).entries.sortedByDescending { it.value }.take(6).map { it.key to it.value },
            topTracks = counter(KEY_TRACKS).entries.sortedByDescending { it.value }.take(6).map { it.key to it.value },
            days = days.size,
            week = weekBars(),
        )
    }

    private fun weekBars(): List<Int> {
        val counts = IntArray(7)
        val cal = Calendar.getInstance()
        val today = epochDay()
        for (h in history()) {
            val day = h.atMs / 86_400_000L
            val diff = (today - day).toInt()
            if (diff in 0..6) counts[6 - diff] += 1
        }
        return counts.toList()
    }

    private fun epochDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 86_400_000L
    }

    private fun counter(key: String): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        for (line in (prefs.getString(key, null) ?: "").split(R)) {
            val p = line.split(F)
            if (p.size < 2) continue
            out[p[0]] = p[1].toIntOrNull() ?: 0
        }
        return out
    }

    private fun writeCounter(map: Map<String, Int>): String =
        map.entries.sortedByDescending { it.value }.joinToString(R.toString()) { "${it.key}$F${it.value}" }

    // ---------------------------------------------------------------- codec

    fun encodeTrack(t: Track): String = listOf(
        t.id, t.title, t.artist, t.album, t.artUrl ?: "", t.artAsset ?: "",
        t.durationMs.toString(), t.source.name, t.videoId, t.assetAudio ?: "",
    ).joinToString(F.toString())

    fun decodeTrack(s: String): Track? {
        val p = s.split(F)
        if (p.size < 10) return null
        if (p[1].isBlank()) return null
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
