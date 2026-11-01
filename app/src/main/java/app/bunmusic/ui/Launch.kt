package app.bunmusic.ui

/**
 * What an external entry point asked the app to do.
 *
 * A widget tap, an app shortcut or a Quick Settings tile all arrive as an
 * `Intent`, and all of them can land while the app is already running. Reducing
 * them to one value with a sequence number means the shell reacts to the *new*
 * request instead of replaying the launch one on every recomposition — which is
 * exactly what used to happen when tapping a widget with the app open.
 */
data class Launch(
    val player: Boolean = false,
    val queue: Boolean = false,
    val search: Boolean = false,
    val commands: Boolean = false,
    val shuffle: Boolean = false,
    val seq: Int = 0,
)
