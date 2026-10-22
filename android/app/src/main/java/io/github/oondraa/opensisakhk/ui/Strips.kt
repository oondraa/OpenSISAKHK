package io.github.oondraa.opensisakhk.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.oondraa.opensisakhk.data.WEEKDAYS_SHORT
import io.github.oondraa.opensisakhk.data.minutes
import io.github.oondraa.opensisakhk.data.monday
import io.github.oondraa.opensisakhk.data.today
import io.github.oondraa.opensisakhk.timetable.PERIODS
import io.github.oondraa.opensisakhk.ui.theme.Tabular
import java.time.LocalDate

/** Five school days with arrows to the weeks around them; the chosen day is a filled pill. */
@Composable
fun DayStrip(selected: LocalDate, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val monday = selected.monday()
    val today = today()
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSelect(selected.minusWeeks(1)) }, Modifier.size(40.dp)) { Icon(Icons.ChevronLeft, "Předchozí týden", Modifier.size(20.dp)) }
            for (i in 0..4) {
                val date = monday.plusDays(i.toLong())
                val active = date == selected
                val bg by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else Color.Transparent, label = "day")
                val fg = when {
                    active -> MaterialTheme.colorScheme.onPrimary
                    date == today -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(bg)
                        .clickable(role = Role.Tab) { onSelect(date) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(WEEKDAYS_SHORT[i], style = MaterialTheme.typography.labelSmall, color = if (active) fg.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleLarge.merge(Tabular), color = fg)
                }
            }
            IconButton(onClick = { onSelect(selected.plusWeeks(1)) }, Modifier.size(40.dp)) { Icon(Icons.ChevronRight, "Další týden", Modifier.size(20.dp)) }
        }
    }
}

/** A scrolling row of periods; the chosen one filled, past ones faded, kept in view as it changes. */
@Composable
fun PeriodStrip(last: Int, period: Int, live: Boolean, now: Int, onPick: (Int) -> Unit) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, period - 2))
    LaunchedEffect(period) { list.animateScrollToItem(maxOf(0, period - 2)) }
    LazyRow(state = list, contentPadding = PaddingValues(horizontal = 0.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 10.dp)) {
        itemsIndexed(PERIODS.take(last + 1)) { p, (start, end) ->
            val active = p == period
            val bg by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer, label = "period")
            val fg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Column(
                Modifier
                    .widthIn(min = 58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .clickable(role = Role.Tab) { onPick(p) }
                    .alpha(if (!active && live && minutes(end) <= now) 0.5f else 1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("$p.", style = MaterialTheme.typography.titleMedium.merge(Tabular), color = fg)
                Text(start, style = MaterialTheme.typography.labelSmall.merge(Tabular), color = fg.copy(alpha = 0.75f))
            }
        }
    }
}

/** Status pill: "Volno", "do 11:45", "učí od 9:45". */
@Composable
fun Pill(text: String, color: Color, sub: String? = null) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.15f), contentColor = color) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelLarge.merge(Tabular))
            if (sub != null) Text("  $sub", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.75f))
        }
    }
}
