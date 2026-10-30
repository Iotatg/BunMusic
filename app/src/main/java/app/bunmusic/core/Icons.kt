package app.bunmusic.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import app.bunmusic.R

/**
 * Launcher icons, one per bun.
 *
 * Android has no "setIcon" call, so the icon is chosen at launch time by which
 * `activity-alias` is enabled. Both aliases point at the same [app.bunmusic.MainActivity]
 * but carry their own icon, label and theme; flipping them is the only supported
 * way to repaint a home-screen icon without shipping an update.
 *
 * The cost is honest and worth stating: a home-screen shortcut pinned to the old
 * alias disappears when the bun changes, and an app update resets both aliases to
 * their manifest defaults — [Skin.load] puts the right one back on the next launch.
 */
object Icons {

    private const val MIDNIGHT_ALIAS = ".LauncherMidnight"
    private const val CHARCOAL_ALIAS = ".LauncherCharcoal"
    private const val CLOUD_ALIAS = ".LauncherCloud"

    fun aliasOf(skin: BunSkin): String = when (skin) {
        BunSkin.MIDNIGHT -> MIDNIGHT_ALIAS
        BunSkin.CHARCOAL -> CHARCOAL_ALIAS
        BunSkin.CLOUD -> CLOUD_ALIAS
    }

    /** The drawable the launcher shows for this bun — reused by the in-app previews. */
    fun launcherOf(skin: BunSkin): Int = when (skin) {
        BunSkin.MIDNIGHT -> R.mipmap.ic_launcher
        BunSkin.CHARCOAL -> R.mipmap.ic_launcher_charcoal
        BunSkin.CLOUD -> R.mipmap.ic_launcher_cloud
    }

    /** The full 108 dp layer, for places that draw the icon without a mask. */
    fun layerOf(skin: BunSkin): Int = when (skin) {
        BunSkin.MIDNIGHT -> R.mipmap.ic_launcher_foreground
        BunSkin.CHARCOAL -> R.mipmap.ic_launcher_charcoal_foreground
        BunSkin.CLOUD -> R.mipmap.ic_launcher_cloud_foreground
    }

    /**
     * Point the home screen at [skin]'s alias.
     *
     * Order matters: the new alias is switched on *before* the old one is switched
     * off, so there is never a frame where BunMusic has no launcher entry. If the
     * first call fails we leave both alone rather than risk stranding the user
     * with an app they cannot open.
     */
    fun apply(context: Context, skin: BunSkin) {
        val target = aliasOf(skin)
        if (!setEnabled(context, target, true)) return
        BunSkin.entries.forEach { other ->
            if (other != skin) setEnabled(context, aliasOf(other), false)
        }
    }

    /**
     * Bring the launcher entry back in line with [skin] without touching anything
     * when it is already right.
     *
     * This runs on every cold start because an app update resets both aliases to
     * their manifest defaults — a Charcoal user would otherwise find Midnight's
     * icon on their home screen until they switched buns again. It deliberately
     * does *not* call [apply] in the steady state: re-declaring the alias you are
     * currently running under is the one call that can disturb a live task.
     */
    fun reconcile(context: Context, skin: BunSkin) {
        val wrong = BunSkin.entries.any { it != skin && isOn(context, it) }
        if (isOn(context, skin) && !wrong) return
        apply(context, skin)
    }

    /** Mirrors the `android:enabled` flags in the manifest, used for unset states. */
    private fun defaultOn(skin: BunSkin) = skin == BunSkin.MIDNIGHT

    private fun isOn(context: Context, skin: BunSkin): Boolean {
        val state = context.packageManager.getComponentEnabledSetting(component(context, aliasOf(skin)))
        return when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> defaultOn(skin)
        }
    }

    private fun component(context: Context, alias: String) =
        ComponentName(context.packageName, context.packageName + alias)

    private fun setEnabled(context: Context, alias: String, on: Boolean): Boolean {
        val pm = context.packageManager
        val name = component(context, alias)
        val want = if (on) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (pm.getComponentEnabledSetting(name) == want) return true
        return runCatching {
            // DONT_KILL_APP: the caller restarts the process itself, and killing it
            // from here would race the write that decides which bun comes back.
            pm.setComponentEnabledSetting(name, want, PackageManager.DONT_KILL_APP)
            true
        }.getOrDefault(false)
    }
}
