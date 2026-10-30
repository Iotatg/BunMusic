package app.bunmusic.core

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.bunmusic.R

/**
 * The palette every screen paints with.
 *
 * These used to be constants. They are now getters onto [Skin], which means a
 * bun swap changes all of them at once — and any screen that reads `Ink.Text`
 * inside composition is repainted for free.
 */
object Ink {
    val Void: Color get() = Skin.p.void
    val Base: Color get() = Skin.p.base
    val Raised: Color get() = Skin.p.raised
    val Card: Color get() = Skin.p.card
    val Line: Color get() = Skin.p.line
    val Text: Color get() = Skin.p.text
    val Muted: Color get() = Skin.p.muted
    val Faint: Color get() = Skin.p.faint
    val Lime: Color get() = Skin.p.accent
    val Lavender: Color get() = Skin.p.lavender
    val Rose: Color get() = Skin.p.rose
    val Mint: Color get() = Skin.p.mint
    val OnAccent: Color get() = Skin.p.onAccent

    val Hairline: Color get() = Skin.p.hairline
    val Field: Color get() = Skin.p.field
    val Sheet: Color get() = Skin.p.sheet
    val NavBg: Color get() = Skin.p.navBg
    val NavActiveBg: Color get() = Skin.p.navActiveBg
    val NavActiveInk: Color get() = Skin.p.navActiveInk
    val MiniBg: Color get() = Skin.p.miniBg
    val MiniText: Color get() = Skin.p.miniText
    val Shadow: Color get() = Skin.p.shadow

    val Hero: List<Color> get() = Skin.p.hero
    val HeroInk: Color get() = Skin.p.heroInk
    val HeroSub: Color get() = Skin.p.heroSub
    val HeroBtn: Color get() = Skin.p.heroBtn
    val HeroBtnInk: Color get() = Skin.p.heroBtnInk

    val isLight: Boolean get() = Skin.p.light
}

private val Sora = FontFamily(
    Font(R.font.sora_regular, FontWeight.Normal),
    Font(R.font.sora_semibold, FontWeight.Medium),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_bold, FontWeight.Bold),
)

private val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal, FontStyle.Normal),
)

private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/**
 * Type follows the bun. Midnight pairs Sora with Instrument Serif, Charcoal is
 * Inter all the way down, and Cloud is Sora — a geometric sans that sits closer
 * to the rounded lettering the blue study is set in.
 */
val SoraFamily: FontFamily get() = if (Skin.current == BunSkin.CHARCOAL) Inter else Sora
val SerifFamily: FontFamily get() = when (Skin.current) {
    BunSkin.CHARCOAL -> Inter
    BunSkin.CLOUD -> Sora
    BunSkin.PINK -> Sora
    BunSkin.MIDNIGHT -> InstrumentSerif
}

private fun typographyFor(sans: FontFamily, display: FontFamily): Typography = Typography(
    displayLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1.2).sp),
    displayMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.9).sp),
    displaySmall = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 31.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 23.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    titleMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp),
    bodyLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.6.sp),
)

@Composable
fun BunTheme(content: @Composable () -> Unit) {
    val p = Skin.p
    val scheme = if (p.light) {
        lightColorScheme(
            primary = p.accent,
            onPrimary = p.onAccent,
            secondary = p.muted,
            onSecondary = p.onAccent,
            tertiary = p.rose,
            background = p.base,
            onBackground = p.text,
            surface = p.raised,
            onSurface = p.text,
            surfaceVariant = p.card,
            onSurfaceVariant = p.muted,
            outline = p.line,
            error = p.rose,
        )
    } else {
        darkColorScheme(
            primary = p.accent,
            onPrimary = p.onAccent,
            secondary = p.lavender,
            onSecondary = p.onAccent,
            tertiary = p.rose,
            background = p.base,
            onBackground = p.text,
            surface = p.raised,
            onSurface = p.text,
            surfaceVariant = p.card,
            onSurfaceVariant = p.muted,
            outline = p.line,
            error = p.rose,
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = typographyFor(SoraFamily, SerifFamily),
        content = content,
    )
}
