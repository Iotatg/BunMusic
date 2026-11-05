package app.bunmusic

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.bunmusic.core.BunMotion
import app.bunmusic.core.BunSkin
import app.bunmusic.core.BunTheme
import app.bunmusic.core.BunWindowProvider
import app.bunmusic.core.Ink
import app.bunmusic.core.Skin
import app.bunmusic.data.Collection
import app.bunmusic.data.Mood
import app.bunmusic.data.Prefs
import app.bunmusic.data.Store
import app.bunmusic.data.Track
import app.bunmusic.player.PlayerState
import app.bunmusic.ui.AboutScreen
import app.bunmusic.ui.AuthScreen
import app.bunmusic.ui.BunsScreen
import app.bunmusic.ui.DiscoveryScreen
import app.bunmusic.ui.EqualizerScreen
import app.bunmusic.ui.ExploreScreen
import app.bunmusic.ui.HomeScreen
import app.bunmusic.ui.HotlistScreen
import app.bunmusic.ui.LibraryScreen
import app.bunmusic.ui.MemoryScreen
import app.bunmusic.ui.PlayerScreen
import app.bunmusic.ui.ProfileScreen
import app.bunmusic.ui.QueuePanel
import app.bunmusic.ui.SearchScreen
import app.bunmusic.ui.SettingsScreen
import app.bunmusic.ui.ShareQueueSheet
import app.bunmusic.ui.StabilityScreen
import app.bunmusic.ui.StatsScreen
import app.bunmusic.ui.StorageScreen
import app.bunmusic.ui.WidgetsScreen
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders real screens to PNG on the JVM.
 *
 * Nothing in this project had ever been looked at: the strings were tested, the
 * ordering was tested, the stroke ratio was tested, but nobody had seen a frame.
 * Robolectric's native graphics mode draws the actual Compose tree into a real
 * bitmap at 411 × 891 dp, which is close enough to a phone screen to catch a
 * layout that overflows, a label that wraps, a colour that disappears.
 *
 * These are not assertions — they are eyes. A shot that throws is a screen that
 * would have crashed on launch, which is worth knowing on its own.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenShotTest {

    @get:Rule val compose = createComposeRule()

    private val outDir = File("/workspace/shots").apply { mkdirs() }

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        Prefs.init(ctx)
        Store.init(ctx)
        Sample.seed()
    }

    /**
     * @param endless true for screens that animate forever — the player's glow
     *   never settles and its disc never stops turning, so waiting for idle on
     *   it never returns. Those shots freeze the clock and take a frame at a
     *   known moment instead.
     */
    private fun shot(
        name: String,
        skin: BunSkin = BunSkin.MIDNIGHT,
        endless: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        Skin.preview(skin)
        if (endless) compose.mainClock.autoAdvance = false
        // Screens do not paint their own background — Root does, once, behind all
        // of them. Without this the first shots came out on the harness window's
        // white and every caption looked invisible.
        compose.setContent {
            BunTheme {
                BunMotion {
                    BunWindowProvider {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    if (Ink.isLight) {
                                        Brush.verticalGradient(listOf(Ink.Base, Ink.Base))
                                    } else {
                                        Brush.verticalGradient(
                                            0f to Ink.MiniBg,
                                            0.35f to Ink.Void,
                                            1f to Ink.Void,
                                        )
                                    }
                                )
                        ) { content() }
                    }
                }
            }
        }
        if (endless) compose.mainClock.advanceTimeBy(700) else compose.waitForIdle()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    // ---- the player, in every bun -------------------------------------------

    @Test fun player() = shot("player", endless = true) {
        PlayerScreen(state = Sample.playing, onClose = {}, onMore = {})
    }

    @Test fun playerCharcoal() = shot("player-charcoal", BunSkin.CHARCOAL, endless = true) {
        PlayerScreen(state = Sample.playing, onClose = {}, onMore = {})
    }

    @Test fun playerCloud() = shot("player-cloud", BunSkin.CLOUD, endless = true) {
        PlayerScreen(state = Sample.playing, onClose = {}, onMore = {})
    }

    @Test fun playerPink() = shot("player-pink", BunSkin.PINK, endless = true) {
        PlayerScreen(state = Sample.playing, onClose = {}, onMore = {})
    }

    @Test fun playerNebula() = shot("player-nebula", BunSkin.NEBULA, endless = true) {
        PlayerScreen(state = Sample.playing, onClose = {}, onMore = {})
    }

    @Test fun playerIdle() = shot("player-idle", endless = true) {
        PlayerScreen(state = PlayerState(), onClose = {}, onMore = {})
    }

    // ---- Nebula across the app, not just the player ------------------------

    @Test fun playerNebulaIdle() = shot("player-nebula-idle", BunSkin.NEBULA, endless = true) {
        PlayerScreen(state = PlayerState(), onClose = {}, onMore = {})
    }

    @Test fun homeNebula() = shot("home-nebula", BunSkin.NEBULA, endless = true) {
        HomeScreen(
            state = Sample.playing, downloadedIds = emptySet(),
            onOpenSearch = {}, onOpenExplore = {}, onOpenCollection = {}, onOpenMood = {},
            onPlay = { _, _ -> }, onOpenPlayer = {}, onTogglePlay = {}, onMore = {},
            onOpenSettings = {}, onOpenDiscovery = {},
        )
    }

    @Test fun libraryNebula() = shot("library-nebula", BunSkin.NEBULA) {
        LibraryScreen(
            state = Sample.playing, downloadedIds = emptySet(),
            onPlay = { _, _ -> }, onOpenCollection = {}, onOpenPlaylist = {},
            onOpenDownloads = {}, onOpenStats = {}, onMore = {},
        )
    }

    @Test fun settingsNebula() = shot("settings-nebula", BunSkin.NEBULA) {
        SettingsScreen(
            onBack = {}, onOpenStats = {}, onOpenWrapped = {}, onOpenDownloads = {},
            onOpenProfile = {}, onOpenEqualizer = {}, onOpenBuns = {}, onOpenAbout = {},
            onOpenWidgets = {}, onOpenCommands = {}, onToast = {},
        )
    }

    @Test fun bunsNebula() = shot("buns-nebula", BunSkin.NEBULA, endless = true) {
        BunsScreen(onBack = {})
    }

    @Test fun shareNebula() = shot("share-nebula", BunSkin.NEBULA) {
        ShareQueueSheet(Sample.queue, onClose = {}, onPlay = {}, onEnqueue = {}, onToast = {})
    }

    @Test fun playerQueue() = shot("player-queue", endless = true) {
        PlayerScreen(state = Sample.playing, onClose = {}, onMore = {}, openQueueOnStart = true)
    }

    @Test fun playerShare() = shot("player-share", endless = true) {
        PlayerScreen(state = Sample.playing, onClose = {}, onMore = {}, openShareOnStart = true)
    }

    // ---- the app around it ---------------------------------------------------

    @Test fun home() = shot("home", endless = true) {
        HomeScreen(
            state = Sample.playing, downloadedIds = emptySet(),
            onOpenSearch = {}, onOpenExplore = {}, onOpenCollection = {}, onOpenMood = {},
            onPlay = { _, _ -> }, onOpenPlayer = {}, onTogglePlay = {}, onMore = {},
            onOpenSettings = {}, onOpenDiscovery = {},
        )
    }

    @Test fun library() = shot("library") {
        LibraryScreen(
            state = Sample.playing, downloadedIds = emptySet(),
            onPlay = { _, _ -> }, onOpenCollection = {}, onOpenPlaylist = {},
            onOpenDownloads = {}, onOpenStats = {}, onMore = {},
        )
    }

    @Test fun settings() = shot("settings") {
        SettingsScreen(
            onBack = {}, onOpenStats = {}, onOpenWrapped = {}, onOpenDownloads = {},
            onOpenProfile = {}, onOpenEqualizer = {}, onOpenBuns = {}, onOpenAbout = {},
            onOpenWidgets = {}, onOpenCommands = {}, onToast = {},
        )
    }

    @Test fun settingsCharcoal() = shot("settings-charcoal", BunSkin.CHARCOAL) {
        SettingsScreen(
            onBack = {}, onOpenStats = {}, onOpenWrapped = {}, onOpenDownloads = {},
            onOpenProfile = {}, onOpenEqualizer = {}, onOpenBuns = {}, onOpenAbout = {},
            onOpenWidgets = {}, onOpenCommands = {}, onToast = {},
        )
    }

    @Test fun queuePanel() = shot("queue-panel") {
        QueuePanel(state = Sample.playing, onClose = {})
    }

    @Test fun shareSheet() = shot("share-sheet") {
        ShareQueueSheet(Sample.queue, onClose = {}, onPlay = {}, onEnqueue = {}, onToast = {})
    }

    @Test fun buns() = shot("buns") { BunsScreen(onBack = {}) }

    @Test fun about() = shot("about") { AboutScreen(onBack = {}) }

    @Test fun auth() = shot("auth") { AuthScreen(onGuest = {}) }

    @Test fun equalizer() = shot("equalizer") { EqualizerScreen(onBack = {}) }

    @Test fun memory() = shot("memory") { MemoryScreen(onBack = {}) }

    @Test fun stats() = shot("stats") { StatsScreen(onBack = {}) }

    @Test fun discovery() = shot("discovery") {
        DiscoveryScreen(Sample.playing, onBack = {}, onPlay = { _, _ -> }, onMore = {}, onToast = {})
    }

    @Test fun hotlist() = shot("hotlist", endless = true) {
        HotlistScreen(Sample.playing, emptySet(), onPlay = { _, _ -> }, onMore = {})
    }

    @Test fun explore() = shot("explore", endless = true) {
        ExploreScreen(
            onOpenCollection = {}, onOpenMood = {}, onPlay = { _, _ -> },
            onMore = {}, onOpenCharts = {},
        )
    }

    @Test fun search() = shot("search") {
        SearchScreen(Sample.playing, onPlay = { _, _ -> }, onOpenCollection = {}, onMore = {})
    }

    @Test fun storage() = shot("storage") { StorageScreen(onBack = {}) }

    @Test fun widgets() = shot("widgets") { WidgetsScreen(onBack = {}, onToast = {}) }

    @Test fun stability() = shot("stability") { StabilityScreen(onBack = {}, onToast = {}) }

    @Test fun profile() = shot("profile") {
        ProfileScreen(onBack = {}, onSignIn = {}, onToast = {})
    }
}

