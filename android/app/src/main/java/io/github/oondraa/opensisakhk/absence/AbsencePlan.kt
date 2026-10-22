package io.github.oondraa.opensisakhk.absence

import io.github.oondraa.opensisakhk.data.Day
import io.github.oondraa.opensisakhk.data.monday
import io.github.oondraa.opensisakhk.data.weekdayIndex
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

// Port of src/web/absencePlan.ts.

/**
 * School rule: missing more than 30–35 % of a subject's lessons means the teacher may refuse to grade it
 * (opravné zkoušky). 30 % is the safe side of that range, so it is the line the app plans against.
 */
const val LIMIT = 30
const val LIMIT_HARD = 35

/** Days without lessons in school year 2026/27 (Královéhradecký kraj). Spring break isn't known yet. */
private val HOLIDAYS = listOf(
    "2026-10-28" to "2026-10-30", // státní svátek + podzimní prázdniny
    "2026-11-17" to "2026-11-17",
    "2026-12-23" to "2027-01-01", // vánoční prázdniny
    "2027-01-29" to "2027-01-29", // pololetní prázdniny
    "2027-03-25" to "2027-03-29", // velikonoční prázdniny, Velký pátek, Velikonoční pondělí
)

private fun isHoliday(iso: String) = HOLIDAYS.any { (from, to) -> iso in from..to }

/** Last teaching day of the semester `today` falls in. */
fun semesterEnd(today: LocalDate): LocalDate {
    val y = today.year
    val end = when {
        today.monthValue >= 9 -> LocalDate.of(y + 1, 1, 31)
        today.monthValue == 1 -> LocalDate.of(y, 1, 31)
        else -> LocalDate.of(y, 6, 30)
    }
    return if (end == LocalDate.of(2027, 1, 31)) LocalDate.of(2027, 1, 28) else end // pololetní vysvědčení 28. 1. 2027
}

/**
 * Lessons per subject from tomorrow until `end`, read off the permanent timetable of two consecutive weeks (the
 * school alternates week 1 and week 2) starting on `weekA`, skipping holidays.
 */
fun remainingLessons(weekA: LocalDate, a: List<Day>, b: List<Day>, today: LocalDate, end: LocalDate): Map<String, Int> {
    val out = mutableMapOf<String, Int>()
    var d = today.plusDays(1)
    while (d <= end) {
        val wd = d.weekdayIndex
        if (wd <= 4 && !isHoliday(d.toString())) {
            val weeks = ChronoUnit.WEEKS.between(weekA, d.monday())
            val day = (if (weeks % 2 == 0L) a else b).find { LocalDate.parse(it.date).weekdayIndex == wd }
            for (l in day?.lessons.orEmpty()) out[l.subject] = (out[l.subject] ?: 0) + 1
        }
        d = d.plusDays(1)
    }
    return out
}

data class Plan(
    /** Lessons in a row the student must attend to get under LIMIT; 0 when already under. */
    val needed: Int,
    /** Lessons left in the semester, if known. */
    val remaining: Int?,
    /** How many of the remaining lessons can still be missed while ending the semester under LIMIT. */
    val reserve: Int?,
    /** Share missed at the end of the semester if no other lesson is missed. */
    val best: Double?,
)

/** Integer arithmetic only, so 30.0 % is never misread as 29.999 %. */
fun plan(missed: Int, taught: Int, remaining: Int?): Plan {
    val over = 100 * missed >= LIMIT * taught && missed > 0
    val needed = if (over) Math.floorDiv(100 * missed - LIMIT * taught, LIMIT) + 1 else 0
    if (remaining == null) return Plan(needed, null, null, null)
    val total = taught + remaining
    val reserve = maxOf(0, minOf(remaining, ceil((LIMIT * total - 100 * missed) / 100.0).toInt() - 1))
    return Plan(needed, remaining, reserve, if (total > 0) (1000.0 * missed / total).roundToInt() / 10.0 else 0.0)
}

/** ok: skippable with some to spare, last: skippable but it's the last one, no: it would end the semester over LIMIT. */
enum class Verdict { Ok, Last, No, Unknown }

fun verdict(budget: Int?): Verdict = when {
    budget == null -> Verdict.Unknown
    budget >= 2 -> Verdict.Ok
    budget == 1 -> Verdict.Last
    else -> Verdict.No
}

/** Lessons left to skip per subject once `planned` (subject of every planned skip) are taken off the reserves. */
fun budgets(reserves: Map<String, Int?>, planned: List<String>): Map<String, Int?> {
    val out = reserves.toMutableMap()
    for (s in planned) out[s]?.let { out[s] = it - 1 }
    return out
}

/** Subjects whose budget can't cover skipping all of `subjects` (one entry per lesson) on top of what's planned. */
fun blockers(budget: Map<String, Int?>, subjects: List<String>): List<String> =
    subjects.groupingBy { it }.eachCount().filter { (s, n) -> (budget[s] ?: Int.MIN_VALUE) < n }.keys.toList()
