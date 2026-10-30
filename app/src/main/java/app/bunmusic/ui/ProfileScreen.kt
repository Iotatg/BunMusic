package app.bunmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SerifFamily
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Auth
import app.bunmusic.data.Downloads
import app.bunmusic.data.Store

/**
 * Everything about the person listening: who they are, what they own, and the
 * two buttons that matter — switch and sign out.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onToast: (String) -> Unit,
) {
    val account by Auth.account.collectAsState()
    val accounts by Auth.accounts.collectAsState()
    val downloads by Downloads.entries.collectAsState()

    var editing by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var linkingEmail by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingSignOut by remember { mutableStateOf(false) }

    val stats = remember { Store.stats() }
    val likes = remember { Store.likes().size }
    val playlists = remember { Store.playlists().size }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item { BackBar("Account", onBack) }

        val acc = account
        if (acc == null) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(74.dp).clip(CircleShape).background(Ink.Card),
                        contentAlignment = Alignment.Center,
                    ) { Glyph(G.User, Ink.Faint, 30.dp) }
                    Spacer(Modifier.height(16.dp))
                    Text("You're browsing as a guest", color = Ink.Text, fontSize = 17.sp, fontFamily = SerifFamily)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Sign in and your likes, playlists and downloads follow you to every device.",
                        color = Ink.Muted,
                        fontSize = 12.sp,
                        fontFamily = SoraFamily,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    FilledBtn("Sign in", G.Logout, onClick = onSignIn)
                }
            }
            return@LazyColumn
        }

        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Avatar(acc, 86.dp)
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Ink.Lime)
                            .border(3.dp, Ink.Void, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Glyph(G.Check, Ink.OnAccent, 12.dp, 2.6.dp) }
                }
                Spacer(Modifier.height(14.dp))
                Text(acc.name, color = Ink.Text, fontSize = 26.sp, fontFamily = SerifFamily)
                Spacer(Modifier.height(4.dp))
                Text(acc.handle, color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProviderChip(acc)
                    if (acc.isVerified) VerifiedChip()
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatTile("LIKES", "$likes", Modifier.weight(1f))
                StatTile("PLAYLISTS", "$playlists", Modifier.weight(1f))
                StatTile("OFFLINE", "${downloads.size}", Modifier.weight(1f))
                StatTile("HOURS", "${stats.totalMs / 3_600_000}", Modifier.weight(1f))
            }
        }

        item { ProfileGroupLabel("Account") }
        item {
            ProfileCard {
                ProfileRow("Display name", acc.name, G.Edit) { editing = true }
                ProfileDivider()
                when {
                    acc.provider == Auth.Provider.EMAIL -> ProfileRow(
                        "Password", "Change your sign-in password", G.Key,
                    ) { changingPassword = true }

                    acc.email == null -> ProfileRow(
                        "Add an email", "Useful for backup and recovery", G.Mail,
                    ) { linkingEmail = true }

                    else -> ProfileRow("Email", acc.email ?: "", G.Mail) { }
                }
                ProfileDivider()
                ProfileRow(
                    "Signed in with",
                    when (acc.provider) {
                        Auth.Provider.EMAIL -> "Email and password"
                        Auth.Provider.PHONE -> Auth.prettyPhone(acc.phone)
                        Auth.Provider.GOOGLE -> "Google"
                    },
                    G.Shield,
                ) { }
            }
        }

        if (accounts.size > 1) {
            item { ProfileGroupLabel("Switch account") }
            item {
                ProfileCard {
                    accounts.filter { it.id != acc.id }.forEach { other ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Auth.switchTo(other.id).fold(
                                        onSuccess = { onToast("Switched to ${other.name}") },
                                        onFailure = { onToast(it.message ?: "Could not switch") },
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(other, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    other.name, color = Ink.Text, fontSize = 13.sp,
                                    fontFamily = SoraFamily, fontWeight = FontWeight.Medium,
                                )
                                Text(other.handle, color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily)
                            }
                            Glyph(G.Forward, Ink.Faint, 14.dp)
                        }
                        ProfileDivider()
                    }
                    ProfileRow("Add another account", "Sign in with a different email or number", G.Plus) {
                        Auth.signOut()
                        onSignIn()
                    }
                }
            }
        }

        item { ProfileGroupLabel("Session") }
        item {
            ProfileCard {
                ProfileRow("Sign out", "Your library stays on this device", G.Logout) {
                    confirmingSignOut = true
                }
                ProfileDivider()
                ProfileRow("Delete account", "Removes this account and everything in it", G.Trash) {
                    confirmingDelete = true
                }
            }
        }

        item {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Passwords are stretched with PBKDF2 (120,000 rounds) and never stored in plain text. " +
                        "Each account keeps its own library, so a shared phone never mixes two people's playlists.",
                    color = Ink.Faint,
                    fontSize = 11.sp,
                    fontFamily = SoraFamily,
                    lineHeight = 17.sp,
                )
            }
        }
    }

    if (editing) {
        TextPromptDialog(
            title = "Display name",
            initial = account?.name ?: "",
            confirm = "Save",
            onDismiss = { editing = false },
        ) { value ->
            Auth.rename(value).fold(
                onSuccess = { onToast("Name updated") },
                onFailure = { onToast(it.message ?: "Could not save") },
            )
            editing = false
        }
    }

    if (changingPassword) {
        PasswordDialog(onDismiss = { changingPassword = false }) { old, fresh ->
            Auth.changePassword(old, fresh).fold(
                onSuccess = { onToast("Password updated") },
                onFailure = { onToast(it.message ?: "Could not update") },
            )
            changingPassword = false
        }
    }

    if (linkingEmail) {
        LinkEmailDialog(onDismiss = { linkingEmail = false }) { email, password ->
            Auth.linkEmail(email, password).fold(
                onSuccess = { onToast("Email linked") },
                onFailure = { onToast(it.message ?: "Could not link") },
            )
            linkingEmail = false
        }
    }

    if (confirmingSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            body = "Your likes, playlists and downloads stay on this device. You can sign back in any time.",
            confirm = "Sign out",
            onDismiss = { confirmingSignOut = false },
        ) {
            Auth.signOut()
            confirmingSignOut = false
            onSignIn()
        }
    }

    if (confirmingDelete) {
        ConfirmDialog(
            title = "Delete this account?",
            body = "This removes the account and its library from this device. It cannot be undone.",
            confirm = "Delete",
            danger = true,
            onDismiss = { confirmingDelete = false },
        ) {
            val outcome = Auth.deleteAccount()
            confirmingDelete = false
            outcome.fold(
                onSuccess = { onToast("Account deleted"); onSignIn() },
                onFailure = { onToast(it.message ?: "Could not delete") },
            )
        }
    }
}

@Composable
private fun ProviderChip(acc: Auth.Account) {
    val pair = when (acc.provider) {
        Auth.Provider.EMAIL -> G.Mail to "Email"
        Auth.Provider.PHONE -> G.Phone to "Phone"
        Auth.Provider.GOOGLE -> G.Google to "Google"
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(Ink.Hairline)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(pair.first, Ink.Muted, 12.dp)
        Spacer(Modifier.width(6.dp))
        Text(pair.second, color = Ink.Muted, fontSize = 11.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun VerifiedChip() {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Ink.Mint.copy(alpha = 0.13f))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(G.Check, Ink.Mint, 11.dp, 2.6.dp)
        Spacer(Modifier.width(5.dp))
        Text("Verified", color = Ink.Mint, fontSize = 11.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Ink.Card, Ink.Raised)))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Ink.Text, fontSize = 18.sp, fontFamily = SerifFamily)
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = Ink.Faint,
            fontSize = 10.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.9.sp,
        )
    }
}

@Composable
private fun ProfileGroupLabel(text: String) {
    Text(
        text.uppercase(),
        color = Ink.Faint,
        fontSize = 10.sp,
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.9.sp,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun ProfileCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Ink.Card)
            .padding(vertical = 4.dp),
    ) { content() }
}

@Composable
private fun ProfileDivider() {
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Ink.Hairline))
}

@Composable
private fun ProfileRow(title: String, subtitle: String, glyph: G, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Ink.Hairline),
            contentAlignment = Alignment.Center,
        ) { Glyph(glyph, Ink.Muted, 15.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.Text, fontSize = 13.5.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily)
        }
        Glyph(G.Forward, Ink.Faint, 14.dp)
    }
}

@Composable
private fun dialogFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Ink.Lime,
    unfocusedBorderColor = Ink.Line,
    focusedTextColor = Ink.Text,
    unfocusedTextColor = Ink.Text,
    cursorColor = Ink.Lime,
)

@Composable
private fun TextPromptDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Card,
        titleContentColor = Ink.Text,
        title = { Text(title, fontFamily = SerifFamily, fontSize = 20.sp) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                colors = dialogFieldColors(),
                shape = RoundedCornerShape(12.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(confirm, color = Ink.Lime, fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Ink.Muted, fontFamily = SoraFamily) }
        },
    )
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var old by remember { mutableStateOf("") }
    var fresh by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Card,
        titleContentColor = Ink.Text,
        title = { Text("Change password", fontFamily = SerifFamily, fontSize = 20.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = old,
                    onValueChange = { old = it },
                    singleLine = true,
                    placeholder = { Text("Current password", color = Ink.Faint, fontSize = 12.sp) },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = dialogFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = fresh,
                    onValueChange = { fresh = it },
                    singleLine = true,
                    placeholder = { Text("New password (8+ characters)", color = Ink.Faint, fontSize = 12.sp) },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = dialogFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(old, fresh) }) {
                Text("Update", color = Ink.Lime, fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Ink.Muted, fontFamily = SoraFamily) }
        },
    )
}

@Composable
private fun LinkEmailDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Card,
        titleContentColor = Ink.Text,
        title = { Text("Add an email", fontFamily = SerifFamily, fontSize = 20.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    singleLine = true,
                    placeholder = { Text("you@example.com", color = Ink.Faint, fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = dialogFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    placeholder = { Text("Choose a password", color = Ink.Faint, fontSize = 12.sp) },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = dialogFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(email, password) }) {
                Text("Link", color = Ink.Lime, fontFamily = SoraFamily, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Ink.Muted, fontFamily = SoraFamily) }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    danger: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Card,
        titleContentColor = Ink.Text,
        textContentColor = Ink.Muted,
        title = { Text(title, fontFamily = SerifFamily, fontSize = 20.sp) },
        text = { Text(body, fontFamily = SoraFamily, fontSize = 12.5.sp, lineHeight = 18.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirm,
                    color = if (danger) Ink.Rose else Ink.Lime,
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Ink.Muted, fontFamily = SoraFamily) }
        },
    )
}