/** Believable data, so a screenshot shows the layout with real-length words. */
object Sample {

    val tracks = listOf(
        Track("a", "Kesariya", "Arijit Singh", "Brahmastra", durationMs = 268_000, videoId = "a"),
        Track("b", "Tum Hi Ho", "Arijit Singh", "Aashiqui 2", durationMs = 262_000, videoId = "b"),
        Track("c", "Channa Mereya", "Arijit Singh", "Ae Dil Hai Mushkil", durationMs = 289_000, videoId = "c"),
        Track("d", "Raataan Lambiyan", "Jubin Nautiyal", "Shershaah", durationMs = 230_000, videoId = "d"),
        Track("e", "Apna Bana Le", "Arijit Singh", "Bhediya", durationMs = 261_000, videoId = "e"),
        Track("f", "Shayad", "Arijit Singh", "Love Aaj Kal", durationMs = 247_000, videoId = "f"),
        Track("g", "Doobey", "Lothika", "Gehraiyaan", durationMs = 219_000, videoId = "g"),
        Track("h", "Ranjha", "B Praak", "Shershaah", durationMs = 228_000, videoId = "h"),
        Track("i", "Tera Hone Laga Hoon", "Atif Aslam", "Ajab Prem Ki Ghazab Kahani", durationMs = 300_000, videoId = "i"),
        Track("j", "Pehle Bhi Main", "Vishal Mishra", "Animal", durationMs = 245_000, videoId = "j"),
        Track("k", "Sahiba", "Jasleen Royal", "Phillauri", durationMs = 235_000, videoId = "k"),
        Track("l", "O Maahi", "Arijit Singh", "Dunki", durationMs = 233_000, videoId = "l"),
    )

    val queue: List<Track> = tracks

    val playing = PlayerState(
        track = tracks[0],
        isPlaying = true,
        positionMs = 72_000,
        durationMs = 268_000,
        queue = tracks,
        index = 0,
        repeat = 0,
        shuffle = false,
    )

    /** Fills the library so the shelf screens are not all empty states. */
    fun seed() {
        tracks.forEach { Store.remember(it) }
        listOf(0, 2, 4, 6).forEach { Store.toggleLike(tracks[it]) }
        tracks.take(5).forEach { Store.pushRecent(it) }
        if (Store.playlists().isEmpty()) {
            val p = Store.createPlaylist("Late night")
            Store.addManyToPlaylist(p.id, tracks.take(4))
        }
    }
}
