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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Glyph
import app.bunmusic.core.Ink
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Cache
import app.bunmusic.data.Downloads
import app.bunmusic.data.Prefs
import app.bunmusic.data.Storage
import app.bunmusic.data.Store

/**
 * Storage, split into the four things that actually take space.
 *
 * The old row said "1.2 GB used" and offered one button. That number is useless
 * without knowing which part is music you asked to keep and which part is
 * artwork you never look at. This screen names each bucket, draws its share, and
 * only offers to delete the ones that can be rebuilt.
 */
@Composable
fun StorageScreen(
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit = {},
    onToast: (String) -> Unit = {},
) {
    val settings by Prefs.settings.collectAsState()
    var tick by remember { mutableStateOf(0) }
    val limitBytes = settings.cacheLimitMb.toLong() * 1024L * 1024L

    val downloadBytes = remember(tick) { Downloads.totalBytes() }
    val downloadCount = remember(tick) { Downloads.entries.value.size }
    val streamBytes = remember(tick) { Cache.streamBytes() }
    val imageBytes = remember(tick) { Cache.imageBytes() }
    // The library is text: roughly the encoded registry plus the play log.
    val libraryBytes = remember(tick) {
        (Store.allTracks().size * 180L) + (Store.events().size * 24L)
    }

    val buckets = Storage.buckets(downloadBytes, downloadCount, streamBytes, imageBytes, libraryBytes)
    val total = Storage.total(buckets)
    val plan = Storage.cleanupPlan(buckets, limitBytes)

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 30.dp),
    ) {
        item { BackBar("Storage", onBack) }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    Storage.human(total),
                    color = Ink.Text,
                    fontSize = 38.sp,
                    fontFamily = app.bunmusic.core.SerifFamily,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    Storage.verdict(total, limitBytes),
                    color = if (total > limitBytes) Ink.Rose else Ink.Muted,
                    fontSize = 12.sp,
                    fontFamily = SoraFamily,
                )
            }
        }

        item {
            // One stacked bar: every bucket's share of the whole, in its own tone.
            Row(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Ink.Field),
            ) {
                buckets.filter { it.bytes > 0 }.forEach { b ->
                    Box(
                        Modifier
                            .weight(b.bytes.toFloat())
                            .fillMaxSize()
                            .background(toneFor(b.id)),
                    )
                }
            }
        }

        items(buckets.size) { i ->
            val b = buckets[i]
            Row(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.width(4.dp).height(34.dp).clip(RoundedCornerShape(2.dp)).background(toneFor(b.id)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.label, color = Ink.Text, fontSize = 14.sp, fontFamily = SoraFamily, fontWeight = FontWeight.Medium)
                    Text(b.note, color = Ink.Faint, fontSize = 10.5.sp, fontFamily = SoraFamily)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Storage.human(b.bytes), color = Ink.Text, fontSize = 13.sp, fontFamily = SoraFamily)
                    if (b.removable && b.bytes > 0) {
                        Text(
                            "Clear",
                            color = Ink.Lime,
                            fontSize = 10.5.sp,
                            fontFamily = SoraFamily,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    when (b.action) {
                                        Storage.Action.CLEAR_STREAM -> Cache.clearStream()
                                        Storage.Action.CLEAR_IMAGES -> Cache.clearImages()
                                        else -> Unit
                                    }
                                    tick++
                                    onToast("${b.label} cleared")
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                if (plan.isEmpty()) {
                    Text(
                        "Nothing to clean: you are under the ${settings.cacheLimitMb} MB limit.",
                        color = Ink.Mint,
                        fontSize = 11.5.sp,
                        fontFamily = SoraFamily,
                    )
                } else {
                    Text(
                        "Over the limit by ${Storage.human(total - limitBytes)}. " +
                            "Clearing ${plan.joinToString(" and ") { it.label.lowercase() }} is enough.",
                        color = Ink.Muted,
                        fontSize = 11.5.sp,
                        fontFamily = SoraFamily,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledBtn("Auto-clean", G.Trash) {
                            plan.forEach {
                                when (it.action) {
                                    Storage.Action.CLEAR_STREAM -> Cache.clearStream()
                                    Storage.Action.CLEAR_IMAGES -> Cache.clearImages()
                                    else -> Unit
                                }
                            }
                            tick++
                            onToast("Freed ${Storage.human(plan.sumOf { it.bytes })}")
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    "Downloads are never removed automatically. They are the only bytes here you asked for.",
                    color = Ink.Faint,
                    fontSize = 10.5.sp,
                    fontFamily = SoraFamily,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ink.Field)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(G.Download, Ink.Muted, 16.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "$downloadCount downloads · ${Storage.human(downloadBytes)}",
                        color = Ink.Text,
                        fontSize = 12.sp,
                        fontFamily = SoraFamily,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Manage",
                        color = Ink.Lime,
                        fontSize = 11.sp,
                        fontFamily = SoraFamily,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenDownloads() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

private fun toneFor(id: String): Color = when (id) {
    "downloads" -> Ink.Mint
    "streams" -> Ink.Lime
    "images" -> Ink.Lavender
    else -> Ink.Faint
}
