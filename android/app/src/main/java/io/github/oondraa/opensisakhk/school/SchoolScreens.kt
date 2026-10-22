package io.github.oondraa.opensisakhk.school

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.oondraa.opensisakhk.data.inMinutes
import io.github.oondraa.opensisakhk.data.longDate
import io.github.oondraa.opensisakhk.data.minutes
import io.github.oondraa.opensisakhk.data.today
import io.github.oondraa.opensisakhk.data.weekdayIndex
import io.github.oondraa.opensisakhk.timetable.PERIODS
import io.github.oondraa.opensisakhk.timetable.rememberNow
import io.github.oondraa.opensisakhk.ui.Card
import io.github.oondraa.opensisakhk.ui.DayStrip
import io.github.oondraa.opensisakhk.ui.Divider
import io.github.oondraa.opensisakhk.ui.Empty
import io.github.oondraa.opensisakhk.ui.Footer
import io.github.oondraa.opensisakhk.ui.Icons
import io.github.oondraa.opensisakhk.ui.PeriodStrip
import io.github.oondraa.opensisakhk.ui.Pill
import io.github.oondraa.opensisakhk.ui.Screen
import io.github.oondraa.opensisakhk.ui.ScreenHeader
import io.github.oondraa.opensisakhk.ui.SectionTitle
import io.github.oondraa.opensisakhk.ui.SkeletonList
import io.github.oondraa.opensisakhk.ui.theme.Tabular
import io.github.oondraa.opensisakhk.ui.theme.Tones
import java.time.LocalDate

// Weight room. (T0 is the sports hall; nobody asked for it, but readSchoolDay sees it too.)
private const val GYM = "P0"

/** Shared top of the three school-timetable screens; the states nothing can be shown in. */
@Composable
private fun SchoolScreen(
    title: String,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    school: SchoolWeek,
    onBack: () -> Unit,
    strip: @Composable () -> Unit = {},
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Screen(
        header = {
            ScreenHeader(title, longDate(selected), onBack = onBack, loading = school.weeks.loading || school.week.loading) {
                Spacer(Modifier.size(12.dp))
                DayStrip(selected, onSelect, Modifier.padding(end = 8.dp))
                strip()
            }
        },
        onRefresh = { school.week.refresh() },
        refreshing = false,
    ) {
        when {
            school.weeks.data != null && school.key == null ->
                item { Empty(Icons.CalendarOff, "Mimo rozsah", "Škola zveřejňuje rozvrh jen na tento a příští týden.", Modifier.fillMaxWidth()) }
            school.week.data == null && school.week.failed -> item { Empty(Icons.Alert, "Rozvrh školy se nepodařilo načíst", null, Modifier.fillMaxWidth()) }
            school.week.data == null -> item { SkeletonList(8, 52.dp) }
            else -> content()
        }
    }
}

@Composable
fun GymScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf(schoolStartDay()) }
    val school = rememberSchoolWeek(selected)
    val now by rememberNow()
    val live = selected == today()
    val cells = school.week.data?.getOrNull(selected.weekdayIndex).orEmpty().filter { it.room == GYM }
    val last = lastPeriodOf(cells)
    val periods = PERIODS.take(last + 1).mapIndexed { p, (start, end) -> Triple(p, start to end, cells.filter { it.period == p }) }

    SchoolScreen("Posilovna", selected, { selected = it }, school, onBack) {
        item { GymStatus(live, now, periods) }
        item {
            Card {
                periods.forEachIndexed { i, (p, time, occ) ->
                    if (i > 0) Divider()
                    val current = live && minutes(time.first) <= now && now < minutes(time.second)
                    val past = live && minutes(time.second) <= now
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val c = if (past) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
                        Text("$p.", style = MaterialTheme.typography.titleMedium.merge(Tabular), color = c, modifier = Modifier.width(32.dp))
                        Text("${time.first}–${time.second}", style = MaterialTheme.typography.bodyMedium.merge(Tabular), color = if (past) c else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (occ.isNotEmpty()) Pill(occ.joinToString(", ") { it.cls }, if (occ.any { it.changed }) Tones.orange else Tones.red, occ.joinToString(", ") { it.teacher })
                        else Pill("Volno", Tones.green)
                    }
                }
            }
        }
        item { Footer("Podle veřejného rozvrhu školy. Kroužky a akce mimo rozvrh tu nejsou.") }
    }
}

