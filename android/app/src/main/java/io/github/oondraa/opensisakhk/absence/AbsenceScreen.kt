package io.github.oondraa.opensisakhk.absence

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.oondraa.opensisakhk.Part
import io.github.oondraa.opensisakhk.data.Absence
import io.github.oondraa.opensisakhk.data.AbsenceSubject
import io.github.oondraa.opensisakhk.data.Api
import io.github.oondraa.opensisakhk.data.Day
import io.github.oondraa.opensisakhk.data.Lesson
import io.github.oondraa.opensisakhk.data.MissedLesson
import io.github.oondraa.opensisakhk.data.TimetableResponse
import io.github.oondraa.opensisakhk.data.WEEKDAYS
import io.github.oondraa.opensisakhk.data.longDate
import io.github.oondraa.opensisakhk.data.minutes
import io.github.oondraa.opensisakhk.data.monday
import io.github.oondraa.opensisakhk.data.today
import io.github.oondraa.opensisakhk.data.weekdayIndex
import io.github.oondraa.opensisakhk.grades.Summary
import io.github.oondraa.opensisakhk.timetable.held
import io.github.oondraa.opensisakhk.timetable.startDay
import io.github.oondraa.opensisakhk.ui.Banner
import io.github.oondraa.opensisakhk.ui.Card
import io.github.oondraa.opensisakhk.ui.DayStrip
import io.github.oondraa.opensisakhk.ui.Divider
import io.github.oondraa.opensisakhk.ui.Empty
import io.github.oondraa.opensisakhk.ui.Footer
import io.github.oondraa.opensisakhk.ui.Icons
import io.github.oondraa.opensisakhk.ui.ListRow
import io.github.oondraa.opensisakhk.ui.Pill
import io.github.oondraa.opensisakhk.ui.Screen
import io.github.oondraa.opensisakhk.ui.ScreenHeader
import io.github.oondraa.opensisakhk.ui.SectionTitle
import io.github.oondraa.opensisakhk.ui.SkeletonList
import io.github.oondraa.opensisakhk.ui.Tag
import io.github.oondraa.opensisakhk.ui.TintIcon
import io.github.oondraa.opensisakhk.ui.cz
import io.github.oondraa.opensisakhk.ui.plural
import io.github.oondraa.opensisakhk.ui.rememberCached
import io.github.oondraa.opensisakhk.data.Cache
import io.github.oondraa.opensisakhk.ui.theme.Tabular
import io.github.oondraa.opensisakhk.ui.theme.Tones
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate

private val CODES = mapOf("n" to "neomluveno", "o" to "omluveno")

/** Accusative: "odchodit 1 hodinu / 3 hodiny / 5 hodin". */
private fun hours(n: Int) = "$n ${plural(n, "hodinu", "hodiny", "hodin")}"
private fun pct(n: Double) = "${if (n % 1.0 == 0.0) n.toInt().toString() else n.cz(1)} %"
private fun shortDate(d: LocalDate) = "${d.dayOfMonth}. ${d.monthValue}."

private enum class Level { None, Low, Mid, High }

private fun level(percent: Double) = when {
    percent <= 0 -> Level.None
    percent < LIMIT - 10 -> Level.Low
    percent < LIMIT -> Level.Mid
    else -> Level.High
}

@Composable
private fun Level.color() = when (this) {
    Level.None -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Level.Low -> Tones.teal
    Level.Mid -> Tones.orange
    Level.High -> Tones.red
}

/** Lessons left per subject until the end of the semester, from the permanent timetable of this and next week. */
@Composable
private fun rememberRemaining(): Map<String, Int>? {
    val t = today()
    val monday = t.monday()
    val data = rememberCached("permanent:$monday", ListSerializer(ListSerializer(Day.serializer()))) {
        coroutineScope {
            listOf(monday, monday.plusWeeks(1)).map { w -> async { Api.permanentTimetable(w.toString()).days } }.map { it.await() }
        }
    }.data
    return remember(data) { data?.let { remainingLessons(monday, it[0], it[1], t, semesterEnd(t)) } }
}

/** A lesson the student plans to skip; `key` tells it apart from the same subject at another time. */
data class PlannedSkip(val key: String, val date: String, val subject: String)

