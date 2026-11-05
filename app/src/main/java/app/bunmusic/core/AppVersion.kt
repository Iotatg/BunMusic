package app.bunmusic.core

/**
 * One place for the version string, so the About screen and Settings cannot
 * drift apart.
 *
 * It *did* drift: this was left at 2.4.0 / 26 through 2.5, 2.6 and 2.7, so
 * three releases shipped an About screen that named the wrong build. A unit
 * test now reads `app/build.gradle.kts` and fails if the two ever disagree
 * again.
 */
object AppVersion {
    const val VERSION = "2.8.0"
    const val CODE = 30
    const val LABEL = "$VERSION ($CODE)"
}
