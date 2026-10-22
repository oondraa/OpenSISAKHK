package io.github.oondraa.opensisakhk

import io.github.oondraa.opensisakhk.absence.Plan
import io.github.oondraa.opensisakhk.absence.Verdict
import io.github.oondraa.opensisakhk.absence.blockers
import io.github.oondraa.opensisakhk.absence.budgets
import io.github.oondraa.opensisakhk.absence.plan
import io.github.oondraa.opensisakhk.absence.remainingLessons
import io.github.oondraa.opensisakhk.absence.semesterEnd
import io.github.oondraa.opensisakhk.absence.verdict
import io.github.oondraa.opensisakhk.data.Day
import io.github.oondraa.opensisakhk.data.Grade
import io.github.oondraa.opensisakhk.data.Lesson
import io.github.oondraa.opensisakhk.grades.Need
import io.github.oondraa.opensisakhk.grades.average
import io.github.oondraa.opensisakhk.grades.finalGrade
import io.github.oondraa.opensisakhk.grades.gradeLabel
import io.github.oondraa.opensisakhk.grades.need
import io.github.oondraa.opensisakhk.grades.weights
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

// Same cases as test/absencePlan.test.ts and test/gradePlan.test.ts.
class PlansTest {
    private fun week(monday: String, perDay: List<List<String>>) = perDay.mapIndexed { i, subjects ->
        Day(LocalDate.parse(monday).plusDays(i.toLong()).toString(), "", subjects.map { Lesson(subject = it, start = "8:00", end = "8:45") })
    }

    @Test
    fun plansAttendance() {
        assertEquals(4, plan(4, 10, null).needed)
        assertEquals(1, plan(3, 10, null).needed)
        assertEquals(0, plan(2, 10, null).needed)
        assertEquals(Plan(0, 10, 3, 10.0), plan(2, 10, 10))
        plan(10, 10, 2).let { assertEquals(listOf(24, 0, 83.3), listOf(it.needed, it.reserve, it.best)) }
    }

    @Test
    fun countsRemainingLessons() {
        val a = week("2026-10-12", listOf(listOf("MAT", "MAT"), listOf("FYZ"), listOf(), listOf(), listOf("TEV")))
        val b = week("2026-10-19", listOf(listOf("MAT"), listOf(), listOf(), listOf(), listOf()))
        assertEquals(mapOf("MAT" to 2, "FYZ" to 1), remainingLessons(LocalDate.parse("2026-10-12"), a, b, LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-30")))
        assertEquals(LocalDate.parse("2027-01-28"), semesterEnd(LocalDate.parse("2026-10-12")))
        assertEquals(LocalDate.parse("2027-06-30"), semesterEnd(LocalDate.parse("2027-03-01")))
    }

    @Test
    fun skipCalculator() {
        val reserves = mapOf("MAT" to 3, "FYZ" to 1, "CJL" to 0, "NEW" to null)
        val b = budgets(reserves, listOf("MAT", "MAT", "FYZ"))
        assertEquals(listOf(1, 0, null), listOf(b["MAT"], b["FYZ"], b["NEW"]))
        assertEquals(listOf(Verdict.Ok, Verdict.Last, Verdict.No, Verdict.No, Verdict.Unknown), listOf(3, 1, 0, -1, null).map { verdict(it) })
        assertEquals(emptyList<String>(), blockers(reserves, listOf("MAT", "MAT", "FYZ")))
        assertEquals(listOf("FYZ", "CJL"), blockers(reserves, listOf("FYZ", "FYZ", "CJL")))
        assertEquals(listOf("NEW", "XYZ"), blockers(reserves, listOf("NEW", "XYZ")))
    }

    private fun g(numeric: Double?, weight: Double?) = Grade(numeric = numeric, weight = weight)

    @Test
    fun plansGrades() {
        assertEquals(8.0 / 6, average(listOf(g(1.0, 5.0), g(3.0, 1.0), g(null, 2.0), g(5.0, null)))!!, 1e-9)
        assertEquals(null, average(emptyList()))
        assertEquals(2, finalGrade(2.49))
        assertEquals(3, finalGrade(2.5))
        assertEquals(listOf(5.0, 2.0, 1.0), weights(listOf(g(1.0, 2.0), g(2.0, 5.0), g(3.0, 5.0), g(1.0, 1.0))))
        assertEquals(Need.Grade(4), need(listOf(g(2.0, 1.0), g(3.0, 1.0)), 2, 1.0))
        assertEquals(Need.Safe, need(listOf(g(2.0, 1.0), g(3.0, 1.0)), 3, 1.0))
        assertEquals("2-", gradeLabel(5))
        assertEquals(Need.Ones(2), need(listOf(g(4.0, 5.0)), 2, 5.0))
        assertEquals(Need.Grade(3), need(listOf(g(3.0, 1.0)), 2, 1.0))
        assertEquals(Need.Ones(8), need(listOf(g(5.0, 1.0)), 1, 1.0))
        assertEquals(Need.Grade(2), need(emptyList(), 1, 5.0))
        assertEquals(Need.Grade(4), need(emptyList(), 2, 5.0))
    }
}