@Composable
fun AbsenceScreen(onBack: () -> Unit) {
    val absence = rememberCached("absence", Absence.serializer()) { Api.absence() }
    val remaining = rememberRemaining()
    var calc by rememberSaveable { mutableStateOf(false) }
    // The plan outlives going back to the list and in again.
    val planned = remember { mutableStateListOf<PlannedSkip>() }
    BackHandler(calc) { calc = false }
    val reserves = remember(absence.data, remaining) {
        absence.data?.subjects.orEmpty().associate { it.subject to plan(it.missed, it.taught, remaining?.get(it.subject)).reserve }
    }

    AnimatedContent(
        calc,
        transitionSpec = {
            if (targetState) (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut()
            else fadeIn() togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
        },
        label = "calc",
    ) { inCalc ->
        if (inCalc) AbsenceCalc(reserves, planned) { calc = false }
        else AbsenceList(absence.data, absence.loading, absence.failed, absence.refresh, remaining, onBack) { calc = true }
    }
}

@Composable
private fun AbsenceList(data: Absence?, loading: Boolean, failed: Boolean, refresh: () -> Unit, remaining: Map<String, Int>?, onBack: () -> Unit, openCalc: () -> Unit) {
    // Subjects without a single missed lesson are left out: nobody needs to read "0 %" twenty times.
    val subjects = data?.subjects.orEmpty().filter { it.missed > 0 }.sortedWith(compareByDescending<AbsenceSubject> { it.percent }.thenBy { it.subject })
    val total = data?.total
    val over = subjects.filter { it.percent >= LIMIT }
    val close = subjects.filter { level(it.percent) == Level.Mid }
    val today = today()

    Screen(header = { ScreenHeader("Absence", "Toto pololetí", onBack = onBack, loading = loading) }, onRefresh = refresh) {
        if (failed && data != null) item { Banner("Absenci se nepodařilo obnovit. Zobrazuji poslední uloženou.") }
        if (data == null) {
            item { if (loading) SkeletonList(6) else Empty(Icons.Absence, "Absence není k dispozici", modifier = Modifier.fillMaxWidth()) }
            return@Screen
        }
        if (total != null) item {
            Summary(
                "Zameškáno celkem", "${total.missed}", level(total.percent).color(),
                "${pct(total.percent)} z ${total.taught}", "${total.unexcused} neomluvených",
                side2Color = if (total.unexcused > 0) Tones.red else MaterialTheme.colorScheme.onSurfaceVariant,
                unit = plural(total.missed, "hodina", "hodiny", "hodin"),
            )
        }
        if (over.isNotEmpty()) item {
            Alert(Tones.red, "Přes hranici $LIMIT %: ${over.joinToString(", ") { it.subject }}", "Od $LIMIT–$LIMIT_HARD % tě vyučující nemusí klasifikovat a hrozí opravné zkoušky.")
        } else if (close.isNotEmpty()) item {
            Alert(Tones.orange, "Blízko hranice $LIMIT %: ${close.joinToString(", ") { it.subject }}", "Hlídej si, kolik hodin ještě můžeš vynechat.")
        }
        if (remaining != null) item {
            Card { ListRow("Co můžu vynechat", "Rozvrh s hodinami, na které nemusíš", onClick = openCalc, lead = { TintIcon(Icons.Calculator, Part.Absence.tint) }) }
        }
        item { SectionTitle("Podle předmětů") }
        if (subjects.isNotEmpty()) item {
            Card {
                subjects.forEachIndexed { i, s ->
                    if (i > 0) Divider()
                    SubjectRow(s, plan(s.missed, s.taught, remaining?.get(s.subject)))
                }
            }
        }
        item {
            Footer(
                (if (subjects.isNotEmpty()) "Předměty bez absence nezobrazuju. " else "V žádném předmětu zatím nemáš absenci. ") +
                    "Procenta jsou ze všech odučených hodin, omluvených i neomluvených. Svislá čára je hranice $LIMIT %. Zbývající hodiny jsou odhad podle stálého rozvrhu do konce pololetí (${shortDate(semesterEnd(today))}), bez suplování a akcí.",
            )
        }
        item { SectionTitle("Zameškané hodiny") }
        if (data.missed.isNotEmpty()) item { MissedList(data.missed) }
        else item { Empty(Icons.Absence, "Žádná absence", "Zatím jsi nic nezameškal.", Modifier.fillMaxWidth()) }
        item { Footer("Omluvenky se zatím zadávají přímo v SIS.") }
    }
}

@Composable
private fun Alert(tone: Color, title: String, text: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = tone.copy(alpha = 0.13f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = tone)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun hint(s: AbsenceSubject, p: Plan): String? {
    val left = if (p.remaining != null) " Do konce pololetí zbývá asi ${p.remaining} h." else ""
    if (p.needed > 0) {
        if (p.remaining != null && p.needed > p.remaining)
            return "Do konce pololetí už pod $LIMIT % neklesneš, ani když přijdeš na všech ${p.remaining} zbývajících hodin (skončíš na ${pct(p.best ?: s.percent)})."
        return "Pod $LIMIT % se dostaneš, když odchodíš ještě ${hours(p.needed)} bez absence.$left"
    }
    if (p.reserve == null || p.remaining == null) return null
    if (p.reserve == 0) return "Už nesmíš vynechat žádnou hodinu, jinak skončíš nad $LIMIT %.$left"
    return "Můžeš vynechat ještě ${hours(p.reserve)} z ${p.remaining} zbývajících."
}

@Composable
private fun SubjectRow(s: AbsenceSubject, p: Plan) {
    val tone = level(s.percent).color()
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.subject, style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 52.dp))
            Spacer(Modifier.width(12.dp))
            // Scale ends at 40 %, so the 30 % line sits at three quarters.
            Canvas(Modifier.weight(1f).height(8.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = r)
                drawRoundRect(tone, size = Size(size.width * minOf(1.0, s.percent / 40).toFloat(), size.height), cornerRadius = r)
                val x = size.width * LIMIT / 40f
                drawLine(line, Offset(x, -3f), Offset(x, size.height + 3f), strokeWidth = 2.dp.toPx())
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 64.dp)) {
                Text(pct(s.percent), style = MaterialTheme.typography.titleMedium.merge(Tabular), color = tone)
                Text("${s.missed}/${s.taught} h", style = MaterialTheme.typography.bodySmall.merge(Tabular), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.unexcused > 0) Text("${s.unexcused} neoml.", style = MaterialTheme.typography.bodySmall, color = Tones.red)
            }
        }
        hint(s, p)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun MissedList(lessons: List<MissedLesson>) {
    Card {
        lessons.groupBy { it.date }.entries.forEachIndexed { i, (date, ls) ->
            if (i > 0) Divider()
            val d = LocalDate.parse(date)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Column(Modifier.width(76.dp)) {
                    Text("${d.dayOfMonth}. ${d.monthValue}.", style = MaterialTheme.typography.titleMedium.merge(Tabular))
                    Text(WEEKDAYS[d.weekdayIndex], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (l in ls) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${l.period}.", style = MaterialTheme.typography.bodyMedium.merge(Tabular), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(26.dp))
                        Text(l.subject, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("  ${l.teacher}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
                        if (l.code == "n") Tag(CODES[l.code]!!, Tones.red.copy(alpha = 0.15f), Tones.red) else Tag(CODES[l.code] ?: l.code)
                    }
                }
            }
        }
    }
}

// ---------- the skip calculator ----------

private fun lessonKey(date: String, l: Lesson) = "$date|${l.start}|${l.subject}|${l.group}"
private fun hoursNom(n: Int) = "$n ${plural(n, "hodina", "hodiny", "hodin")}"

/**
 * The real timetable (with substitutions) day by day, each lesson marked with whether it can be skipped without
 * ending the semester over LIMIT. Tapping a lesson plans it as skipped, and everything recounts with it.
 */
@Composable
private fun AbsenceCalc(reserves: Map<String, Int?>, planned: MutableList<PlannedSkip>, onBack: () -> Unit) {
    val t = today()
    var selected by remember { mutableStateOf(startDay(Cache.get("timetable:${t.monday()}", TimetableResponse.serializer())?.days)) }
    val monday = selected.monday()
    val week = rememberCached("timetable:$monday", TimetableResponse.serializer()) { Api.timetable(monday.toString()) }
    val days = week.data?.takeIf { it.week == monday.toString() }?.days.orEmpty()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Co vynechat", longDate(selected), onBack = onBack, backLabel = "Absence", loading = week.loading) {
            Spacer(Modifier.size(12.dp))
            DayStrip(selected, { selected = it }, Modifier.padding(end = 8.dp))
        }
        if (week.failed && week.data != null) Box16 { Banner("Rozvrh se nepodařilo obnovit. Zobrazuji poslední uložený.") }
        val pager = rememberPagerState(initialPage = selected.weekdayIndex.coerceIn(0, 4)) { 5 }
        val mondayNow by rememberUpdatedState(monday)
        LaunchedEffect(selected) { if (pager.currentPage != selected.weekdayIndex.coerceIn(0, 4)) pager.animateScrollToPage(selected.weekdayIndex.coerceIn(0, 4)) }
        LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { val d = mondayNow.plusDays(it.toLong()); if (d != selected) selected = d } }
        HorizontalPager(pager, Modifier.weight(1f), key = { "$monday-$it" }) { i ->
            val date = monday.plusDays(i.toLong())
            val day = days.find { it.date == date.toString() }
            when {
                date < t -> Empty(Icons.CalendarOff, "Už proběhlo", "Počítám jen s hodinami, které tě ještě čekají.")
                date > semesterEnd(t) -> Empty(Icons.CalendarOff, "Další pololetí", "Absence se počítá zvlášť za každé pololetí.")
                day != null -> DayCalc(day, reserves, planned)
                week.loading -> Column(Modifier.padding(16.dp)) { SkeletonList(7) }
                else -> Empty(Icons.CalendarOff, "Žádná data")
            }
        }
    }
}

