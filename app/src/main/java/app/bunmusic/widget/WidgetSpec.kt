package app.bunmusic.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import app.bunmusic.R
import app.bunmusic.core.BunPalette
import app.bunmusic.core.BunSkin
import app.bunmusic.core.Skin

/**
 * Which colours a widget wears.
 *
 * [AUTO] is the useful default: the widget follows the bun the app is wearing,
 * so switching buns repaints the home screen too. The rest pin one bun no matter
 * what the app is doing, and [GLASS] is the smoked panel from the reference
 * screenshot — dark and translucent, legible on any wallpaper.
 */
enum class WidgetSkin(val id: String, val label: String) {
    AUTO("AUTO", "Match the bun"),
    MIDNIGHT("MIDNIGHT", "Midnight Lime"),
    CHARCOAL("CHARCOAL", "Charcoal"),
    CLOUD("CLOUD", "Cloud Blue"),
    PINK("PINK", "Pink Bun"),
    GLASS("GLASS", "Smoked glass"),
    ;

    /** The bun this skin borrows, or null for glass. */
    fun bun(): BunSkin? = when (this) {
        AUTO -> Skin.current
        MIDNIGHT -> BunSkin.MIDNIGHT
        CHARCOAL -> BunSkin.CHARCOAL
        CLOUD -> BunSkin.CLOUD
        PINK -> BunSkin.PINK
        GLASS -> null
    }

