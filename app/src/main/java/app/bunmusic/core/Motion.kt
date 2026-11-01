package app.bunmusic.core

import android.provider.Settings
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