@Composable
private fun Box16(content: @Composable () -> Unit) = Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { content() }

@Composable
private fun DayCalc(day: Day, reserves: Map<String, Int?>, planned: MutableList<PlannedSkip>) {
    val lessons = held(day.lessons).sortedBy { minutes(it.start) }
    if (lessons.isEmpty()) return Empty(Icons.Sun, "Volno", "Tenhle den nemáš žádnou hodinu.")

    val subjects = lessons.map { it.subject }
    val isPlanned = planned.map { it.key }.toSet()
    val budget = budgets(reserves, planned.map { it.subject })
    // The day on its own, as if nothing of it were planned yet: the summary doesn't shift while tapping through it.
    val others = budgets(reserves, planned.filter { it.date != day.date }.map { it.subject })
    val skippable = subjects.groupingBy { it }.eachCount().entries.sumOf { (s, n) -> minOf(n, maxOf(0, others[s] ?: 0)) }
    val blocked = blockers(others, subjects)
    val plannedHere = planned.filter { it.date == day.date }

    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Summary(
                "Můžeš vynechat", "$skippable",
                when (skippable) { lessons.size -> Tones.green; 0 -> Tones.red; else -> Tones.orange },
                if (blocked.isNotEmpty()) "Celý den ne kvůli" else "Celý den v pohodě",
                blocked.joinToString(", "),
                side2Color = Tones.red,
                unit = "z ${hoursNom(lessons.size)}",
            )
        }
        item {
            Card {
                lessons.forEachIndexed { i, l ->
                    if (i > 0) Divider(64.dp)
                    val key = lessonKey(day.date, l)
                    val left = budget[l.subject]
                    val on = key in isPlanned
                    // A planned lesson is already taken off `left`; whether it fits is whether that stayed at 0 or above.
                    val v = if (on) (if (left == null) Verdict.Unknown else if (left >= 0) Verdict.Ok else Verdict.No) else verdict(left)
                    val note = if (on) {
                        if (left != null && left < 0) "Tímhle bys skončil nad $LIMIT %" else if (left != null) "Pak můžeš vynechat ještě ${hoursNom(left)}" else "Vynechám"
                    } else when (v) {
                        Verdict.Ok -> "Můžeš vynechat ještě ${hoursNom(left ?: 0)}"
                        Verdict.Last -> "Poslední hodina, kterou můžeš vynechat"
                        Verdict.No -> "Skončil bys nad $LIMIT %"
                        Verdict.Unknown -> "Nevím, kolik hodin zbývá"
                    }
                    val tone = when (v) { Verdict.Ok -> Tones.green; Verdict.Last -> Tones.orange; Verdict.No -> Tones.red; Verdict.Unknown -> Tones.neutral }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (on) planned.removeAll { it.key == key } else planned += PlannedSkip(key, day.date, l.subject)
                            }
                            .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                            .heightIn(min = 62.dp)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.width(48.dp)) {
                            Text(l.start, style = MaterialTheme.typography.bodyMedium.merge(Tabular), fontWeight = FontWeight.SemiBold)
                            Text(l.end, style = MaterialTheme.typography.bodySmall.merge(Tabular), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(l.subject + if (l.group.isNotEmpty()) "  sk. ${l.group}" else "", style = MaterialTheme.typography.titleMedium)
                            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (on) Pill("Vynechám", MaterialTheme.colorScheme.primary)
                        else Pill(when (v) { Verdict.Ok -> "Můžeš"; Verdict.Last -> "Naposled"; Verdict.No -> "Ne"; Verdict.Unknown -> "?" }, tone)
                    }
                }
            }
        }
        item {
            Card {
                ListRow(if (plannedHere.isNotEmpty()) "Zrušit plán tohoto dne" else "Vynechat celý den", chevron = false, titleColor = MaterialTheme.colorScheme.primary, onClick = {
                    val rest = planned.filter { it.date != day.date }
                    planned.clear()
                    planned += rest
                    if (plannedHere.isEmpty()) planned += lessons.map { PlannedSkip(lessonKey(day.date, it), day.date, it.subject) }
                })
                if (planned.size > plannedHere.size) {
                    Divider()
                    ListRow("Zrušit celý plán (${hoursNom(planned.size)})", chevron = false, titleColor = MaterialTheme.colorScheme.error, onClick = { planned.clear() })
                }
            }
        }
        item {
            Footer("Klepnutím na hodinu si ji naplánuješ jako vynechanou a zbytek se přepočítá, i v ostatních dnech. Hranice je $LIMIT % za pololetí z každého předmětu. Kolik hodin do konce pololetí zbývá, je odhad podle stálého rozvrhu, bez suplování a akcí.")
        }
    }
}
