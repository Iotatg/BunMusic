package app.bunmusic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import app.bunmusic.core.BunTheme
import app.bunmusic.core.BunWindowProvider
import app.bunmusic.core.Haptics
import app.bunmusic.core.Skin
import app.bunmusic.ui.Launch
import app.bunmusic.ui.Root
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * The most recent external request. A widget, a shortcut and the Quick
     * Settings tile all land here; [seq] makes a repeat request (same extras, a
     * second tap) still count as new.
     */
    private val launch = MutableStateFlow(Launch())
    private var launchSeq = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The status bar has to flip with the bun: dark icons on Charcoal, light on Midnight.
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = if (Skin.light) {
                SystemBarStyle.light(transparent, transparent)
            } else {
                SystemBarStyle.dark(transparent)
            },
            navigationBarStyle = if (Skin.light) {
                SystemBarStyle.light(transparent, transparent)
            } else {
                SystemBarStyle.dark(transparent)
            },
        )
        Haptics.attach(window.decorView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        readIntent(intent)
        setContent {
            BunTheme {
                // The window size is resolved once, at the top, so every screen
                // and every shared component reads the same answer.
                BunWindowProvider {
                    val request by launch.collectAsState()
                    Root(launch = request)
                }
            }
        }
    }

    /**
     * The app is singleTop, so a second widget tap arrives here rather than in
     * [onCreate]. Without this override the extras were read once and then
     * ignored — tapping the widget with the app open did nothing.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val player = intent.getBooleanExtra("open_player", false) || action == ACTION_PLAYER
        val queue = intent.getBooleanExtra("open_queue", false)
        val search = intent.getBooleanExtra("open_search", false) || action == ACTION_SEARCH
        val commands = intent.getBooleanExtra("open_commands", false) || action == ACTION_COMMANDS
        val shuffle = intent.getBooleanExtra("shuffle", false) || action == ACTION_SHUFFLE
        if (!player && !queue && !search && !commands && !shuffle) return
        launch.value = Launch(
            player = player,
            queue = queue,
            search = search,
            commands = commands,
            shuffle = shuffle,
            seq = ++launchSeq,
        )
    }

    companion object {
        const val ACTION_PLAYER = "app.bunmusic.OPEN_PLAYER"
        const val ACTION_SEARCH = "app.bunmusic.SEARCH"
        const val ACTION_COMMANDS = "app.bunmusic.COMMANDS"
        const val ACTION_SHUFFLE = "app.bunmusic.SHUFFLE"
    }
}
