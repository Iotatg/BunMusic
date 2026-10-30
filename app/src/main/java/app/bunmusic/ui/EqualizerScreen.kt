package app.bunmusic.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SerifFamily
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Eq
import app.bunmusic.data.Prefs

/**
 * The equaliser, driven by whatever the device's DSP actually exposes.
 * No fake five-band graphic: if the hardware has seven bands, seven appear.
 */
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val eq by Eq.state.collectAsState()
    val settings by Prefs.settings.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item { BackBar("Equaliser", onBack) }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Display("Equaliser", 30)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (eq.available) "${eq.bands.size} bands · hardware audio effects" else "Not available on this output",
                    color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily,
                )
            }
        }

        item {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (eq.enabled) {
                                Brush.horizontalGradient(listOf(Ink.Lime.copy(alpha = 0.16f), Ink.Card))
                            } else {
                                Brush.horizontalGradient(listOf(Ink.Card, Ink.Card))
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (eq.enabled) Ink.Lime else Color.White.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center,
                    ) { Glyph(G.Sliders, if (eq.enabled) Ink.OnAccent else Ink.Muted, 17.dp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Equaliser",
                            color = Ink.Text, fontSize = 14.sp, fontFamily = SoraFamily,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (eq.enabled) "Shaping playback" else "Off — flat output",
                            color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily,
                        )
                    }
                    Switch(
                        checked = eq.enabled,
                        enabled = eq.available,
                        onCheckedChange = { on ->
                            Prefs.update { it.copy(eqEnabled = on) }
                            Eq.setEnabled(on)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Ink.OnAccent,
                            checkedTrackColor = Ink.Lime,
                            uncheckedThumbColor = Ink.Muted,
                            uncheckedTrackColor = Ink.Line,
                        ),
                    )
                }
            }
        }

        if (!eq.available) {
            item {
                EmptyState(
                    G.Sliders,
                    "No effects available",
                    "This device does not expose an equaliser on the current audio output. " +
                        "Start a track, then come back — effects attach to the player's audio session.",
                )
            }
            return@LazyColumn
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "PRESETS",
                    color = Ink.Faint,
                    fontSize = 9.sp,
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.3.sp,
                )
            }
        }

        item {
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Eq.presets.size) { i ->
                    val name = Eq.presets[i]
                    Chip(name, selected = eq.preset == name) {
                        if (!eq.enabled) {
                            Prefs.update { it.copy(eqEnabled = true) }
                            Eq.setEnabled(true)
                        }
                        Eq.setPreset(name)
                    }
                }
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Ink.Card)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                eq.bands.forEachIndexed { index, band ->
                    BandColumn(
                        label = band.label,
                        db = band.levelDb,
                        min = band.minMb / 100f,
                        max = band.maxMb / 100f,
                        enabled = eq.enabled,
                    ) { value ->
                        Eq.setBand(index, (value * 100).toInt())
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Ink.Card)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Bass boost",
                            color = Ink.Text, fontSize = 13.5.sp, fontFamily = SoraFamily,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            if (eq.bass == 0) "Off" else "${eq.bass / 10}%",
                            color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily,
                        )
                    }
                    Slider(
                        value = eq.bass.toFloat(),
                        onValueChange = { Eq.setBass(it.toInt()) },
                        valueRange = 0f..1000f,
                        enabled = eq.bassAvailable && eq.enabled,
                        modifier = Modifier.width(150.dp).height(28.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Ink.Lime,
                            activeTrackColor = Ink.Lime,
                            inactiveTrackColor = Color.White.copy(alpha = 0.12f),
                        ),
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Ink.Card)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Volume normalisation",
                            color = Ink.Text, fontSize = 13.5.sp, fontFamily = SoraFamily,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "Lifts quiet masters without clipping",
                            color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily,
                        )
                    }
                    Switch(
                        checked = settings.normalize,
                        onCheckedChange = { on ->
                            Prefs.update { it.copy(normalize = on) }
                            Eq.setNormalisation(on)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Ink.OnAccent,
                            checkedTrackColor = Ink.Lime,
                            uncheckedThumbColor = Ink.Muted,
                            uncheckedTrackColor = Ink.Line,
                        ),
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .clickable { Eq.reset() }
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(G.Refresh, Ink.Muted, 14.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset to flat", color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily)
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    "Bands are read from the device's audio DSP and adjusted in millibels. " +
                        "Effects attach to BunMusic's own audio session, so nothing else on the phone is affected.",
                    color = Ink.Faint,
                    fontSize = 11.sp,
                    fontFamily = SoraFamily,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun BandColumn(
    label: String,
    db: Float,
    min: Float,
    max: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    var local by remember { mutableStateOf(db) }
    androidx.compose.runtime.LaunchedEffect(db) { local = db }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            String.format("%+.0f", local),
            color = if (local == 0f) Ink.Faint else Ink.Lime,
            fontSize = 9.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.height(150.dp).width(38.dp), contentAlignment = Alignment.Center) {
            Slider(
                value = local,
                onValueChange = {
                    local = it
                    onChange(it)
                },
                valueRange = min..max,
                enabled = enabled,
                modifier = Modifier.width(140.dp).rotate(-90f),
                colors = SliderDefaults.colors(
                    thumbColor = if (enabled) Ink.Lime else Ink.Faint,
                    activeTrackColor = if (enabled) Ink.Lime.copy(alpha = 0.7f) else Ink.Line,
                    inactiveTrackColor = Color.White.copy(alpha = 0.10f),
                ),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = Ink.Muted,
            fontSize = 9.5.sp,
            fontFamily = SoraFamily,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}
