package app.bunmusic.core

import android.view.HapticFeedbackConstants
import android.view.View
import app.bunmusic.data.Prefs

/**
 * One haptic pulse, gated by the preference. Kept deliberately tiny: the app
 * never buzzes on its own, only where a tap already changed something.
 */
object Haptics {

    private var view: View? = null

    fun attach(v: View) {
        view = v
        enabled = Prefs.settings.value.haptics
    }

    var enabled: Boolean = true

    fun tick() {
        if (!enabled) return
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun thud() {
        if (!enabled) return
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}
