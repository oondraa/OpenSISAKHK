package io.github.oondraa.opensisakhk.timetable

import io.github.oondraa.opensisakhk.data.Day
import io.github.oondraa.opensisakhk.data.Lesson
import io.github.oondraa.opensisakhk.data.hhmm
import io.github.oondraa.opensisakhk.data.minutes
import io.github.oondraa.opensisakhk.data.nextSchoolDay
import io.github.oondraa.opensisakhk.data.nowMinutes
import io.github.oondraa.opensisakhk.data.today
import io.github.oondraa.opensisakhk.data.defaultSchoolDay
import java.time.LocalDate

// Port of src/web/Timetable.tsx (dayItems, periodOf, startDay): keep the two in step.

/** The school's bell schedule, period 0 at 7:00 (src/shared/schoolOcr.ts PERIODS). */
val PERIODS = listOf(
    "7:00" to "7:45", "7:50" to "8:35", "8:45" to "9:30", "9:45" to "10:30", "10:45" to "11:30", "11:45" to "12:30",
    "12:45" to "13:30", "13:45" to "14:30", "14:45" to "15:30", "15:45" to "16:30", "16:45" to "17:30", "17:45" to "18:30",
)

/** Lessons that actually take place: SIS keeps a cancelled one in the day, struck out. */
fun held(lessons: List<Lesson>) = lessons.filter { !it.cancelled }

sealed interface Item {
    val start: String
    val end: String

    /** `was`: the cancelled lessons whose slot this one took. */
    data class LessonItem(val lesson: Lesson, val was: List<Lesson> = emptyList()) : Item {
        override val start get() = lesson.start
        override val end get() = lesson.end
    }

    data class Lunch(override val start: String, override val end: String) : Item
    data class Free(override val start: String, override val end: String) : Item
}

private const val LUNCH_MIN = 30 // eating takes about this long; SIS marks the whole window the canteen serves in
private const val FREE_MIN = 20 // a gap at least this long gets its own row; shorter ones are ordinary breaks

/**
 * Lessons with what's between them: a long gap shows as free time, and lunch takes only its ~30 minutes of it
 * (at the start of the gap, or when the canteen opens). A cancelled lesson with something else taught in its slot
 * folds into that lesson as `was`; one with nothing in its place keeps its own row, slotted in by its start.
 */
fun dayItems(day: Day): List<Item> {
    val lessons = held(day.lessons).sortedBy { minutes(it.start) }
    val items = mutableListOf<Item>()
    val lunch = day.lunch?.let { minutes(it.start) to minutes(it.end) }
    var lunchPlaced = false
    fun free(a: Int, b: Int) {
        if (b - a >= FREE_MIN) items += Item.Free(hhmm(a), hhmm(b))
    }
    lessons.forEachIndexed { i, lesson ->
        items += Item.LessonItem(lesson)
        val next = lessons.getOrNull(i + 1) ?: return@forEachIndexed
        val g0 = minutes(lesson.end)
        val g1 = minutes(next.start)
        if (lunch != null && !lunchPlaced && lunch.first < g1 && lunch.second > g0 && g1 - maxOf(g0, lunch.first) >= 15) {
            val ls = maxOf(g0, lunch.first)
            val le = minOf(ls + LUNCH_MIN, g1)
            free(g0, ls)
            items += Item.Lunch(hhmm(ls), hhmm(le))
            free(le, g1)
            lunchPlaced = true
        } else free(g0, g1)
    }
    if (lunch != null && !lunchPlaced) items += Item.Lunch(hhmm(lunch.first), hhmm(minOf(lunch.first + LUNCH_MIN, lunch.second)))

    for (lesson in day.lessons.filter { it.cancelled }) {
        // Its slot is whatever overlaps it in time: SIS can shift the replacement by a few minutes (12:45 for a 12:55).
        // Of those, the one of the same subject or group took its place, else the one overlapping it most.
        fun overlap(it: Item) = minOf(minutes(it.end), minutes(lesson.end)) - maxOf(minutes(it.start), minutes(lesson.start))
        val slot = items.withIndex()
            .filter { (_, it) -> it is Item.LessonItem && !it.lesson.cancelled && overlap(it) > 0 }
            .sortedByDescending { overlap(it.value) }
        val by = slot.firstOrNull { (it.value as Item.LessonItem).lesson.subject == lesson.subject }
            ?: slot.firstOrNull { lesson.group.isNotEmpty() && (it.value as Item.LessonItem).lesson.group == lesson.group }
            ?: slot.firstOrNull()
        if (by != null) {
            val it = by.value as Item.LessonItem
            items[by.index] = it.copy(was = it.was + lesson)
        } else items += Item.LessonItem(lesson)
    }
    return items.sortedBy { minutes(it.start) }
}

/**
 * School periods a lesson spans ("3", "1–2"), the way students count them. A lesson on a shifted schedule
 * (12:55–13:40) counts as the period it mostly overlaps. Empty when it overlaps none by half.
 */
fun periodOf(start: String, end: String): String {
    val s = minutes(start)
    val e = minutes(end)
    val covered = PERIODS.withIndex().filter { (_, p) ->
        val overlap = minOf(e, minutes(p.second)) - maxOf(s, minutes(p.first))
        overlap * 2 >= minutes(p.second) - minutes(p.first)
    }.map { it.index }
    if (covered.isEmpty()) return ""
    return if (covered.last() > covered.first()) "${covered.first()}–${covered.last()}" else "${covered.first()}"
}

/** Today until its last lesson is over, then the next school day: nobody opens the app to see what already was. */
fun startDay(days: List<Day>?): LocalDate {
    val t = today()
    val day = defaultSchoolDay()
    val lessons = held(days?.find { it.date == t.toString() }?.lessons.orEmpty())
    if (day != t || lessons.isEmpty()) return day
    return if (nowMinutes() >= lessons.maxOf { minutes(it.end) }) nextSchoolDay(t) else t
}

/** What a changed lesson's detail says: field by field what it was and is now, or why there's nothing to compare. */
data class ChangeLine(val label: String, val from: String?, val to: String)

fun changeLines(l: Lesson, was: List<Lesson>): List<ChangeLine> {
    if (l.cancelled) return listOf(ChangeLine("Odpadá", null, listOf(l.teacher, if (l.room.isNotEmpty()) "učebna ${l.room}" else "").filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "nic za ni není" }))
    if (was.isNotEmpty()) return was.flatMap { o ->
        listOf(
            ChangeLine("Předmět", o.subject, l.subject),
            ChangeLine("Učitel", o.teacher, l.teacher),
            ChangeLine("Učebna", o.room, l.room),
            ChangeLine("Skupina", o.group, l.group),
        ).filter { it.from != it.to && (!it.from.isNullOrEmpty() || it.to.isNotEmpty()) }
            .ifEmpty { listOf(ChangeLine("Odpadá", null, "${o.subject}, místo ní stejná hodina znovu")) }
    }
    if (l.added) return listOf(ChangeLine("Navíc", null, "v běžném rozvrhu tahle hodina není"))
    return listOf(ChangeLine("Změna", null, "SIS u hodiny hlásí změnu, ale neuvádí, co bylo původně"))
}

/** The tag on a lesson row, empty when nothing about it is unusual. */
fun tagOf(l: Lesson, was: List<Lesson>): String = when {
    l.cancelled -> "odpadá"
    was.isNotEmpty() -> if (was.any { it.subject == l.subject }) "změna" else "náhrada"
    l.added -> "přidaná"
    l.changed -> "změna"
    else -> ""
}
