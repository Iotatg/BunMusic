package app.bunmusic.core

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import app.bunmusic.MainActivity
import app.bunmusic.data.Prefs

/**
 * Buns — the app's skins.
 *
 * A bun is not a tint: it swaps the whole surface language. Palette, typeface,
 * corner radii, how the bottom bar sits, whether a list shows artwork or a track
 * number, whether the seek bar is a line or a waveform. Every screen reads
 * colours through [Ink] and type through [SoraFamily] / [SerifFamily], so both of
 * those resolve against [Skin.current] and a swap repaints everything.
 *
 * Switching a bun restarts the process. That is deliberate — a restart is the
 * only way to be certain no `remember`ed colour from the old bun survives.
 */
enum class BunSkin(
    val id: String,
    val title: String,
    val tagline: String,
    val credit: String,
) {
    MIDNIGHT(
        id = "MIDNIGHT",
        title = "Midnight Lime",
        tagline = "Ink black, acid lime, lavender hero. The house bun.",
        credit = "BunMusic original",
    ),
    CHARCOAL(
        id = "CHARCOAL",
        title = "Charcoal",
        tagline = "Paper white, charcoal ink, Inter. Quiet and typographic.",
        credit = "After a Dribbble study",
    ),
    ;

    companion object {
        fun of(id: String?): BunSkin = entries.firstOrNull { it.id == id } ?: MIDNIGHT
    }
}

/**
 * Every colour a bun needs. Anything hard-coded in a screen is a bug: if a shade
 * is not in here, the second bun cannot restyle it.
 */
data class BunPalette(
    val void: Color,
    val base: Color,
    val raised: Color,
    val card: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val onAccent: Color,
    val lavender: Color,
    val rose: Color,
    val mint: Color,
    /** 1px separators and card outlines. */
    val hairline: Color,
    /** Inset surfaces: chips, icon wells, selected rows. */
    val field: Color,
    /** Bottom sheets. */
    val sheet: Color,
    val navBg: Color,
    val navActiveBg: Color,
    val navActiveInk: Color,
    val miniBg: Color,
    val miniText: Color,
    val shadow: Color,
    /** True for light buns — a handful of layouts flip on this. */
    val light: Boolean,
)

private val Midnight = BunPalette(
    void = Color(0xFF08070B),
    base = Color(0xFF0C0A11),
    raised = Color(0xFF151219),
    card = Color(0xFF1C1823),
    line = Color(0xFF2A2533),
    text = Color(0xFFF4F1E9),
    muted = Color(0xFF938DA0),
    faint = Color(0xFF5E596B),
    accent = Color(0xFFD8F65C),
    onAccent = Color(0xFF11130A),
    lavender = Color(0xFFC6BEF2),
    rose = Color(0xFFFF8CA4),
    mint = Color(0xFF7FE3C0),
    hairline = Color(0x0DFFFFFF),
    field = Color(0x0FFFFFFF),
    sheet = Color(0xFF17131E),
    navBg = Color(0xFF17131E),
    navActiveBg = Color(0x24D8F65C),
    navActiveInk = Color(0xFFD8F65C),
    miniBg = Color(0xFF211C2C),
    miniText = Color(0xFFF4F1E9),
    shadow = Color(0x66000000),
    light = false,
)

private val Charcoal = BunPalette(
    void = Color(0xFFFFFFFF),
    base = Color(0xFFFFFFFF),
    raised = Color(0xFFFAFBFC),
    card = Color(0xFFF3F5F8),
    line = Color(0xFFE7EAEF),
    text = Color(0xFF171C26),
    muted = Color(0xFF6C7689),
    faint = Color(0xFFA4AAB7),
    accent = Color(0xFF171C26),
    onAccent = Color(0xFFFFFFFF),
    lavender = Color(0xFF6C7689),
    rose = Color(0xFFD95C6A),
    mint = Color(0xFF2E9E77),
    hairline = Color(0xFFE9EBF0),
    field = Color(0xFFF1F3F6),
    sheet = Color(0xFFFFFFFF),
    navBg = Color(0xFFFFFFFF),
    navActiveBg = Color(0xFF171C26),
    navActiveInk = Color(0xFFFFFFFF),
    miniBg = Color(0xFF171C26),
    miniText = Color(0xFFFFFFFF),
    shadow = Color(0x1F171C26),
    light = true,
)

object Skin {

    /** Set by [load] at startup and by [apply] on a switch. */
    var current: BunSkin by mutableStateOf(BunSkin.MIDNIGHT)
        private set

    val p: BunPalette get() = paletteOf(current)

    /** A bun's palette without making it current — used by the picker previews. */
    fun paletteOf(skin: BunSkin): BunPalette = when (skin) {
        BunSkin.MIDNIGHT -> Midnight
        BunSkin.CHARCOAL -> Charcoal
    }

    val light: Boolean get() = p.light

    /**
     * The bun is stored in its own tiny preferences file, written with [commit]
     * rather than [SharedPreferences.Editor.apply].
     *
     * That detail is the whole reason switching used to fail: `apply()` hands the
     * write to a background thread, and we kill the process a moment later, so the
     * choice never reached the disk and the next launch read the old bun back.
     * `commit()` blocks until the bytes are written.
     */
    private const val FILE = "bun_skin"
    private const val KEY = "skin"

    private fun store(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(context: Context) {
        Prefs.init(context)
        val stored = store(context).getString(KEY, null) ?: Prefs.settings.value.skin
        current = BunSkin.of(stored)
        // keep the two stores honest if one of them was written by an older build
        if (stored != Prefs.settings.value.skin) {
            Prefs.update { it.copy(skin = stored) }
        }
    }

    /**
     * Persist the choice, then restart the process. The alarm fires the launch
     * intent a fraction of a second later, so the new process always starts from
     * a cold state — no stale colours, no half-swapped typography.
     */
    fun apply(context: Context, skin: BunSkin) {
        val written = store(context).edit().putString(KEY, skin.id).commit()
        Prefs.update { it.copy(skin = skin.id) }
        current = skin
        if (!written) {
            // Disk refused the write — restarting would only reload the old bun.
            return
        }
        restart(context)
    }

    private fun restart(context: Context) {
        val app = context.applicationContext
        val intent = Intent(app, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val pending = PendingIntent.getActivity(
            app,
            8842,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Long enough that the kill below always lands before the relaunch, short
        // enough that it feels like a tap. If the alarm cannot be scheduled, launch
        // directly instead of leaving the listener staring at a dead screen.
        val scheduled = runCatching {
            val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarms.set(AlarmManager.RTC, System.currentTimeMillis() + 400, pending)
            true
        }.getOrDefault(false)

        (context as? Activity)?.finishAffinity()
        if (scheduled) {
            android.os.Process.killProcess(android.os.Process.myPid())
        } else {
            runCatching { app.startActivity(intent) }
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
