package io.github.oondraa.opensisakhk

import io.github.oondraa.opensisakhk.data.Day
import io.github.oondraa.opensisakhk.data.Lesson
import io.github.oondraa.opensisakhk.data.TimeSpan
import io.github.oondraa.opensisakhk.timetable.Item
import io.github.oondraa.opensisakhk.timetable.dayItems
import io.github.oondraa.opensisakhk.timetable.periodOf
import org.junit.Assert.assertEquals
import org.junit.Test

// Same cases as test/dayItems.test.ts, so the two ports can't drift apart unnoticed.
class DayItemsTest {
    private fun lesson(subject: String, start: String, end: String, cancelled: Boolean = false, group: String = "") =
        Lesson(subject = subject, start = start, end = end, room = "211", teacher = "HU", cancelled = cancelled, group = group)

    private fun day(lessons: List<Lesson>, lunch: TimeSpan? = null) = Day("2026-10-14", "14. středa", lessons, lunch)

    private fun shape(d: Day) = dayItems(d).map {
        "${when (it) { is Item.LessonItem -> "lesson"; is Item.Lunch -> "lunch"; is Item.Free -> "free" }} ${it.start}-${it.end}"
    }

    private fun names(d: Day) = dayItems(d).map {
        if (it is Item.LessonItem) "${it.lesson.subject}${if (it.lesson.cancelled) " x" else ""} ${it.was.joinToString(",") { w -> w.subject }}".trim() else "-"
    }

    @Test
    fun splitsLongBreakIntoLunchAndFree() {
        assertEquals(
            listOf("lesson 10:45-11:30", "lunch 11:30-12:00", "free 12:00-13:45", "lesson 13:45-14:30"),
            shape(day(listOf(lesson("MAT", "10:45", "11:30"), lesson("AGJ", "13:45", "14:30")), TimeSpan("11:30", "13:30"))),
        )
    }

    @Test
    fun keepsLunchAfterLastLesson() {
        assertEquals(
            listOf("lesson 8:00-8:45", "lesson 8:55-9:40", "lunch 11:30-12:00"),
            shape(day(listOf(lesson("MAT", "8:00", "8:45"), lesson("AGJ", "8:55", "9:40")), TimeSpan("11:30", "13:30"))),
        )
    }

    @Test
    fun foldsCancelledIntoReplacement() {
        val d = day(listOf(lesson("MAT", "8:45", "9:30", true), lesson("CJL", "9:45", "10:30"), lesson("FYZ", "8:45", "9:30", true), lesson("AGJ", "8:45", "9:30"), lesson("DEJ", "10:45", "11:30", true)))
        assertEquals(listOf("AGJ MAT,FYZ", "CJL", "DEJ x"), names(d))
    }

    @Test
    fun foldsIntoShiftedReplacement() {
        val d = day(listOf(lesson("P20", "12:45", "13:30"), lesson("OBN", "12:55", "13:40", true), lesson("P20", "13:45", "14:30"), lesson("CJL", "13:45", "14:30", true)))
        assertEquals(listOf("P20 OBN", "P20 CJL"), names(d))
    }

    @Test
    fun numbersPeriods() {
        assertEquals(
            listOf("3", "7–8", "6", "5", ""),
            listOf("9:45" to "10:30", "13:45" to "15:30", "12:55" to "13:40", "11:35" to "12:20", "6:00" to "6:40").map { periodOf(it.first, it.second) },
        )
    }
}
