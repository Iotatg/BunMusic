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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bunmusic.core.G
import app.bunmusic.core.Ink
import app.bunmusic.core.SoraFamily
import app.bunmusic.data.Listening
import app.bunmusic.data.Store
import app.bunmusic.data.Wrapped
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The year in review.
 *
 * Ten full-width cards you scroll through once a year, built from the same
 * [Wrapped.Summary] the share image uses. The screen owns no numbers of its own —
 * every figure comes from [Wrapped.summarise], which is pure and tested.
 *
 * When there is not enough history the screen says so instead of drawing a
 * "0 minutes" card. A Wrapped that congratulates you for nothing is worse than
 * no Wrapped.
 */
@Composable
fun WrappedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val events = remember { Store.events() }
    val offset = remember { Listening.zoneOffset() }

    val years = remember(events) {
        val set = events.map { Wrapped.yearOf(it.atMs, offset) }.toSortedSet(reverseOrder())
        if (set.isEmpty()) sortedSetOf(Calendar.getInstance().get(Calendar.YEAR)) else set
    }
    var year by remember { mutableIntStateOf(years.first()) }

    val summary = remember(year, events) {
        Wrapped.summarise(events, Store::track, year, offset)
    }
    val cards = remember(summary) {
        val months = SimpleDateFormat("MMMM", Locale.getDefault())
        // Epoch days are UTC midnights. Formatting them in the device zone would
        // print the previous day for anyone west of Greenwich.
        val days = SimpleDateFormat("d MMMM", Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        Wrapped.cards(
            s = summary,
            monthName = { m -> months.format(monthDate(m)) },
            dayLabel = { d -> days.format(Date(d * Listening.DAY)) },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            BackBar(
                "Year in review",
                onBack,
                trailing = {
                    if (summary.hasStory) {
                        IconBtn(G.Share, Ink.Text, 38.dp, 17.dp) {
                            ShareCard.shareWrapped(context, summary)
                        }
                    }
                },
            )
        }

        if (years.size > 1) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    years.forEach { y ->
                        YearChip(y, y == year) { year = y }
                    }
                }
            }
        }

        if (!summary.hasStory) {
            item {
                EmptyState(
                    G.Sparkle,
                    "Not enough listening yet",
                    "Play ${Wrapped.MIN_PLAYS} songs this year and this page fills itself in.",
                )
            }
        } else {
            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Display("Your $year", 30)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Built from ${summary.plays} plays on this device. Nothing left the phone.",
                        color = Ink.Muted, fontSize = 12.sp, fontFamily = SoraFamily, lineHeight = 17.sp,
                    )
                }
            }

            itemsIndexed(cards) { i, card ->
                ReelCard(card, index = i, total = cards.size)
            }

            item {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    FilledBtn("Share this year", G.Share) {
                        ShareCard.shareWrapped(context, summary)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "The image is drawn at 1080 × 1350 and saved to the cache before sharing.",
                        color = Ink.Faint, fontSize = 11.sp, fontFamily = SoraFamily, lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

private fun monthDate(month: Int): Date {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.MONTH, month.coerceIn(0, 11))
    return cal.time
}

@Composable
private fun YearChip(year: Int, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Ink.Lime else Ink.Field)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            "$year",
            color = if (active) Ink.OnAccent else Ink.Muted,
            fontSize = 12.sp, fontFamily = SoraFamily,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * One card of the reel.
 *
 * The accent rotates through the four theme colours so consecutive cards never
 * share a gradient — the same trick the palette does for artwork, for the same
 * reason: two identical panels in a row read as one panel.
 */
@Composable
private fun ReelCard(card: Wrapped.Card, index: Int, total: Int) {
    val tint = when (card.accent % 4) {
        0 -> Ink.Lime
        1 -> Ink.Lavender
        2 -> Ink.Mint
        else -> Ink.Rose
    }
    val big = card.kind == Wrapped.Kind.INTRO

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Ink.Card),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        listOf(tint.copy(alpha = 0.20f), Color.Transparent, Color.Transparent),
                    )
                )
        )
        Column(Modifier.padding(22.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    card.kicker.uppercase(),
                    color = tint, fontSize = 10.5.sp, fontFamily = SoraFamily,
                    letterSpacing = 1.1.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${index + 1}/$total",
                    color = Ink.Faint, fontSize = 10.5.sp, fontFamily = SoraFamily,
                )
            }
            Spacer(Modifier.height(if (big) 18.dp else 14.dp))
            Text(
                card.headline,
                color = Ink.Text,
                fontSize = if (big) 40.sp else 30.sp,
                fontFamily = SoraFamily,
                fontWeight = FontWeight.SemiBold,
                lineHeight = if (big) 44.sp else 34.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                card.body,
                color = Ink.Muted, fontSize = 13.sp, fontFamily = SoraFamily, lineHeight = 19.sp,
            )
        }
    }
}
