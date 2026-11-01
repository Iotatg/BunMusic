package app.bunmusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SoraFamily

/**
 * The command deck: every verb in the app, searchable.
 *
 * Not a settings page and not a launcher — a single field that answers "can this
 * app do X" without making you remember which screen X lives on. Typing filters
 * across the title, the hint, the group and the keywords, and every result is a
 * real action rather than a navigation shortcut dressed up as one.
 */
@Composable
fun CommandPalette(host: CommandHost, onDismiss: () -> Unit) {
    val commands = remember(host) { buildCommands(host) }
    var query by remember { mutableStateOf("") }
    val results = remember(query, commands) { filterCommands(commands, query) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    BackHandler { onDismiss() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable { onDismiss() },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                // Swallow taps so the sheet does not dismiss itself when the
                // user misses a row by two pixels.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Ink.Sheet)
                    .border(1.dp, Ink.Hairline, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(G.Search, Ink.Muted, 17.dp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            "Search ${commands.size} commands",
                            color = Ink.Faint,
                            fontSize = 15.sp,
                            fontFamily = SoraFamily,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Ink.Text,
                            fontSize = 15.sp,
                            fontFamily = SoraFamily,
                        ),
                        cursorBrush = SolidColor(Ink.Lime),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            results.firstOrNull()?.let {
                                keyboard?.hide()
                                it.run()
                                host.close()
                            }
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable { query = "" },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(G.Close, Ink.Faint, 12.dp) }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                if (query.isBlank()) {
                    "${commands.size} commands · type to filter"
                } else {
                    "${results.size} of ${commands.size}"
                },
                color = Ink.Faint,
                fontSize = 10.5.sp,
                fontFamily = SoraFamily,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
            )

            if (results.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Nothing answers to “$query”",
                        color = Ink.Muted,
                        fontSize = 13.sp,
                        fontFamily = SoraFamily,
                    )
                }
            }

            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Ink.Sheet.copy(alpha = 0.97f))
                    .border(1.dp, Ink.Hairline, RoundedCornerShape(20.dp)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
            ) {
                itemsIndexed(results, key = { _, c -> c.id }) { index, command ->
                    val previous = results.getOrNull(index - 1)
                    if (previous == null || previous.group != command.group) {
                        Text(
                            command.group.uppercase(),
                            color = Ink.Faint,
                            fontSize = 9.5.sp,
                            fontFamily = SoraFamily,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                keyboard?.hide()
                                command.run()
                                host.close()
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Ink.Field),
                            contentAlignment = Alignment.Center,
                        ) { Glyph(command.glyph, Ink.Muted, 14.dp) }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                command.title,
                                color = Ink.Text,
                                fontSize = 13.5.sp,
                                fontFamily = SoraFamily,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                            if (command.hint.isNotBlank()) {
                                Text(
                                    command.hint,
                                    color = Ink.Faint,
                                    fontSize = 10.5.sp,
                                    fontFamily = SoraFamily,
                                    maxLines = 1,
                                )
                            }
                        }
                        Glyph(G.Forward, Ink.Faint.copy(alpha = 0.7f), 11.dp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Runs instantly · nothing is applied twice",
                    color = Ink.Faint,
                    fontSize = 10.sp,
                    fontFamily = SoraFamily,
                )
                Text(
                    "Tap outside to close",
                    color = Ink.Faint,
                    fontSize = 10.sp,
                    fontFamily = SoraFamily,
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
        }
    }
}
