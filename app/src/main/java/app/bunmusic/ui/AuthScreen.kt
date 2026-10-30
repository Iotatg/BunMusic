package app.bunmusic.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SerifFamily
import app.bunmusic.core.Skin
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Auth
import app.bunmusic.data.GoogleAuth
import app.bunmusic.data.Prefs
import kotlinx.coroutines.delay

private enum class Door { EMAIL, PHONE }

/**
 * The front door. One screen, three ways in, and an honest exit for anyone who
 * would rather not have an account at all.
 */
@Composable
fun AuthScreen(onGuest: () -> Unit) {
    val context = LocalContext.current
    val settings by Prefs.settings.collectAsState()
    val accounts by Auth.accounts.collectAsState()

    var door by remember { mutableStateOf(Door.EMAIL) }
    var signUp by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf<Auth.OtpChallenge?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var googleSetup by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableStateOf(0) }

    LaunchedEffect(challenge?.resendAt) {
        while (true) {
            val at = challenge?.resendAt ?: break
            val left = ((at - System.currentTimeMillis()) / 1000).toInt()
            secondsLeft = left.coerceAtLeast(0)
            if (left <= 0) break
            delay(500)
        }
    }

    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        busy = false
        if (result.resultCode == Activity.RESULT_OK) {
            runCatching { GoogleAuth.idTokenFrom(result.data) }
                .onSuccess { token -> Auth.signInWithGoogle(token).onFailure { error = it.message } }
                .onFailure { error = it.message }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ink.Base, Ink.Void, Ink.Void)))
    ) {
        // lime halo behind the wordmark
        Box(
            Modifier
                .size(340.dp)
                .align(Alignment.TopEnd)
                .padding(top = 40.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Ink.Lime.copy(alpha = 0.16f), Color.Transparent),
                    )
                )
        )
        Box(
            Modifier
                .size(260.dp)
                .align(Alignment.BottomStart)
                .background(
                    Brush.radialGradient(
                        listOf(Ink.Lavender.copy(alpha = 0.10f), Color.Transparent),
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(26.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // The same artwork the launcher shows, so the first screen and the
                // home screen agree about what this app looks like.
                BunIcon(
                    skin = Skin.current,
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(13.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "BunMusic",
                    color = Ink.Text,
                    fontSize = 17.sp,
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp,
                )
            }

            Spacer(Modifier.height(34.dp))

            Text(
                "Music that\nknows you back.",
                color = Ink.Text,
                fontSize = 38.sp,
                lineHeight = 41.sp,
                fontFamily = SerifFamily,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Sign in to carry your likes, playlists and downloads across devices.",
                color = Ink.Muted,
                fontSize = 13.sp,
                fontFamily = SoraFamily,
                lineHeight = 19.sp,
            )

            Spacer(Modifier.height(26.dp))

            if (accounts.isNotEmpty()) {
                Text(
                    "WELCOME BACK",
                    color = Ink.Faint,
                    fontSize = 9.sp,
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.3.sp,
                )
                Spacer(Modifier.height(10.dp))
                accounts.take(3).forEach { acc ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Ink.Card)
                            .clickable {
                                Auth.switchTo(acc.id).fold(
                                    onSuccess = { },
                                    onFailure = { error = it.message },
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(acc, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                acc.name,
                                color = Ink.Text,
                                fontSize = 13.sp,
                                fontFamily = SoraFamily,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                acc.handle,
                                color = Ink.Faint,
                                fontSize = 11.sp,
                                fontFamily = SoraFamily,
                            )
                        }
                        Glyph(G.Forward, Ink.Faint, 14.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(10.dp))
            }

            Segmented(
                options = listOf("Email" to Door.EMAIL, "Phone" to Door.PHONE),
                selected = door,
            ) { door = it; error = null; notice = null; challenge = null; otp = "" }

            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = door,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "door",
            ) { which ->
                when (which) {
                    Door.EMAIL -> Column {
                        if (signUp) {
                            AuthField(
                                label = "NAME",
                                value = name,
                                onValue = { name = it },
                                glyph = G.User,
                                hint = "How should we call you?",
                                keyboard = KeyboardType.Text,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        AuthField(
                            label = "EMAIL",
                            value = email,
                            onValue = { email = it },
                            glyph = G.Mail,
                            hint = "you@example.com",
                            keyboard = KeyboardType.Email,
                        )
                        Spacer(Modifier.height(12.dp))
                        AuthField(
                            label = "PASSWORD",
                            value = password,
                            onValue = { password = it },
                            glyph = G.Lock,
                            hint = if (signUp) "At least 8 characters" else "Your password",
                            keyboard = KeyboardType.Password,
                            secret = !showPassword,
                            trailing = {
                                Box(
                                    Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .clickable { showPassword = !showPassword },
                                    contentAlignment = Alignment.Center,
                                ) { Glyph(if (showPassword) G.EyeOff else G.Eye, Ink.Faint, 16.dp) }
                            },
                        )

                        if (error != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(error!!, color = Ink.Rose, fontSize = 11.5.sp, fontFamily = SoraFamily)
                        }

                        Spacer(Modifier.height(18.dp))
                        PrimaryButton(
                            text = if (signUp) "Create account" else "Sign in",
                            busy = busy,
                        ) {
                            error = null
                            busy = true
                            val result = if (signUp) {
                                Auth.signUp(name, email, password)
                            } else {
                                Auth.signIn(email, password)
                            }
                            busy = false
                            if (result.isFailure) error = result.exceptionOrNull()?.message
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (signUp) "Already have an account?" else "New to BunMusic?",
                                color = Ink.Muted,
                                fontSize = 12.sp,
                                fontFamily = SoraFamily,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (signUp) "Sign in" else "Create one",
                                color = Ink.Lime,
                                fontSize = 12.sp,
                                fontFamily = SoraFamily,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    signUp = !signUp
                                    error = null
                                },
                            )
                        }
                    }

                    Door.PHONE -> Column {
                        if (challenge == null) {
                            AuthField(
                                label = "MOBILE NUMBER",
                                value = phone,
                                onValue = { input ->
                                    phone = input.filter { it.isDigit() || it == '+' }.take(14)
                                },
                                glyph = G.Phone,
                                hint = "+91 98765 43210",
                                keyboard = KeyboardType.Phone,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "We will text a six digit code. Standard rates apply.",
                                color = Ink.Faint,
                                fontSize = 11.sp,
                                fontFamily = SoraFamily,
                                lineHeight = 16.sp,
                            )
                            if (error != null) {
                                Spacer(Modifier.height(10.dp))
                                Text(error!!, color = Ink.Rose, fontSize = 11.5.sp, fontFamily = SoraFamily)
                            }
                            Spacer(Modifier.height(18.dp))
                            PrimaryButton("Send code", busy = busy) {
                                error = null
                                busy = true
                                val result = Auth.requestOtp(phone)
                                busy = false
                                result.fold(
                                    onSuccess = {
                                        challenge = it
                                        notice = if (it.demo) "Demo code: ${it.code}" else "Code sent to ${Auth.prettyPhone(it.phone)}"
                                    },
                                    onFailure = { error = it.message },
                                )
                            }
                        } else {
                            Text(
                                "Enter the 6 digit code",
                                color = Ink.Text,
                                fontSize = 15.sp,
                                fontFamily = SoraFamily,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Sent to ${Auth.prettyPhone(challenge!!.phone)}",
                                color = Ink.Muted,
                                fontSize = 12.sp,
                                fontFamily = SoraFamily,
                            )

                            Spacer(Modifier.height(14.dp))
                            TextField(
                                value = otp,
                                onValueChange = { input ->
                                    otp = input.filter { it.isDigit() }.take(6)
                                    error = null
                                },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Ink.Text,
                                    fontSize = 24.sp,
                                    fontFamily = SoraFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 10.sp,
                                    textAlign = TextAlign.Center,
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Done,
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Ink.Card,
                                    unfocusedContainerColor = Ink.Card,
                                    focusedIndicatorColor = Ink.Lime,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = Ink.Lime,
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            if (notice != null) {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Ink.Lime.copy(alpha = 0.10f))
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Glyph(G.Shield, Ink.Lime, 13.dp)
                                    Spacer(Modifier.width(7.dp))
                                    Text(notice!!, color = Ink.Lime, fontSize = 11.sp, fontFamily = SoraFamily)
                                }
                            }
                            if (error != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(error!!, color = Ink.Rose, fontSize = 11.5.sp, fontFamily = SoraFamily)
                            }

                            Spacer(Modifier.height(18.dp))
                            PrimaryButton("Verify and continue", busy = busy, enabled = otp.length == 6) {
                                error = null
                                busy = true
                                val result = Auth.verifyOtp(challenge!!.phone, otp)
                                busy = false
                                if (result.isFailure) error = result.exceptionOrNull()?.message
                            }

                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (secondsLeft > 0) "Resend in ${secondsLeft}s" else "Didn't get it?",
                                    color = Ink.Muted,
                                    fontSize = 12.sp,
                                    fontFamily = SoraFamily,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Resend",
                                    color = if (secondsLeft > 0) Ink.Faint else Ink.Lime,
                                    fontSize = 12.sp,
                                    fontFamily = SoraFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable(enabled = secondsLeft == 0) {
                                        error = null
                                        Auth.requestOtp(challenge!!.phone).fold(
                                            onSuccess = {
                                                challenge = it
                                                notice = if (it.demo) "Demo code: ${it.code}" else "Code sent again"
                                            },
                                            onFailure = { error = it.message },
                                        )
                                    },
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "Change number",
                                    color = Ink.Muted,
                                    fontSize = 12.sp,
                                    fontFamily = SoraFamily,
                                    modifier = Modifier.clickable {
                                        challenge = null
                                        otp = ""
                                        error = null
                                        notice = null
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(Ink.Hairline))
                Text(
                    "or",
                    color = Ink.Faint,
                    fontSize = 10.sp,
                    fontFamily = SoraFamily,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Box(Modifier.weight(1f).height(1.dp).background(Ink.Hairline))
            }

            Spacer(Modifier.height(16.dp))

            GoogleButton(busy) {
                error = null
                val intent = GoogleAuth.signInIntent(context, settings.googleClientId)
                if (intent == null) {
                    googleSetup = true
                } else {
                    busy = true
                    runCatching { googleLauncher.launch(intent) }
                        .onFailure {
                            busy = false
                            error = it.message ?: "Could not open Google sign-in"
                        }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "By continuing you agree to keep the music yours — no ads, no tracking, " +
                    "and nothing leaves this device unless you back it up.",
                color = Ink.Faint,
                fontSize = 10.sp,
                fontFamily = SoraFamily,
                lineHeight = 15.sp,
            )

            Spacer(Modifier.height(14.dp))

            Box(
                Modifier.fillMaxWidth().clickable { onGuest() }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Continue without an account",
                    color = Ink.Muted,
                    fontSize = 12.sp,
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (googleSetup) {
        GoogleSetupSheet(
            current = settings.googleClientId,
            onDismiss = { googleSetup = false },
            onSave = { id ->
                Prefs.update { it.copy(googleClientId = id.trim()) }
                googleSetup = false
                notice = if (id.isBlank()) null else "Google client ID saved — tap Continue with Google"
            },
        )
    }
}

@Composable
private fun GoogleButton(busy: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF6F4EE))
            .clickable(enabled = !busy) { onClick() }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(G.Google, Color(0xFF1A1A1A), 18.dp, 2.4.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            if (busy) "Opening Google…" else "Continue with Google",
            color = Color(0xFF141414),
            fontSize = 13.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PrimaryButton(text: String, busy: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val active = enabled && !busy
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) Ink.Lime else Ink.Line)
            .clickable(enabled = active) { onClick() }
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (busy) "One moment…" else text,
            color = if (active) Ink.OnAccent else Ink.Faint,
            fontSize = 13.5.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun AuthField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    glyph: G,
    hint: String,
    keyboard: KeyboardType,
    secret: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column {
        Text(
            label,
            color = Ink.Faint,
            fontSize = 9.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(Ink.Card)
                .border(1.dp, Ink.Hairline, RoundedCornerShape(15.dp))
                .padding(horizontal = 14.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(glyph, Ink.Faint, 17.dp)
            Spacer(Modifier.width(10.dp))
            TextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                placeholder = {
                    Text(hint, color = Ink.Faint, fontSize = 13.sp, fontFamily = SoraFamily)
                },
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Ink.Text,
                    fontSize = 14.sp,
                    fontFamily = SoraFamily,
                ),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboard,
                    imeAction = ImeAction.Next,
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Ink.Lime,
                ),
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
    }
}

@Composable
private fun Segmented(options: List<Pair<String, Door>>, selected: Door, onSelect: (Door) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.Card)
            .padding(4.dp),
    ) {
        options.forEach { (label, value) ->
            val active = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) Color.White.copy(alpha = 0.09f) else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) Ink.Text else Ink.Faint,
                    fontSize = 12.5.sp,
                    fontFamily = SoraFamily,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
fun Avatar(account: Auth.Account, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val tint = remember(account.id) {
        val palette = listOf(Ink.Lime, Ink.Lavender, Ink.Mint, Ink.Rose)
        palette[(account.id.hashCode().let { if (it < 0) -it else it }) % palette.size]
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.85f), tint.copy(alpha = 0.45f)))),
        contentAlignment = Alignment.Center,
    ) {
        if (account.avatarUrl != null) {
            ArtImage(account.avatarUrl, null, Modifier.fillMaxSize(), CircleShape)
        } else {
            Text(
                account.initials,
                color = Ink.OnAccent,
                fontSize = (size.value * 0.36f).sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Google Sign-In is the one door that needs a key we cannot invent: an OAuth client
 * registered against this app's package name and signing certificate. Rather than
 * fake it, the app explains the two-minute setup and takes the id at runtime.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GoogleSetupSheet(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    var value by remember { mutableStateOf(current) }
    val sha1 = remember { GoogleAuth.signingSha1(context) }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Ink.Sheet,
        contentColor = Ink.Text,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                "Connect Google sign-in",
                color = Ink.Text,
                fontSize = 17.sp,
                fontFamily = SerifFamily,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Google only issues tokens to an OAuth client that knows this app. " +
                    "Create a Web client in Google Cloud Console, then paste its client ID here.",
                color = Ink.Muted,
                fontSize = 12.sp,
                fontFamily = SoraFamily,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "STEP 1 · REGISTER THIS SIGNATURE",
                color = Ink.Faint,
                fontSize = 9.sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink.Card)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "app.bunmusic\nSHA-1 $sha1",
                    color = Ink.Text,
                    fontSize = 10.5.sp,
                    fontFamily = SoraFamily,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "STEP 2 · PASTE THE CLIENT ID",
                color = Ink.Faint,
                fontSize = 9.sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = {
                    Text("1234567890-abc.apps.googleusercontent.com", color = Ink.Faint, fontSize = 11.sp)
                },
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Ink.Text,
                    fontSize = 12.sp,
                    fontFamily = SoraFamily,
                ),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ink.Lime,
                    unfocusedBorderColor = Ink.Line,
                    cursorColor = Ink.Lime,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(18.dp))
            PrimaryButton("Save client ID", busy = false, enabled = value.isNotBlank()) {
                onSave(value)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().clickable { onDismiss() }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Not now", color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily)
            }
        }
    }
}
