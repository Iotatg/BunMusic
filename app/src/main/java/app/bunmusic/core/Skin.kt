package app.bunmusic.core

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
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
    /** What the picker prints under the swatches. */
    val typeLabel: String,
) {
    MIDNIGHT(
        id = "MIDNIGHT",
        title = "Midnight Lime",
        tagline = "Ink black, acid lime, lavender hero. The house bun.",
        credit = "BunMusic original",
        typeLabel = "Sora + Instrument Serif",
    ),
    CHARCOAL(
        id = "CHARCOAL",
        title = "Charcoal",
        tagline = "Paper white, charcoal ink, Inter. Quiet and typographic.",
        credit = "After a Dribbble study",
        typeLabel = "Inter · Regular / Medium / SemiBold / Bold",
    ),
    CLOUD(
        id = "CLOUD",
        title = "Cloud Blue",
        tagline = "Chalk white, electric blue, the bun in headphones.",
        credit = "After the Lunkgem study",
        typeLabel = "Sora · geometric, tight tracking",
    ),
    PINK(
        id = "PINK",
        title = "Pink Bun",
        tagline = "Blush paper, rose ink, a bun that feels the music.",
        credit = "After the Pink Bun study",
        typeLabel = "Sora · rounded, soft tracking",
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
    /** Home hero gradient, top to bottom. Midnight is lavender, Cloud is blue. */
    val hero: List<Color>,
    val heroInk: Color,
    val heroSub: Color,
    /** The button sitting on the hero. */
    val heroBtn: Color,
    val heroBtnInk: Color,
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
    hero = listOf(Color(0xFFC6BEF2), Color(0xFF8F86D6), Color(0xFF6D63B8)),
    heroInk = Color(0xFF15112A),
    heroSub = Color(0xFF2E2650),
    heroBtn = Color(0xFF15112A),
    heroBtnInk = Color(0xFFD8F65C),
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
    hero = listOf(Color(0xFF171C26), Color(0xFF171C26)),
    heroInk = Color(0xFFFFFFFF),
    heroSub = Color(0xB8FFFFFF),
    heroBtn = Color(0xFFFFFFFF),
    heroBtnInk = Color(0xFF171C26),
    light = true,
)

/**
 * Cloud Blue — the third bun, drawn from a white-and-electric-blue study.
 *
 * It is a light bun, so it borrows Charcoal's layouts wholesale; what changes is
 * the weather. Chalk surfaces, a blue that is allowed to be loud, a navy mini
 * player, and a hero card that is a blue gradient rather than a black block.
 */
private val Cloud = BunPalette(
    void = Color(0xFFEEF2FA),
    base = Color(0xFFF6F8FD),
    raised = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    line = Color(0xFFE3EAF7),
    text = Color(0xFF0E1A33),
    muted = Color(0xFF66769A),
    faint = Color(0xFFA6B2CB),
    accent = Color(0xFF2E6BFF),
    onAccent = Color(0xFFFFFFFF),
    lavender = Color(0xFF6E8CFF),
    rose = Color(0xFFFF5C7C),
    mint = Color(0xFF12B48A),
    hairline = Color(0xFFE7EEFB),
    field = Color(0xFFEDF3FF),
    sheet = Color(0xFFFFFFFF),
    navBg = Color(0xFFFFFFFF),
    navActiveBg = Color(0xFF2E6BFF),
    navActiveInk = Color(0xFFFFFFFF),
    miniBg = Color(0xFF13234A),
    miniText = Color(0xFFFFFFFF),
    shadow = Color(0x1F2E6BFF),
    hero = listOf(Color(0xFF2E6BFF), Color(0xFF5B8DFF), Color(0xFF86B4FF)),
    heroInk = Color(0xFFFFFFFF),
    heroSub = Color(0xCCFFFFFF),
    heroBtn = Color(0xFFFFFFFF),
    heroBtnInk = Color(0xFF2E6BFF),
    light = true,
)

/**
 * Pink Bun — the fourth bun, drawn from the blush-and-rose study.
 *
 * It is a light bun and uses the same layouts as Charcoal and Cloud. The rose is
 * deliberately deeper than the artwork's candy pink (#D63366 rather than #FF6B95)
 * because white button labels sit on it: at the brighter shade they fail AA.
 */
private val Pink = BunPalette(
    void = Color(0xFFFFE9F0),
    base = Color(0xFFFFF6F9),
    raised = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    line = Color(0xFFF7DDE6),
    text = Color(0xFF3B1B27),
    muted = Color(0xFFA36F80),
    faint = Color(0xFFC9A2AF),
    accent = Color(0xFFD63366),
    onAccent = Color(0xFFFFFFFF),
    lavender = Color(0xFFFF9FBB),
    rose = Color(0xFFE84678),
    mint = Color(0xFF2FA37C),
    hairline = Color(0xFFF8E1E9),
    field = Color(0xFFFFF0F5),
    sheet = Color(0xFFFFFFFF),
    navBg = Color(0xFFFFFFFF),
    navActiveBg = Color(0xFFD63366),
    navActiveInk = Color(0xFFFFFFFF),
    miniBg = Color(0xFF3A1A27),
    miniText = Color(0xFFFFFFFF),
    shadow = Color(0x26D63366),
    hero = listOf(Color(0xFFFF7FA8), Color(0xFFFFA9C1), Color(0xFFFFD0DC)),
    heroInk = Color(0xFF4A1C2C),
    heroSub = Color(0xFF642B3E),
    heroBtn = Color(0xFFD63366),
    heroBtnInk = Color(0xFFFFFFFF),
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
        BunSkin.CLOUD -> Cloud
        BunSkin.PINK -> Pink
    }

    val light: Boolean get() = p.light

    /**
     * Cloud is the one bun that is light *and* blue. A few pieces — the line
     * seek bar, the blue play disc — key off this rather than off "light", so
     * Charcoal keeps its waveform and its black button.
     */
    val isCloud: Boolean get() = current == BunSkin.CLOUD

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
        // An app update resets both launcher aliases to their manifest defaults,
        // so the icon is re-pointed here on every cold start rather than only on
        // a switch. Cheap: it returns immediately when the right alias is on.
        Icons.reconcile(context, current)
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
        // The launcher icon has to be swapped before the relaunch, otherwise the
        // new process comes back wearing the previous bun's face.
        Icons.apply(context, skin)
        // The home-screen widget is drawn by the launcher, not by us, so it has to
        // be repainted now — nothing in the new process will touch it until the
        // next track change.
        runCatching { app.bunmusic.widget.Bar.pushAll(context.applicationContext) }
        restart(context)
    }

    private fun restart(context: Context) {
        val app = context.applicationContext
        // Launch through the alias, not MainActivity: the alias is what carries the
        // splash theme, and it is the component the launcher would have started.
        val aliasIntent = Intent().apply {
            component = ComponentName(app.packageName, app.packageName + Icons.aliasOf(current))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        // If the component switch was refused the alias is disabled, and starting it
        // would throw — leaving the listener on a dead screen. Fall back to the
        // activity itself, which always exists.
        val resolvable = runCatching {
            app.packageManager.resolveActivity(aliasIntent, 0) != null
        }.getOrDefault(false)
        val intent = if (resolvable) aliasIntent else {
            Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
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