@Composable
private fun GymStatus(live: Boolean, now: Int, periods: List<Triple<Int, Pair<String, String>, List<Cell>>>) {
    val busy = periods.filter { it.third.isNotEmpty() }
    var title = if (busy.isNotEmpty()) "Obsazeno ${busy.size}× za den" else "Celý den volno"
    var detail = if (busy.isNotEmpty()) busy.joinToString(" · ") { (p, _, occ) -> "$p. ${occ.joinToString("+") { it.cls }}" } else "Rozvrh tu nemá žádnou hodinu."
    var tone = Tones.neutral
    if (live) {
        val cur = periods.find { minutes(it.second.first) <= now && now < minutes(it.second.second) }
        val nextBusy = busy.find { minutes(it.second.first) > now }
        if (cur != null && cur.third.isNotEmpty()) {
            tone = Tones.red
            title = "Teď obsazeno"
            detail = "${cur.third.joinToString(", ") { "${it.cls} · ${it.teacher}" }} · volno ${inMinutes(minutes(cur.second.second) - now)}"
        } else {
            tone = Tones.green
            title = "Teď volno"
            detail = if (nextBusy != null) "Obsadí ${nextBusy.third.joinToString("+") { it.cls }} ${inMinutes(minutes(nextBusy.second.first) - now)}" else "Dnes už tam žádná hodina není."
        }
    }
    Surface(shape = RoundedCornerShape(24.dp), color = tone.copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(tone), contentAlignment = Alignment.Center) {
                Icon(Icons.Dumbbell, null, Modifier.size(28.dp), tint = androidx.compose.ui.graphics.Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, color = tone)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun RoomsScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf(schoolStartDay()) }
    var picked by remember { mutableStateOf<Int?>(null) }
    val school = rememberSchoolWeek(selected)
    val now by rememberNow()
    val live = selected == today()
    val week = school.week.data
    val day = week?.getOrNull(selected.weekdayIndex).orEmpty()
    val rooms = week?.let { allRooms(it) }.orEmpty()
    val last = lastPeriodOf(day)
    val period = minOf(picked ?: autoPeriod(day, live, now), last)
    val free = freeRooms(rooms, day, period)
    val blocks = blocksAt(day, period)
    val (start, end) = PERIODS[period]
    val isNow = live && minutes(start) <= now && now < minutes(end)

    SchoolScreen(
        "Volné učebny", selected, { selected = it; picked = null }, school, onBack,
        strip = { if (school.key != null && week != null) PeriodStrip(last, period, live, now) { picked = it } },
    ) {
        when {
            rooms.isEmpty() -> item { Empty(Icons.CalendarOff, "Tento týden se neučí", "Rozvrh školy nemá žádnou hodinu.", Modifier.fillMaxWidth()) }
            free.isEmpty() -> item { Empty(Icons.Door, "Všechno obsazeno", "V $period. hodině se učí ve všech ${rooms.size} učebnách.", Modifier.fillMaxWidth()) }
            else -> {
                item { SectionTitle("${if (isNow) "Teď volné" else "Volné"} · $start–$end") }
                // Two columns of rooms: a list of 30 one-word rows is mostly empty space.
                items(free.chunked(2)) { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (r in pair) RoomTile(r, Modifier.weight(1f))
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item {
                    Footer(
                        if (blocks.isNotEmpty()) "V této hodině běží ${blocks.joinToString(", ")}. Rozvrh školy neukazuje, ve kterých učebnách, takže některá z volných může být obsazená."
                        else "${free.size} z ${rooms.size} učeben. Podle veřejného rozvrhu školy, konzultace a akce mimo rozvrh tu nejsou.",
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomTile(r: FreeRoom, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(r.room, style = MaterialTheme.typography.headlineMedium.merge(Tabular))
            Text(
                if (r.until == null) "do konce dne" else "do ${PERIODS[r.until].first}",
                style = MaterialTheme.typography.bodySmall.merge(Tabular),
                color = Tones.green,
            )
        }
    }
}

@Composable
fun TeachersScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf(schoolStartDay()) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val school = rememberSchoolWeek(selected)
    val now by rememberNow()
    val live = selected == today()
    val week = school.week.data
    val day = week?.getOrNull(selected.weekdayIndex).orEmpty()
    val last = lastPeriodOf(day)
    val period = minOf(picked ?: autoPeriod(day, live, now), last)
    val (start, end) = PERIODS[period]
    val isNow = live && minutes(start) <= now && now < minutes(end)
    val q = query.trim().uppercase()
    val (allTeaching, allFree) = teachersAt(day, period)
    val teaching = allTeaching.filter { q.isEmpty() || it.teacher.uppercase().contains(q) }
    val free = allFree.filter { q.isEmpty() || it.teacher.uppercase().contains(q) }

    SchoolScreen(
        "Učitelé", selected, { selected = it; picked = null }, school, onBack,
        strip = { if (school.key != null && week != null) PeriodStrip(last, period, live, now) { picked = it } },
    ) {
        if (day.isEmpty()) {
            item { Empty(Icons.CalendarOff, "Tento den se neučí", "Rozvrh školy nemá žádnou hodinu.", Modifier.fillMaxWidth()) }
            return@SchoolScreen
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Zkratka učitele, třeba HU") },
                leadingIcon = { Icon(Icons.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (teaching.isEmpty() && free.isEmpty()) {
            item { Empty(Icons.Teacher, "Nikdo takový", "Tento den neučí nikdo se zkratkou $q.", Modifier.fillMaxWidth()) }
            return@SchoolScreen
        }
        if (teaching.isNotEmpty()) item {
            Column {
                SectionTitle("${if (isNow) "Teď učí" else "Učí"} · $start–$end")
                Card {
                    teaching.forEachIndexed { i, t ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t.teacher, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(52.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.subject, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.classes.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Pill(t.rooms.joinToString(", ").ifEmpty { "blok" }, if (t.changed) Tones.orange else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (free.isNotEmpty()) item {
            Column {
                SectionTitle("Neučí")
                Card {
                    free.forEachIndexed { i, t ->
                        if (i > 0) Divider()
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t.teacher, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (t.next == null) Pill("dnes už neučí", Tones.neutral) else Pill("učí od ${PERIODS[t.next].first}", Tones.green)
                        }
                    }
                }
                Footer("Podle veřejného rozvrhu školy: kdo v té hodině neučí, nemusí být ve škole. Suplování se tu objeví, až ho škola zanese do rozvrhu.")
            }
        }
    }
}
