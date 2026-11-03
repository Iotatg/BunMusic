package app.bunmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.AppVersion
import app.bunmusic.core.CrashGuard
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SoraFamily

/**
 * Settings › Stability.
 *
 * The page that answers "did it crash, when, and where" without a debugger
 * attached. It reads the same file [CrashGuard] writes, so what is on screen is
 * exactly what would be sent if somebody reported a bug.
 *
 * The two test buttons exist for the same reason the notification test card
 * does: a claim that the app recovers from a crash is worth nothing until it has
 * been made to happen on the phone that is being held.
 */
@Composable
fun StabilityScreen(onBack: () -> Unit, onToast: (String) -> Unit) {
    var version by remember { mutableIntStateOf(0) }
    val entries = remember(version) { CrashGuard.history() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var confirmCrash by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()),
    ) {
        BackBar("Stability", onBack)

        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Display("Stability", 29)
            Spacer(Modifier.height(6.dp))
            Text(
                "BunMusic keeps a note of anything that went wrong. Nothing is uploaded — " +
                    "this file lives in the app's own folder.",
                color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill(AppVersion.VERSION, "Version", modifier = Modifier.weight(1f))
                StatPill(if (entries.isEmpty()) "None" else "${entries.size}", "Recorded", modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("What is protected")
            Spacer(Modifier.height(8.dp))
            GuardRow("Uncaught crash", "Written down, then the app comes back on Home")
            GuardRow("Repeated crash", "Two within 90 seconds stops the restart instead of looping")
            GuardRow("Background work", "A failed prefetch or radio call is logged, not fatal")
            GuardRow("A broken screen", "Reopens on Home and names the page that failed")
            GuardRow("Startup", "A corrupt preference file can no longer stop the app opening")

            Spacer(Modifier.height(20.dp))
            SectionTitle("Last problem")
            Spacer(Modifier.height(8.dp))

            val last = entries.lastOrNull()
            if (last == null) {
                Text(
                    "Nothing recorded. Either this is a clean install or the bugs are behaving.",
                    color = Ink.Faint, fontSize = 12.sp, fontFamily = SoraFamily, lineHeight = 18.sp,
                )
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (Ink.isLight) Ink.Card else Ink.Sheet)
                        .border(1.dp, Ink.Hairline, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Text(last.clock, color = Ink.Faint, fontSize = 10.5.sp, fontFamily = SoraFamily)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        last.headline,
                        color = Ink.Text, fontSize = 12.5.sp, fontFamily = SoraFamily,
                        fontWeight = FontWeight.Medium, lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        last.stack.lineSequence().take(8).joinToString("\n"),
                        color = Ink.Faint, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp,
                    )
                }
                if (entries.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "…and ${entries.size - 1} older",
                        color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostBtn("Copy report", G.Share) {
                    clipboard.setText(AnnotatedString(CrashGuard.report()))
                    onToast("Report copied")
                }
                if (entries.isNotEmpty()) {
                    GhostBtn("Clear", G.Trash) {
                        CrashGuard.clear()
                        version++
                        onToast("Crash log cleared")
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionTitle("Prove it")
            Spacer(Modifier.height(8.dp))
            Text(
                "Two checks, in increasing order of drama. The first writes a fake entry so you " +
                    "can see the log work. The second really does crash the app — it should come " +
                    "straight back with a note at the bottom.",
                color = Ink.Muted, fontSize = 11.5.sp, fontFamily = SoraFamily, lineHeight = 17.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostBtn("Log a test entry", G.Check) {
                    CrashGuard.record(
                        IllegalStateException("Test entry — nothing is broken"),
                        "stability:test",
                    )
                    version++
                    onToast("Test entry written")
                }
                GhostBtn("Crash for real", G.Offline) { confirmCrash = true }
            }

            Spacer(Modifier.height(26.dp))
            Text(
                "BunMusic ${AppVersion.LABEL} · ${context.packageName}",
                color = Ink.Faint, fontSize = 10.5.sp, fontFamily = SoraFamily,
            )
            Spacer(Modifier.height(30.dp))
        }
    }

    if (confirmCrash) {
        AlertDialog(
            onDismissRequest = { confirmCrash = false },
            containerColor = Ink.Sheet,
            title = { Text("Crash on purpose?", color = Ink.Text, fontFamily = SoraFamily, fontSize = 16.sp) },
            text = {
                Text(
                    "BunMusic will close and reopen itself. Nothing is lost — the queue and the " +
                        "position are saved. If it does not come back, the guard is working as " +
                        "designed: two crashes in a row stops the restart.",
                    color = Ink.Muted, fontSize = 12.sp, lineHeight = 18.sp,
                )
            },
            confirmButton = {
                Text(
                    "Crash now",
                    color = Ink.Rose,
                    fontSize = 13.sp,
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            confirmCrash = false
                            // Off the main thread on purpose: this is the path a
                            // real background failure takes, and it is the one the
                            // handler has to survive.
                            Thread {
                                throw IllegalStateException("Deliberate crash from Settings › Stability")
                            }.start()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
            dismissButton = {
                Text(
                    "Cancel",
                    color = Ink.Muted,
                    fontSize = 13.sp,
                    fontFamily = SoraFamily,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { confirmCrash = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
        )
    }
}

@Composable
private fun GuardRow(title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Top,
    ) {
        Glyph(G.Check, Ink.Lime, 13.dp)
        Spacer(Modifier.height(0.dp))
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, color = Ink.Text, fontSize = 12.5.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
            Text(body, color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily, lineHeight = 16.sp)
        }
    }
}
