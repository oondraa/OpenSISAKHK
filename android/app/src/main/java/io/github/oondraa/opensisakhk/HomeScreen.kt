package io.github.oondraa.opensisakhk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.oondraa.opensisakhk.absence.LIMIT
import io.github.oondraa.opensisakhk.data.Absence
import io.github.oondraa.opensisakhk.data.Api
import io.github.oondraa.opensisakhk.data.Cache
import io.github.oondraa.opensisakhk.data.CanteenResponse
import io.github.oondraa.opensisakhk.data.GradesResponse
import io.github.oondraa.opensisakhk.data.Me
import io.github.oondraa.opensisakhk.data.ProjectsResponse
import io.github.oondraa.opensisakhk.data.TimetableResponse
import io.github.oondraa.opensisakhk.data.WEEKDAYS
import io.github.oondraa.opensisakhk.data.inMinutes
import io.github.oondraa.opensisakhk.data.minutes
import io.github.oondraa.opensisakhk.data.monday
import io.github.oondraa.opensisakhk.data.nowMinutes
import io.github.oondraa.opensisakhk.data.today
import io.github.oondraa.opensisakhk.data.weekdayIndex
import io.github.oondraa.opensisakhk.school.allRooms
import io.github.oondraa.opensisakhk.school.autoPeriod
import io.github.oondraa.opensisakhk.school.cachedSchoolWeek
import io.github.oondraa.opensisakhk.school.freeRooms
import io.github.oondraa.opensisakhk.timetable.PERIODS
import io.github.oondraa.opensisakhk.timetable.held
import io.github.oondraa.opensisakhk.timetable.rememberNow
import io.github.oondraa.opensisakhk.timetable.startDay
import io.github.oondraa.opensisakhk.ui.Card
import io.github.oondraa.opensisakhk.ui.Divider
import io.github.oondraa.opensisakhk.ui.Icons
import io.github.oondraa.opensisakhk.ui.ListRow
import io.github.oondraa.opensisakhk.ui.TintIcon
import io.github.oondraa.opensisakhk.ui.cz
import io.github.oondraa.opensisakhk.ui.plural
import io.github.oondraa.opensisakhk.ui.rememberCached
import io.github.oondraa.opensisakhk.ui.theme.Tabular
import io.github.oondraa.opensisakhk.ui.theme.Tones
import io.github.oondraa.opensisakhk.ui.theme.gradeTone
import java.time.LocalDate
import java.util.Locale

private const val MORNING = 7 * 60 // from 7:00 the timetable card names the day's subjects, before that only its span

/** What a part's tile says without opening it, from the last loaded data. */
private class Glance(val value: String? = null, val color: (@Composable () -> Color)? = null, val sub: String)

/**
 * The launcher: the timetable card on top, the first four parts as widgets, the rest (and Settings) as one list.
 * Each one already answers the question it stands for, from the last loaded data. Only the timetable is refreshed
 * here; the rest refresh when opened. `refresh` changes when a screen closes, so the glances are read again.
 */
