package app.bunmusic.core

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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
 * BunMusic palette — "Midnight Lime".
 * Deep ink background, acid-lime accent, soft lavender secondary, warm off-white type.
 */
object Ink {
    val Void = Color(0xFF08070B)
    val Base = Color(0xFF0C0A11)
    val Raised = Color(0xFF151219)
    val Card = Color(0xFF1C1823)
    val Line = Color(0xFF2A2533)
    val Text = Color(0xFFF4F1E9)
    val Muted = Color(0xFF938DA0)
    val Faint = Color(0xFF5E596B)
    val Lime = Color(0xFFD8F65C)
    val Lavender = Color(0xFFC6BEF2)
    val Rose = Color(0xFFFF8CA4)
    val Mint = Color(0xFF7FE3C0)
    val OnAccent = Color(0xFF11130A)
}

val SoraFamily = FontFamily(
    Font(R.font.sora_regular, FontWeight.Normal),
    Font(R.font.sora_semibold, FontWeight.Medium),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_bold, FontWeight.Bold),
)

val SerifFamily = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal, FontStyle.Normal),
)

private val bunTypography = Typography(
    displayLarge = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1.2).sp),
    displayMedium = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.9).sp),
    displaySmall = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 31.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 23.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    titleMedium = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp),
    bodyLarge = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = SoraFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.6.sp),
)

private val bunColors = darkColorScheme(
    primary = Ink.Lime,
    onPrimary = Ink.OnAccent,
    secondary = Ink.Lavender,
    onSecondary = Ink.OnAccent,
    tertiary = Ink.Rose,
    background = Ink.Base,
    onBackground = Ink.Text,
    surface = Ink.Raised,
    onSurface = Ink.Text,
    surfaceVariant = Ink.Card,
    onSurfaceVariant = Ink.Muted,
    outline = Ink.Line,
    error = Ink.Rose,
)

@Composable
fun BunTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = bunColors,
        typography = bunTypography,
        content = content,
    )
}
