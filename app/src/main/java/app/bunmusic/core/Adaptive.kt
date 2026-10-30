package app.bunmusic.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max

/**
 * How much room the app has, expressed in the units the layouts actually need.
 *
 * Compose gives us one number — the window width in dp — and every screen used to
 * assume it was a phone. That held until the first tablet: a 124 dp card row on a
 * 1280 dp page looks like a mistake, a bottom bar on a 10" screen wastes the one
 * edge with the most room, and a full-bleed track list turns into 90-character
 * lines nobody can read.
 *
 * The breakpoints are Material's, not invented here: 600 dp is where a phone
 * becomes a small tablet (or a phone in split screen), 840 dp is where a tablet
 * has room for two panes. Height is classified separately because a phone in
 * landscape is 891 × 411 — wide, but with no vertical room at all, and those two
 * facts need different answers.
 */
enum class BunWidth { COMPACT, MEDIUM, EXPANDED }

enum class BunHeight { SHORT, REGULAR, TALL }

@Immutable
data class BunWindow(
    val widthDp: Int = 360,
    val heightDp: Int = 800,
) {
    val width: BunWidth
        get() = when {
            widthDp >= EXPANDED_FROM -> BunWidth.EXPANDED
            widthDp >= MEDIUM_FROM -> BunWidth.MEDIUM
            else -> BunWidth.COMPACT
        }

    val height: BunHeight
        get() = when {
            heightDp >= TALL_FROM -> BunHeight.TALL
            heightDp < SHORT_BELOW -> BunHeight.SHORT
            else -> BunHeight.REGULAR
        }

    /** Wider than it is tall. The player and the sheets care about this. */
    val landscape: Boolean get() = widthDp > heightDp

    /** True from 600 dp up: the bottom bar becomes a rail down the left edge. */
    val wide: Boolean get() = width != BunWidth.COMPACT

    /** True from 840 dp up: the player splits into artwork + controls. */
    val twoPane: Boolean get() = width == BunWidth.EXPANDED && landscape

    /** True for landscape phones: content must scroll rather than stretch. */
    val short: Boolean get() = height == BunHeight.SHORT

    /**
     * Page margin. A 20 dp inset is right on a phone and looks pinched on a
     * tablet, where the eye expects the text column to breathe.
     */
    val gutter: Dp
        get() = when (width) {
            BunWidth.COMPACT -> 20.dp
            BunWidth.MEDIUM -> 28.dp
            BunWidth.EXPANDED -> 36.dp
        }

    /**
     * How wide a single reading column may get. Beyond ~1040 dp a list of song
     * titles stops being scannable, so on a big tablet the content is centred
     * inside this instead of stretching to the bezels.
     */
    val contentMax: Dp
        get() = when (width) {
            BunWidth.COMPACT -> Dp.Infinity
            BunWidth.MEDIUM -> 720.dp
            BunWidth.EXPANDED -> 1040.dp
        }

    /** Sheets and the player: one comfortable column, whatever the device. */
    val readingMax: Dp
        get() = when (width) {
            BunWidth.COMPACT -> Dp.Infinity
            else -> 560.dp
        }

    /**
     * A carousel tile, grown for the device. The base sizes are the phone sizes
     * the layouts were drawn at; on a tablet the same card at the same dp reads
     * as a postage stamp next to 36 dp margins.
     */
    fun tile(base: Dp): Dp = when (width) {
        BunWidth.COMPACT -> base
        BunWidth.MEDIUM -> base * 1.12f
        BunWidth.EXPANDED -> base * 1.24f
    }

    /**
     * How many fixed-minimum tiles fit in a row at this width. Used by the mood
     * grid, which is the one place a wrapped grid beats a carousel.
     */
    fun columns(minItem: Dp, gap: Dp = 10.dp, pad: Dp = gutter): Int {
        val usable = widthDp - 2 * pad.value
        if (usable <= minItem.value) return 2
        val fit = floor((usable + gap.value) / (minItem.value + gap.value)).toInt()
        return max(2, fit).coerceAtMost(4)
    }

    /**
     * Ceiling for the now-playing artwork. Unlimited on an ordinary phone — the
     * width fraction already fits. On a landscape phone the *height* is the
     * binding constraint and an uncapped square pushes the transport row off
     * the bottom of the screen.
     */
    fun artCap(): Dp = if (short) (heightDp * 0.46f).dp else Dp.Infinity

    /** Width of the navigation rail when [wide]. */
    val railWidth: Dp get() = 92.dp

    companion object {
        const val MEDIUM_FROM = 600
        const val EXPANDED_FROM = 840
        const val SHORT_BELOW = 480
        const val TALL_FROM = 900
    }
}

/**
 * The window every screen reads. Defaults to a 360 × 800 phone so previews and
 * any composable used outside the shell still lay out sensibly.
 */
val LocalBunWindow = staticCompositionLocalOf { BunWindow() }

/** Reads the current window size, re-reading it on rotation, resize and fold. */
@Composable
fun rememberBunWindow(): BunWindow {
    val config = LocalConfiguration.current
    return remember(config.screenWidthDp, config.screenHeightDp) {
        BunWindow(config.screenWidthDp, config.screenHeightDp)
    }
}

/**
 * Installs [LocalBunWindow] for the whole app. Call once, above the shell.
 *
 * This is read from the configuration rather than from `BoxWithConstraints` so
 * that a screen deep in the tree can ask "am I wide?" without threading a
 * parameter through six composables — and so the answer is the same everywhere.
 */
@Composable
fun BunWindowProvider(content: @Composable () -> Unit) {
    val window = rememberBunWindow()
    CompositionLocalProvider(LocalBunWindow provides window, content = content)
}