@Composable
fun HomeScreen(onOpen: (Part) -> Unit, refresh: Int) {
    val now by rememberNow()
    val t = today()
    val day = remember(refresh) { startDay(Cache.get("timetable:${t.monday()}", TimetableResponse.serializer())?.days) }
    val week = rememberCached("timetable:${day.monday()}", TimetableResponse.serializer()) { Api.timetable(day.monday().toString()) }
    val order = remember(refresh) { homeOrder() }
    val me = remember(refresh) { Cache.get("me", Me.serializer()) }
    val glances = remember(refresh, now) { order.associateWith { glance(it, now) } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.statusBarsPadding().height(4.dp)) }
        item { TimetableCard(day, week.data?.takeIf { it.week == day.monday().toString() }, now, week.loading) { onOpen(Part.Timetable) } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (pair in order.take(4).chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (p in pair) Widget(p, glances.getValue(p), Modifier.weight(1f)) { onOpen(p) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item {
            Card {
                order.drop(4).forEach { p ->
                    val g = glances.getValue(p)
                    ListRow(p.label, listOfNotNull(g.value, g.sub).joinToString(" · "), onClick = { onOpen(p) }, lead = { TintIcon(p.icon, p.tint) })
                    Divider(62.dp)
                }
                ListRow("Nastavení", me?.name ?: "Notifikace, Strava, vzhled", onClick = { onOpen(Part.Settings) }, lead = { TintIcon(Part.Settings.icon, Part.Settings.tint) })
            }
        }
        item { Box(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun Widget(p: Part, g: Glance, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.height(150.dp)) {
        Column(Modifier.padding(16.dp).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(p.icon, null, Modifier.size(16.dp), tint = p.tint)
                Spacer(Modifier.width(6.dp))
                Text(p.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.weight(1f))
            g.value?.let {
                Text(
                    it,
                    style = (if (it.length > 6) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall).merge(Tabular),
                    fontWeight = FontWeight.SemiBold,
                    color = g.color?.invoke() ?: MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(g.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TimetableCard(day: LocalDate, week: TimetableResponse?, now: Int, loading: Boolean, onClick: () -> Unit) {
    val t = today()
    val lessons = held(week?.days?.find { it.date == day.toString() }?.lessons.orEmpty()).sortedBy { minutes(it.start) }
    val live = day == t
    val current = if (live) lessons.find { minutes(it.start) <= now && now < minutes(it.end) } else null
    val next = lessons.find { !live || minutes(it.start) > now }
    val dayName = if (day == t.plusDays(1)) "Zítra" else WEEKDAYS[day.weekdayIndex]

    var eyebrow = ""
    var title = "Rozvrh"
    var room = ""
    var meta = if (loading) "Načítám…" else "Otevři rozvrh"
    // School's out for today, or it's the early morning before it: just how long the day is.
    val overview = lessons.isNotEmpty() && current == null && (!live || now < MORNING)
    when {
        week != null && lessons.isEmpty() -> {
            eyebrow = if (live) "Dnes" else dayName
            title = "Volno"
            meta = "Žádná hodina"
        }
        overview -> {
            eyebrow = if (live) "Dnes" else dayName
            title = "${lessons.minBy { minutes(it.start) }.start}–${lessons.maxBy { minutes(it.end) }.end}"
            meta = "${lessons.size} ${plural(lessons.size, "hodina", "hodiny", "hodin")}"
        }
        current != null -> {
            eyebrow = "Teď · končí ${inMinutes(minutes(current.end) - now)}"
            title = current.subject
            room = current.room
            meta = if (next != null) "Pak ${next.subject} · ${next.room}" else "Poslední hodina"
        }
        next != null && live -> {
            eyebrow = "Další · ${inMinutes(minutes(next.start) - now)}"
            title = next.subject
            room = next.room
            meta = "${next.start}–${next.end} · ${next.teacher}"
        }
    }

    Surface(onClick = onClick, shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            if (eyebrow.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.width(8.dp))
                Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Calendar, null, Modifier.size(16.dp), tint = Part.Timetable.tint)
                Spacer(Modifier.width(6.dp))
                Text("Rozvrh", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    title,
                    style = (if (overview) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium).merge(Tabular),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (room.isNotEmpty()) Text(room, style = MaterialTheme.typography.headlineMedium.merge(Tabular), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // The whole day as one segment per lesson, filled as it passes.
            if (live && lessons.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (l in lessons) {
                        val p = ((now - minutes(l.start)).toFloat() / (minutes(l.end) - minutes(l.start))).coerceIn(0f, 1f)
                        val on = minutes(l.start) <= now && now < minutes(l.end)
                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))) {
                            Box(
                                Modifier.fillMaxWidth(p).fillMaxHeight().clip(RoundedCornerShape(3.dp))
                                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun glance(p: Part, now: Int): Glance = when (p) {
    Part.Grades -> {
        val subjects = Cache.get("grades", GradesResponse.serializer())?.subjects.orEmpty()
        val avgs = subjects.mapNotNull { it.average }
        val overall = if (avgs.isNotEmpty()) avgs.average() else null
        val count = subjects.sumOf { it.grades.size }
        Glance(overall?.cz(), { gradeTone(overall) }, if (overall != null) "Průměr · $count ${plural(count, "známka", "známky", "známek")}" else "Průměr a nové známky")
    }
    Part.Absence -> {
        val data = Cache.get("absence", Absence.serializer())
        val over = data?.subjects.orEmpty().filter { it.percent >= LIMIT }.map { it.subject }
        val total = data?.total
        Glance(
            total?.let { "${String.format(Locale.forLanguageTag("cs"), "%.1f", it.percent).removeSuffix(",0")} %" },
            if (total == null) null else { { if (over.isNotEmpty()) Tones.red else if (total.percent >= LIMIT - 10) Tones.orange else Tones.teal } },
            when {
                over.isNotEmpty() -> "Přes $LIMIT %: ${over.joinToString(", ")}"
                total != null -> "${total.missed} ${plural(total.missed, "hodina", "hodiny", "hodin")} celkem"
                else -> "Hranice $LIMIT %"
            },
        )
    }
    Part.Lunch -> {
        val data = Cache.get("canteen", CanteenResponse.serializer())
        val t = today().toString()
        val late = nowMinutes() >= 14 * 60
        val day = data?.days?.find { (it.date > t || (it.date == t && !late)) && it.closed == null }
        val meal = day?.meals?.find { it.ordered }
        val whenText = when {
            day == null -> ""
            day.date == t -> "Dnes"
            day.date == today().plusDays(1).toString() -> "Zítra"
            else -> WEEKDAYS[LocalDate.parse(day.date).weekdayIndex]
        }
        Glance(
            meal?.number?.toString() ?: if (day != null && meal == null) "Nic" else null,
            null,
            when {
                data?.connected != true -> "Objednávky ze Stravy"
                meal != null -> "$whenText · ${meal.name}"
                day != null -> "$whenText nemáš objednáno"
                else -> "Jídelníček"
            },
        )
    }
    Part.Gym -> {
        val t = today()
        val cells = cachedSchoolWeek(t)?.getOrNull(t.weekdayIndex)?.filter { it.room == "P0" }
        if (cells == null || t.weekdayIndex > 4) Glance(null, null, "Kdy je volno")
        else {
            val period = PERIODS.indexOfFirst { minutes(it.first) <= now && now < minutes(it.second) }
            val lastEnd = minutes(PERIODS[maxOf(8, cells.maxOfOrNull { it.period } ?: 0)].second)
            val busy = if (period >= 0) cells.filter { it.period == period } else emptyList()
            when {
                now >= lastEnd -> Glance("Volno", { Tones.green }, "Do konce dne")
                busy.isNotEmpty() -> Glance("Obsazeno", { Tones.red }, "${busy.joinToString(", ") { it.cls }} · do ${PERIODS[period].second}")
                else -> Glance("Volno", { Tones.green }, "Právě teď")
            }
        }
    }
    Part.Rooms -> {
        val t = today()
        val week = cachedSchoolWeek(t)
        val day = week?.getOrNull(t.weekdayIndex)
        if (week == null || day.isNullOrEmpty()) Glance(null, null, "Kde je volno")
        else {
            val period = autoPeriod(day, true, now)
            val (start, end) = PERIODS[period]
            if (now >= minutes(end)) Glance(null, null, "Kde je volno")
            else {
                val n = freeRooms(allRooms(week), day, period).size
                Glance("$n ${plural(n, "volná", "volné", "volných")}", { Tones.green }, if (minutes(start) <= now) "Právě teď" else "Od $start")
            }
        }
    }
    Part.Teachers -> Glance(null, null, "Kdo kde učí")
    Part.Projects -> {
        val data = Cache.get("projects:latest", ProjectsResponse.serializer())
        val mine = data?.mine?.find { it.approved } ?: data?.mine?.firstOrNull()
        Glance(mine?.number, null, mine?.name ?: "Projektové dny")
    }
    // No amounts on the home screen: whoever glances at the phone doesn't see what's owed.
    Part.Finance -> Glance(null, null, "Školné a fond")
    Part.Timetable, Part.Settings -> Glance(null, null, "")
}
