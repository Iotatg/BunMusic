package app.bunmusic.core

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.bunmusic.data.Prefs

/**
 * Motion and type, applied once at the top of the tree.
 *
 * Two accessibility settings that a music app cannot fake:
 *
 *  - **Reduced motion.** A phone that has animations switched off in the system
 *    settings is telling every app something. BunMusic listens, and the in-app
 *    switch can also turn motion down without changing the whole phone.
 *  - **Type scale.** The system font size already scales Compose text; this adds
 *    an app-level multiplier on top, because a 9 sp caption at 1.3× is still a
 *    caption and a 34 sp greeting at 1.3× is a poster.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun BunMotion(content: @Composable () -> Unit) {
    val settings by Prefs.settings.collectAsState()
    val context = LocalContext.current
    val systemReduced = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
    val reduced = settings.reducedMotion || systemReduced

    // The colour getters read Skin.contrast, so the preference has to be mirrored
    // into state — a preference read alone would not recompose anything.
    LaunchedEffect(settings.highContrast) { Skin.contrast = settings.highContrast }

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalReducedMotion provides reduced,
        LocalDensity provides Density(density.density, density.fontScale * settings.fontScale),
    ) {
        content()
    }
}

/** A duration that collapses to zero when motion is reduced. */
@Composable
fun motionMs(full: Int): Int = if (LocalReducedMotion.current) 0 else full

// ------------------------------------------------------------------- curves

/**
 * The three curves everything in BunMusic moves on.
 *
 * Material's motion spec calls these *emphasised*, *standard* and *accelerate*,
 * and the reason to name them here rather than write `tween(300)` at forty call
 * sites is that a screen with forty slightly different timings does not feel
 * fast — it feels unsettled. One vocabulary means a page change, a sheet and a
 * button press all read as the same hand moving.
 *
 *  - **Emphasised** starts fast and lands slowly. For things entering.
 *  - **Standard** is symmetric. For things moving from A to B.
 *  - **Accelerate** starts slowly and leaves. For things going away.
 */
object BunEase {
    val Emphasised: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
}

/**
 * Springs, by job.
 *
 * Stiffness numbers are written out rather than taken from `Spring.Stiffness*`
 * because those constants collapse two of the five names onto the same value,
 * and the difference between "a thumb pressed this" and "a page arrived" is
 * exactly the difference that constant cannot express.
 *
 *  - [snappy] — press feedback. Settles before the finger lifts.
 *  - [smooth] — layout changes. No visible overshoot.
 *  - [bouncy] — a hero moment. Overshoots once, deliberately.
 *  - [gentle] — ambient drift. Never finishes, never draws attention.
 */
object BunSpring {
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.75f, stiffness = 900f)
    fun <T> smooth(): SpringSpec<T> = spring(dampingRatio = 0.86f, stiffness = 420f)
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = 700f)
    fun <T> gentle(): SpringSpec<T> = spring(dampingRatio = 0.92f, stiffness = 260f)
}

/**
 * How long a row waits before it appears.
 *
 * A list that fades in all at once reads as a page load; the same list with a
 * 26 ms head start per row reads as a deck being dealt. The cap matters more
 * than the step: without it the fortieth row waits a full second, which is the
 * difference between "animated" and "slow".
 */
object BunStagger {
    const val STEP = 26
    const val CAP = 160

    fun delayFor(index: Int, step: Int = STEP, cap: Int = CAP): Int {
        if (index <= 0 || step <= 0 || cap <= 0) return 0
        // Compared before multiplying, not after: `index * step` overflows to a
        // negative number somewhere past 82 million rows, and a negative delay
        // is not a delay. The comparison costs one division.
        return if (index > cap / step) cap else index * step
    }
}

/** [spec], or a snap when the listener has asked for less motion. */
@Composable
fun <T> motionOr(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spec

/** A tween on [BunEase.Emphasised] that collapses under reduced motion. */
@Composable
fun eased(durationMs: Int = 300, easing: Easing = BunEase.Emphasised): FiniteAnimationSpec<Float> =
    androidx.compose.animation.core.tween(motionMs(durationMs), easing = easing)

/**
 * When a sideways drag on the now-playing strip counts as "skip".
 *
 * Pure arithmetic, because a threshold is a decision about the user's intent and
 * those should be checkable without a phone: a third of the strip is far enough
 * that a thumb reaching past it for the tab bar cannot trigger it, and short
 * enough that a deliberate swipe does not have to cross the whole screen.
 */
object SwipeSkip {
    /** Fraction of the strip's width that commits the skip. */
    const val THRESHOLD = 0.33f

    /** Fraction the strip may be dragged; past this it stops following. */
    const val LIMIT = 0.70f

    /** How far the strip travels when it commits, as a fraction of its width. */
    const val EXIT = 1.10f

    /** Rubber band: the offset actually shown for a raw drag. */
    fun clamp(dx: Float, width: Float): Float {
        if (width <= 0f) return 0f
        val limit = width * LIMIT
        return dx.coerceIn(-limit, limit)
    }

    fun commits(dx: Float, width: Float): Boolean =
        width > 0f && kotlin.math.abs(dx) > width * THRESHOLD

    /**
     * -1 for next (dragged left), +1 for previous (dragged right), 0 for "put it
     * back". Direction is the sign of the drag, not of the velocity: the strip is
     * already under the finger, so where it is says more than how fast it got there.
     */
    fun direction(dx: Float, width: Float): Int = when {
        !commits(dx, width) -> 0
        dx < 0f -> -1
        else -> 1
    }

    /** Opacity of the strip at [dx]: it fades as it is pulled away. */
    fun alpha(dx: Float, width: Float): Float {
        if (width <= 0f) return 1f
        return 1f - 0.30f * (kotlin.math.abs(dx) / width).coerceIn(0f, 1f)
    }
}
