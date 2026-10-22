package io.github.oondraa.opensisakhk

import io.github.oondraa.opensisakhk.data.Absence
import io.github.oondraa.opensisakhk.data.Day
import io.github.oondraa.opensisakhk.data.Grade
import io.github.oondraa.opensisakhk.data.Lesson
import io.github.oondraa.opensisakhk.data.MissedLesson
import io.github.oondraa.opensisakhk.data.Subject
import io.github.oondraa.opensisakhk.watch.Message
import io.github.oondraa.opensisakhk.watch.NewGrade
import io.github.oondraa.opensisakhk.watch.absenceMessage
import io.github.oondraa.opensisakhk.watch.compare
import io.github.oondraa.opensisakhk.watch.compareTimetable
import io.github.oondraa.opensisakhk.watch.gradesMessage
import io.github.oondraa.opensisakhk.watch.timetableMessage
import org.junit.Assert.assertEquals
import org.junit.Test

// Same cases as test/watch.test.ts.
class WatchTest {
    private fun grade(value: String, name: String, date: String = "1.10.2026") = Grade(date = date, value = value, type = "P", weight = 3.0, name = name)
    private fun subjects(vararg g: Grade) = listOf(Subject("MAT", null, g.toList()))
    private fun absence(vararg m: MissedLesson) = Absence(missed = m.toList())

    @Test
    fun remembersFirstThenFindsNew() {
        val first = compare(null, subjects(grade("1", "Písemka")), absence())
        assertEquals(emptyList<NewGrade>(), first.second)
        assertEquals(1, first.first.grades.size)
        val l = MissedLesson("2026-10-12", 3, "MAT", "NO", "n")
        val r = compare(first.first, subjects(grade("1", "Písemka"), grade("2-", "Test", "5.10.2026")), absence(l))
        assertEquals(listOf(NewGrade("MAT", "2-", "Test", 3.0)), r.second)
        assertEquals(listOf(l), r.third)
    }

    @Test
    fun formatsMessages() {
        assertEquals(Message("Nová známka", "MAT: 1 · Písemka (váha 3)"), gradesMessage(listOf(NewGrade("MAT", "1", "Písemka", 3.0))))
        val m = absenceMessage(listOf(MissedLesson("2026-10-12", 4, "FYZ"), MissedLesson("2026-10-12", 3, "MAT")))
        assertEquals(Message("Nová absence (2 h)", "Po 12. 10.: 3. MAT, 4. FYZ"), m)
    }

    private fun lesson(start: String, subject: String, room: String, cancelled: Boolean = false) =
        Lesson(subject = subject, start = start, end = "", room = room, teacher = "NO", cancelled = cancelled)

    @Test
    fun reportsTimetableChanges() {
        val first = compareTimetable(null, listOf(Day("2026-10-13", "", listOf(lesson("8:00", "MAT", "211"), lesson("8:55", "FYZ", "B13")))), "2026-10-12")
        assertEquals(0, first.second.size)
        val r = compareTimetable(
            first.first,
            listOf(
                Day("2026-10-13", "", listOf(lesson("8:00", "MAT", "105"), lesson("10:00", "AGJ", "B13"))),
                Day("2026-10-14", "", listOf(lesson("8:00", "ELZ", "211"))),
            ),
            "2026-10-12",
        )
        assertEquals(listOf("8:00" to "105", "10:00" to "B13", "8:55" to null), r.second.map { it.start to it.lesson?.room })
        assertEquals("Út 13. 10.: 8:00 MAT · 105 · NO, 8:55 odpadá, 10:00 AGJ · B13 · NO", timetableMessage(r.second, "2026-10-12").body)
    }

    @Test
    fun struckOutIsCancelled() {
        val first = compareTimetable(null, listOf(Day("2026-10-13", "", listOf(lesson("8:00", "MAT", "211")))), "2026-10-12")
        val r = compareTimetable(first.first, listOf(Day("2026-10-13", "", listOf(lesson("8:00", "MAT", "211", cancelled = true)))), "2026-10-12")
        assertEquals(listOf("8:00" to null), r.second.map { it.start to it.lesson })
    }
}
