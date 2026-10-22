package io.github.oondraa.opensisakhk.grades

import io.github.oondraa.opensisakhk.data.Grade
import kotlin.math.floor

// Port of src/web/gradePlan.ts.

/**
 * Final grade from the average: under 1.5 is a 1, under 2.5 a 2 and so on. The school doesn't publish how it rounds a
 * x.50 exactly, so a target counts as reached only strictly under the line: the safe side.
 * Grades are kept doubled (1- = 1.5 → 3) so 2.50 is never read as 2.4999.
 */
private val SCALE = listOf(2, 3, 4, 5, 6, 7, 8, 9, 10) // 1, 1-, 2, 2-, … 5, doubled

fun gradeLabel(doubled: Int) = "${doubled / 2}${if (doubled % 2 != 0) "-" else ""}"

/** Grades that count: a number (not "N", "x"…) and a weight. */
private fun counted(grades: List<Grade>) = grades.filter { it.numeric != null && it.weight != null && it.weight > 0 }

/** Weighted average the way SIS computes it, or null without a counted grade. */
fun average(grades: List<Grade>): Double? {
    val gs = counted(grades)
    val w = gs.sumOf { it.weight!! }
    return if (w > 0) gs.sumOf { it.numeric!! * it.weight!! } / w else null
}

/** Final grade (1–5) the average leads to; x.50 already counts as the worse grade. */
fun finalGrade(avg: Double) = floor(avg + 0.5 + 1e-9).toInt().coerceIn(1, 5)

/** Weights the subject's grades have, most used first (ties: heavier first). */
fun weights(grades: List<Grade>): List<Double> =
    counted(grades).groupingBy { it.weight!! }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<Double, Int>> { it.value }.thenByDescending { it.key }).map { it.key }

sealed interface Need {
    /** Any grade from the next test keeps the target, even a 5. */
    data object Safe : Need

    /** The worst grade (doubled) the next test can be. */
    data class Grade(val worst: Int) : Need

    /** Even a 1 isn't enough: this many 1s of this weight in a row. */
    data class Ones(val count: Int) : Need
}

/** What the next test(s) of weight `w` have to be for the subject to end on `target` (1–5). */
fun need(grades: List<Grade>, target: Int, w: Double): Need {
    if (target >= 5) return Need.Safe
    val gs = counted(grades)
    val s = gs.sumOf { 2 * it.numeric!! * it.weight!! }
    val bigW = gs.sumOf { it.weight!! }
    val t = 2 * target + 1 // the line, doubled: target 2 → under 2.5 → 5
    // (S + w·g) / (W + w) < T  ⇔  w·g < T·(W + w) − S
    val ok = SCALE.filter { w * it < t * (bigW + w) - s }
    if (ok.size == SCALE.size) return Need.Safe
    if (ok.isNotEmpty()) return Need.Grade(ok.last())
    // k ones: S + 2kw < T(W + kw)  ⇔  k·w·(T − 2) > S − T·W
    return Need.Ones(floor((s - t * bigW) / (w * (t - 2))).toInt() + 1)
}
