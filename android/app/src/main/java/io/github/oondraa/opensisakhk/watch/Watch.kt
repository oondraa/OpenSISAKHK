package io.github.oondraa.opensisakhk.watch

import io.github.oondraa.opensisakhk.data.Absence
import io.github.oondraa.opensisakhk.data.Day
import io.github.oondraa.opensisakhk.data.Lesson
import io.github.oondraa.opensisakhk.data.MissedLesson
import io.github.oondraa.opensisakhk.data.Subject
import io.github.oondraa.opensisakhk.data.minutes
import java.time.LocalDate

// Port of src/shared/watch.ts. The server keeps short hashes because it holds them for many students; here
// everything stays on the student's own phone, so the plain keys are kept.

/** Personal timetable as last seen: date → lesson start → what was in that slot. */
typealias SeenTimetable = Map<String, Map<String, String>>

/** `lesson`: the lesson now in that slot, or null when it was cancelled. */
data class TimetableChange(val date: String, val start: String, val lesson: Lesson?)

/**
 * Compares the personal timetable (from `today` on) with the last check. Only days that were already seen are
 * compared: a week that just came into view has nothing to differ from.
 */
fun compareTimetable(seen: SeenTimetable?, days: List<Day>, today: String): Pair<SeenTimetable, List<TimetableChange>> {
    val next = mutableMapOf<String, Map<String, String>>()
    val changes = mutableListOf<TimetableChange>()
    for (d in days) {
        if (d.date < today) continue
        // The "changed" flag is left out: SIS drops it again the next day without anything changing.
        // A cancelled lesson stays in SIS struck out: its slot is reported as cancelled, as if it were gone.
        val lessons = d.lessons.filter { !it.cancelled }
        val slots = lessons.associate { it.start to listOf(it.subject, it.room, it.teacher, it.group).joinToString("|") }
        next[d.date] = slots
        val before = seen?.get(d.date) ?: continue
        for (l in lessons) if (before[l.start] != slots[l.start]) changes += TimetableChange(d.date, l.start, l)
        for (start in before.keys) if (start !in slots) changes += TimetableChange(d.date, start, null)
    }
    return next to changes
}

data class NewGrade(val subject: String, val value: String, val name: String, val weight: Double?)

data class Seen(val grades: Set<String>, val absence: Set<String>)

/**
 * Compares a fresh reading with what was seen last time. The first check (`seen` = null) only remembers, so
 * switching it on doesn't flood the student with every grade of the semester.
 */
fun compare(seen: Seen?, subjects: List<Subject>, absence: Absence): Triple<Seen, List<NewGrade>, List<MissedLesson>> {
    val grades = subjects.flatMap { s ->
        s.grades.map { g -> listOf(s.name, g.date, g.value, g.type, g.name).joinToString("|") to NewGrade(s.name, g.value, g.name, g.weight) }
    }
    val missed = absence.missed.map { listOf(it.date, it.period, it.subject).joinToString("|") to it }
    val next = Seen(grades.map { it.first }.toSet(), missed.map { it.first }.toSet())
    if (seen == null) return Triple(next, emptyList(), emptyList())
    return Triple(next, grades.filter { it.first !in seen.grades }.map { it.second }, missed.filter { it.first !in seen.absence }.map { it.second })
}

private val DAYS = listOf("Po", "Út", "St", "Čt", "Pá", "So", "Ne")
private fun day(iso: String) = LocalDate.parse(iso).let { "${DAYS[it.dayOfWeek.value - 1]} ${it.dayOfMonth}. ${it.monthValue}." }
private fun num(w: Double) = if (w % 1.0 == 0.0) w.toInt().toString() else w.toString().replace('.', ',')

data class Message(val title: String, val body: String)

fun gradesMessage(grades: List<NewGrade>): Message {
    fun line(g: NewGrade) = "${g.subject}: ${g.value}${if (g.name.isNotEmpty()) " · ${g.name}" else ""}${g.weight?.let { " (váha ${num(it)})" } ?: ""}"
    return Message(
        if (grades.size == 1) "Nová známka" else "Nové známky (${grades.size})",
        grades.take(6).joinToString("\n") { line(it) } + if (grades.size > 6) "\na další ${grades.size - 6}" else "",
    )
}

fun absenceMessage(missed: List<MissedLesson>): Message {
    val lines = missed.groupBy { it.date }.toSortedMap().map { (date, ls) ->
        "${day(date)}: ${ls.sortedBy { it.period }.joinToString(", ") { "${it.period}. ${it.subject}" }}"
    }
    return Message(if (missed.size == 1) "Nová absence" else "Nová absence (${missed.size} h)", lines.joinToString("\n"))
}

fun timetableMessage(changes: List<TimetableChange>, today: String): Message {
    fun item(c: TimetableChange) =
        if (c.lesson != null) "${c.start} ${listOf(c.lesson.subject, c.lesson.room, c.lesson.teacher).filter { it.isNotEmpty() }.joinToString(" · ")}" else "${c.start} odpadá"
    val lines = changes.groupBy { it.date }.toSortedMap().map { (date, cs) ->
        "${if (date == today) "Dnes" else day(date)}: ${cs.sortedBy { minutes(it.start) }.joinToString(", ") { item(it) }}"
    }
    return Message("Změna rozvrhu", lines.joinToString("\n"))
}