    companion object {
        fun of(id: String?): WidgetSkin = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/** Corner language. Same three words the rest of the app uses. */
enum class WidgetShape(val id: String, val label: String, val radiusDp: Int) {
    SHARP("SHARP", "Sharp", 8),
    ROUND("ROUND", "Round", 20),
    PILL("PILL", "Pill", 30),
    ;

    /** Cover art inside a card is rounded a little less than the card itself. */
    val artRadiusDp: Int get() = when (this) {
        SHARP -> 6
        ROUND -> 10
        PILL -> 14
    }

    companion object {
        fun of(id: String?): WidgetShape = entries.firstOrNull { it.id == id } ?: ROUND
    }
}

/**
 * The smoked panel. Not a bun: it is deliberately theme-independent, because the
 * point of a glass widget is that it reads on top of whatever wallpaper is
 * behind it.
 */
val GlassPalette: BunPalette = BunPalette(
    void = Color(0xFF08070B),
    base = Color(0xFF121016),
    raised = Color(0xFF1A1720),
    card = Color(0xFF1C1823),
    line = Color(0xFF2A2533),
    text = Color(0xFFF6F2EA),
    muted = Color(0xFFC3BCCB),
    faint = Color(0xFFB4ADBD),
    accent = Color(0xFFD8F65C),
    onAccent = Color(0xFF11130A),
    lavender = Color(0xFFC6BEF2),
    rose = Color(0xFFFF8CA4),
    mint = Color(0xFF7FE3C0),
    hairline = Color(0x1FFFFFFF),
    field = Color(0x14FFFFFF),
    sheet = Color(0xFF17131E),
    navBg = Color(0xFF17131E),
    navActiveBg = Color(0x24D8F65C),
    navActiveInk = Color(0xFFD8F65C),
    miniBg = Color(0xFF211C2C),
    miniText = Color(0xFFF4F1E9),
    shadow = Color(0x66000000),
    hero = listOf(Color(0xFF2459E8), Color(0xFF2E6BFF)),
    heroInk = Color(0xFFFFFFFF),
    heroSub = Color(0xFFFFFFFF),
    heroBtn = Color(0xFFFFFFFF),
    heroBtnInk = Color(0xFF2E6BFF),
    light = false,
)

/**
 * Everything a single widget instance can be configured to do.
 *
 * Kept as one small value so it can be encoded into one preference string, and
 * so the renderer never has to ask five different questions.
 */
data class WidgetLook(
    val skin: WidgetSkin = WidgetSkin.AUTO,
    val shape: WidgetShape = WidgetShape.ROUND,
    val progress: Boolean = true,
    val times: Boolean = true,
    val like: Boolean = true,
    val queue: Boolean = true,
) {
    val palette: BunPalette get() = skin.bun()?.let { Skin.paletteOf(it) } ?: GlassPalette

    val cardRes: Int get() = WidgetRes.card(skin.bun(), shape)
    val playRes: Int get() = WidgetRes.play(skin.bun())

    fun encode(): String = listOf(
        skin.id,
        shape.id,
        if (progress) "1" else "0",
        if (times) "1" else "0",
        if (like) "1" else "0",
        if (queue) "1" else "0",
    ).joinToString("|")

    companion object {
        val Default = WidgetLook()

        fun decode(raw: String?): WidgetLook {
            if (raw.isNullOrBlank()) return Default
            val p = raw.split("|")
            fun flag(i: Int): Boolean = p.getOrNull(i) != "0"
            return WidgetLook(
                skin = WidgetSkin.of(p.getOrNull(0)),
                shape = WidgetShape.of(p.getOrNull(1)),
                progress = flag(2),
                times = flag(3),
                like = flag(4),
                queue = flag(5),
            )
        }
    }
}

/** Resource lookup. Explicit `when`s rather than `getIdentifier`, so a rename fails the build. */
internal object WidgetRes {

    fun card(bun: BunSkin?, shape: WidgetShape): Int = when (bun) {
        BunSkin.MIDNIGHT -> when (shape) {
            WidgetShape.SHARP -> R.drawable.widget_card_midnight_sharp
            WidgetShape.ROUND -> R.drawable.widget_card_midnight_round
            WidgetShape.PILL -> R.drawable.widget_card_midnight_pill
        }
        BunSkin.CHARCOAL -> when (shape) {
            WidgetShape.SHARP -> R.drawable.widget_card_charcoal_sharp
            WidgetShape.ROUND -> R.drawable.widget_card_charcoal_round
            WidgetShape.PILL -> R.drawable.widget_card_charcoal_pill
        }
        BunSkin.CLOUD -> when (shape) {
            WidgetShape.SHARP -> R.drawable.widget_card_cloud_sharp
            WidgetShape.ROUND -> R.drawable.widget_card_cloud_round
            WidgetShape.PILL -> R.drawable.widget_card_cloud_pill
        }
        BunSkin.PINK -> when (shape) {
            WidgetShape.SHARP -> R.drawable.widget_card_pink_sharp
            WidgetShape.ROUND -> R.drawable.widget_card_pink_round
            WidgetShape.PILL -> R.drawable.widget_card_pink_pill
        }
        null -> when (shape) {
            WidgetShape.SHARP -> R.drawable.widget_card_glass_sharp
            WidgetShape.ROUND -> R.drawable.widget_card_glass_round
            WidgetShape.PILL -> R.drawable.widget_card_glass_pill
        }
    }

    fun scrim(shape: WidgetShape): Int = when (shape) {
        WidgetShape.SHARP -> R.drawable.widget_scrim_sharp
        WidgetShape.ROUND -> R.drawable.widget_scrim_round
        WidgetShape.PILL -> R.drawable.widget_scrim_pill
    }

    fun play(bun: BunSkin?): Int = when (bun) {
        BunSkin.MIDNIGHT -> R.drawable.widget_play_midnight
        BunSkin.CHARCOAL -> R.drawable.widget_play_charcoal
        BunSkin.CLOUD -> R.drawable.widget_play_cloud
        BunSkin.PINK -> R.drawable.widget_play_pink
        null -> R.drawable.widget_play_glass
    }
}

/**
 * Per-widget configuration, plus the defaults new widgets start from.
 *
 * The defaults are what Settings edits; a widget that was never configured (or
 * whose config activity was skipped) inherits them, so changing the default
 * style updates every unconfigured widget on the next refresh.
 */
object WidgetStore {

    private const val FILE = "bunmusic_widgets"
    private const val KEY_DEFAULTS = "defaults"

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    private fun ready(context: Context): SharedPreferences {
        init(context)
        return sp
    }

    fun look(context: Context, widgetId: Int): WidgetLook {
        val raw = ready(context).getString("w$widgetId", null)
        return if (raw == null) defaults(context) else WidgetLook.decode(raw)
    }

    fun save(context: Context, widgetId: Int, look: WidgetLook) {
        ready(context).edit().putString("w$widgetId", look.encode()).apply()
    }

    fun forget(context: Context, ids: IntArray) {
        val e = ready(context).edit()
        ids.forEach { e.remove("w$it") }
        e.apply()
    }

    fun defaults(context: Context): WidgetLook =
        WidgetLook.decode(ready(context).getString(KEY_DEFAULTS, null))

    fun setDefaults(context: Context, look: WidgetLook) {
        ready(context).edit().putString(KEY_DEFAULTS, look.encode()).apply()
    }
}
