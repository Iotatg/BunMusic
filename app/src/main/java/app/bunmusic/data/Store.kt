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

    /**
     * Library namespace. Empty means "this device, no account" — the guest shelf.
     * Every key is written through [k] so signing in swaps the whole library view
     * without touching a single call site.
     */
    private var scope: String = ""

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("bunmusic", Context.MODE_PRIVATE)
    }

    private fun k(key: String): String = if (scope.isEmpty()) key else "$scope/$key"

    private fun scopePrefix(id: String) = "acct_$id"

    private val SCOPED_KEYS = listOf(
        KEY_REG, KEY_LIKES, KEY_HISTORY, KEY_RECENT, KEY_PLAYLISTS,
        KEY_TOTAL_MS, KEY_PLAYS, KEY_ARTISTS, KEY_TRACKS, KEY_DAYS, KEY_QUERIES,
        KEY_DISLIKE, KEY_FOLLOWS, KEY_PLAY_IDS, KEY_EVENTS, KEY_PINNED, KEY_NOTES,
        KEY_SKIPS,
    )

    /**
     * Point the store at an account's slice of the library.
     *
     * The first time an account is used on a device that already has guest data, the
     * guest shelf is adopted so nobody loses their playlists by signing up.
     */
    @Synchronized
    fun attach(accountId: String?) {
        if (!::prefs.isInitialized) return
        val next = if (accountId.isNullOrBlank()) "" else scopePrefix(accountId)
        if (next == scope) return
        val hadGuestData = prefs.contains(KEY_LIKES) || prefs.contains(KEY_PLAYLISTS) || prefs.contains(KEY_HISTORY)
        val accountHasData = next.isNotEmpty() && (
            prefs.contains("$next/$KEY_LIKES") || prefs.contains("$next/$KEY_PLAYLISTS") || prefs.contains("$next/$KEY_HISTORY")
            )
        scope = next
        if (next.isNotEmpty() && hadGuestData && !accountHasData) copyScope("", next)
    }

    fun currentScope(): String = scope

    /** Remove everything an account owned. Called when the account is deleted. */
    @Synchronized
    fun dropScope(accountId: String) {
        val prefix = scopePrefix(accountId)
        val editor = prefs.edit()
        SCOPED_KEYS.forEach { editor.remove("$prefix/$it") }
        editor.apply()
        if (scope == prefix) scope = ""
    }

    private fun copyScope(from: String, to: String) {
        val editor = prefs.edit()
        for (key in SCOPED_KEYS) {
            val src = if (from.isEmpty()) key else "$from/$key"
            val dst = if (to.isEmpty()) key else "$to/$key"
            if (!prefs.contains(src) || prefs.contains(dst)) continue
            when (val value = prefs.all[src]) {
                is String -> editor.putString(dst, value)
                is Int -> editor.putInt(dst, value)
                is Long -> editor.putLong(dst, value)
                is Boolean -> editor.putBoolean(dst, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(dst, value as Set<String>)
                }
            }
        }
        editor.apply()
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
    private const val KEY_PLAY_IDS = "stats_play_ids"
    private const val KEY_EVENTS = "play_events"
    private const val KEY_DISLIKE = "disliked_ids"
    private const val KEY_FOLLOWS = "followed_artists"
    private const val KEY_PINNED = "pinned_playlists"
    private const val KEY_NOTES = "playlist_notes"
    private const val KEY_SKIPS = "stats_skips"

    private fun regRaw(): String = prefs.getString(k(KEY_REG), null) ?: ""

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
        prefs.edit().putString(k(KEY_REG), map.values.joinToString(R.toString()) { encodeTrack(it) }).apply()
    }

    fun track(id: String): Track? = registry()[id]

    /** Everything the registry knows, most recently remembered first. */
    fun allTracks(): List<Track> = registry().values.toList().asReversed()

    // ------------------------------------------------------------------ likes

    private fun likeIds(): List<String> {
        val raw = prefs.getString(k(KEY_LIKES), null)
        if (raw != null) return raw.split(R).filter { it.isNotBlank() }
        // migrate the 1.0.0 set-based storage
        val legacy = prefs.getStringSet(k(KEY_LEGACY_LIKES), emptySet()) ?: emptySet()
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
        prefs.edit().putString(k(KEY_LIKES), ids.joinToString(R.toString())).apply()
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

    /**
     * Like or unlike a whole selection at once.
     *
     * The old single-track path rewrote the list once per track, which for a
     * fifty-song selection meant fifty writes and fifty full re-reads. This
     * builds the result once.
     */
    @Synchronized
    fun likeMany(ids: List<String>, liked: Boolean) {
        if (ids.isEmpty()) return
        val current = likeIds().toMutableList()
        if (liked) {
            ids.reversed().forEach { if (!current.contains(it)) current.add(0, it) }
        } else {
            current.removeAll(ids.toSet())
        }
        prefs.edit().putString(k(KEY_LIKES), current.joinToString(R.toString())).apply()
    }

    // --------------------------------------------------------------- dislikes

    /**
     * "Not for me".
     *
     * Kept apart from the likes list rather than as a third state on it: a dislike
     * must never be able to eat a like, and the two lists are read by different
     * screens. Disliking a liked track unlikes it, because both at once is a
     * contradiction the radio has no answer for.
     */
    private fun dislikeIds(): List<String> =
        (prefs.getString(k(KEY_DISLIKE), null) ?: "").split(R).filter { it.isNotBlank() }

    fun dislikes(): Set<String> = dislikeIds().toSet()

    fun isDisliked(id: String): Boolean = dislikeIds().contains(id)

    @Synchronized
    fun toggleDislike(track: Track): Boolean {
        remember(track)
        val ids = dislikeIds().toMutableList()
        val now = if (ids.contains(track.id)) {
            ids.remove(track.id); false
        } else {
            ids.add(0, track.id)
            if (likeIds().contains(track.id)) {
                prefs.edit().putString(
                    k(KEY_LIKES),
                    likeIds().filterNot { it == track.id }.joinToString(R.toString()),
                ).apply()
            }
            true
        }
        prefs.edit().putString(k(KEY_DISLIKE), ids.joinToString(R.toString())).apply()
        return now
    }

    // ---------------------------------------------------------------- follows

    fun follows(): Set<String> =
        (prefs.getString(k(KEY_FOLLOWS), null) ?: "").split(R).filter { it.isNotBlank() }.toSet()

    fun isFollowing(artist: String): Boolean = follows().contains(artist.trim())

    @Synchronized
    fun toggleFollow(artist: String): Boolean {
        val name = artist.trim()
        if (name.isEmpty()) return false
        val set = follows().toMutableSet()
        val now = if (!set.add(name)) { set.remove(name); false } else true
        prefs.edit().putString(k(KEY_FOLLOWS), set.joinToString(R.toString())).apply()
        return now
    }

    // ---------------------------------------------------------------- history

    fun history(): List<HistoryItem> {
        val map = registry()
        val out = ArrayList<HistoryItem>()
        for (line in (prefs.getString(k(KEY_HISTORY), null) ?: "").split(R)) {
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
            val legacy = prefs.getString(k(KEY_RECENT), null) ?: return emptyList()
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
            .putString(k(KEY_HISTORY), items.joinToString(R.toString()) { "${it.track.id}$F${it.atMs}" })
            .apply()
    }

    @Synchronized
    fun clearHistory() {
        prefs.edit()
            .remove(k(KEY_HISTORY))
            .remove(k(KEY_RECENT))
            // The log is history too. Leaving it behind would let the year in
            // review keep showing plays the user just asked to forget.
            .remove(k(KEY_EVENTS))
            .apply()
    }

    // -------------------------------------------------------------- playlists

    fun playlists(): List<Playlist> {
        val out = ArrayList<Playlist>()
        for (line in (prefs.getString(k(KEY_PLAYLISTS), null) ?: "").split(R)) {
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
            k(KEY_PLAYLISTS),
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

    /** Add a whole selection in one write — see [likeMany] for why. */
    @Synchronized
    fun addManyToPlaylist(playlistId: String, tracks: List<Track>): Int {
        if (tracks.isEmpty()) return 0
        tracks.forEach { remember(it) }
        var added = 0
        writePlaylists(
            playlists().map { pl ->
                if (pl.id != playlistId) return@map pl
                val ids = pl.trackIds.toMutableList()
                tracks.forEach { if (ids.add(it.id)) added++ }
                pl.copy(trackIds = ids)
            }
        )
        return added
    }

    fun playlistTracks(playlistId: String): List<Track> {
        val map = registry()
        return playlist(playlistId)?.trackIds?.mapNotNull { map[it] } ?: emptyList()
    }

    // ------------------------------------------------------ pins and notes

    /**
     * Pinned playlists sit above the fold in the library.
     *
     * Stored as ids, not positions: a pin is a property of the playlist, and a
     * reorder of the list must not silently move somebody's favourites.
     */
    fun pins(): Set<String> =
        (prefs.getString(k(KEY_PINNED), null) ?: "").split(R).filter { it.isNotBlank() }.toSet()

    fun isPinned(playlistId: String): Boolean = pins().contains(playlistId)

    @Synchronized
    fun togglePin(playlistId: String): Boolean {
        val now = pins()
        val next = if (now.contains(playlistId)) now - playlistId else now + playlistId
        prefs.edit().putString(k(KEY_PINNED), next.joinToString(R.toString())).apply()
        return next.contains(playlistId)
    }

    /** Free-text note attached to a playlist. Empty removes it. */
    fun note(playlistId: String): String {
        val raw = prefs.getString(k(KEY_NOTES), null) ?: return ""
        for (line in raw.split(R)) {
            val at = line.indexOf(F)
            if (at > 0 && line.substring(0, at) == playlistId) return line.substring(at + 1)
        }
        return ""
    }

    @Synchronized
    fun setNote(playlistId: String, text: String) {
        val kept = (prefs.getString(k(KEY_NOTES), null) ?: "")
            .split(R)
            .filter { it.isNotBlank() && it.substringBefore(F) != playlistId }
        val next = if (text.isBlank()) kept else kept + "$playlistId$F${text.replace('\n', ' ')}"
        prefs.edit().putString(k(KEY_NOTES), next.joinToString(R.toString())).apply()
    }

    // ---------------------------------------------------------- search history

    private const val KEY_QUERIES = "queries"

    fun queries(): List<String> =
        (prefs.getString(k(KEY_QUERIES), null) ?: "").split(R).filter { it.isNotBlank() }.take(10)

    @Synchronized
    fun pushQuery(q: String) {
        val text = q.trim()
        if (text.isBlank()) return
        val list = queries().filterNot { it.equals(text, true) }.toMutableList()
        list.add(0, text)
        prefs.edit().putString(k(KEY_QUERIES), list.take(10).joinToString(R.toString())).apply()
    }

    fun clearQueries() {
        prefs.edit().remove(k(KEY_QUERIES)).apply()
    }

    // ------------------------------------------------------------------ stats
    @Synchronized
    fun recordPlay(track: Track) {
        remember(track)
        val plays = prefs.getInt(k(KEY_PLAYS), 0) + 1
        val totalMs = prefs.getLong(k(KEY_TOTAL_MS), 0L) + track.durationMs.coerceAtLeast(0L)

        val artists = counter(KEY_ARTISTS).toMutableMap()
        artists[track.artist] = (artists[track.artist] ?: 0) + 1
        val tracks = counter(KEY_TRACKS).toMutableMap()
        tracks[track.title] = (tracks[track.title] ?: 0) + 1

        // Counted by id as well as by title: the auto playlists need to look a
        // track up again, and a title is not a key.
        val byId = counter(KEY_PLAY_IDS).toMutableMap()
        byId[track.id] = (byId[track.id] ?: 0) + 1

        val days = (prefs.getStringSet(k(KEY_DAYS), emptySet()) ?: emptySet()).toMutableSet()
        days.add(epochDay().toString())

        // The counters above answer "top six of all time". The log below answers
        // "March" and "2 am", which is what the period charts and the year in
        // review need. Both are written here so they cannot drift apart.
        val log = Listening.append(events(), Listening.Event(track.id, System.currentTimeMillis(), track.durationMs))

        prefs.edit()
            .putInt(k(KEY_PLAYS), plays)
            .putLong(k(KEY_TOTAL_MS), totalMs)
            .putString(k(KEY_ARTISTS), writeCounter(artists))
            .putString(k(KEY_TRACKS), writeCounter(tracks))
            .putString(k(KEY_PLAY_IDS), writeCounter(byId))
            .putString(k(KEY_EVENTS), log.joinToString(R.toString()) { Listening.encode(it) })
            .putStringSet(k(KEY_DAYS), days)
            .apply()
    }

    /**
     * A skip is not a play.
     *
     * Counted separately so smart shuffle can learn what to stop offering
     * without corrupting the play counts, the charts or the year in review.
     */
    @Synchronized
    fun recordSkip(track: Track) {
        val map = counter(KEY_SKIPS).toMutableMap()
        map[track.id] = (map[track.id] ?: 0) + 1
        prefs.edit().putString(k(KEY_SKIPS), writeCounter(map)).apply()
    }

    fun skips(): Map<String, Int> = counter(KEY_SKIPS)

    /** The raw play log, newest first. See [Listening] for the arithmetic over it. */
    fun events(): List<Listening.Event> =
        (prefs.getString(k(KEY_EVENTS), null) ?: "")
            .split(R)
            .mapNotNull { Listening.decode(it) }

    fun stats(): LibraryStats {
        val days = prefs.getStringSet(k(KEY_DAYS), emptySet()) ?: emptySet()
        return LibraryStats(
            totalMs = prefs.getLong(k(KEY_TOTAL_MS), 0L),
            plays = prefs.getInt(k(KEY_PLAYS), 0),
            topArtists = counter(KEY_ARTISTS).entries.sortedByDescending { it.value }.take(6).map { it.key to it.value },
            topTracks = counter(KEY_TRACKS).entries.sortedByDescending { it.value }.take(6).map { it.key to it.value },
            days = days.size,
            week = weekBars(),
        )
    }

    /** track id -> times played, for the auto playlists. */
    fun playCounts(): Map<String, Int> = counter(KEY_PLAY_IDS)

    fun lastPlayedAt(id: String): Long = history().firstOrNull { it.track.id == id }?.atMs ?: 0L

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
        for (line in (prefs.getString(k(key), null) ?: "").split(R)) {
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
