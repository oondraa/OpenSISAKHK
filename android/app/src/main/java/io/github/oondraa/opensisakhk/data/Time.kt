package io.github.oondraa.opensisakhk.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// School time is Prague time, wherever the phone is.
private val PRAGUE: ZoneId = ZoneId.of("Europe/Prague")

val WEEKDAYS = listOf("Pondělí", "Úterý", "Středa", "Čtvrtek", "Pátek", "Sobota", "Neděle")
val WEEKDAYS_SHORT = listOf("PO", "ÚT", "ST", "ČT", "PÁ", "SO", "NE")
private val MONTHS = listOf("ledna", "února", "března", "dubna", "května", "června", "července", "srpna", "září", "října", "listopadu", "prosince")

fun today(): LocalDate = LocalDate.now(PRAGUE)

fun nowMinutes(): Int = LocalTime.now(PRAGUE).let { it.hour * 60 + it.minute }

/** "9:45" → 585 */
fun minutes(t: String): Int {
    val (h, m) = t.split(":").map { it.trim().toInt() }
    return h * 60 + m
}

fun hhmm(m: Int) = "${m / 60}:${(m % 60).toString().padStart(2, '0')}"

/** 0 = Monday … 6 = Sunday */
val LocalDate.weekdayIndex get() = dayOfWeek.value - 1

fun LocalDate.monday(): LocalDate = minusDays(weekdayIndex.toLong())

/** School day to show by default: today on weekdays, next Monday on weekends. */
fun defaultSchoolDay(): LocalDate = today().let { if (it.weekdayIndex < 5) it else it.plusDays(7L - it.weekdayIndex) }

/** The weekday after `d` (Friday → Monday). */
fun nextSchoolDay(d: LocalDate): LocalDate = if (d.dayOfWeek >= DayOfWeek.FRIDAY) d.plusDays(7L - d.weekdayIndex) else d.plusDays(1)

/** "Čtvrtek 22. října" */
fun longDate(d: LocalDate) = "${WEEKDAYS[d.weekdayIndex]} ${d.dayOfMonth}. ${MONTHS[d.monthValue - 1]}"

fun inMinutes(n: Int): String = when {
    n < 1 -> "teď"
    n < 60 -> "za $n min"
    else -> "za ${n / 60} h${if (n % 60 != 0) " ${n % 60} min" else ""}"
}

/** "1 h 25 min", "40 min" */
fun span(m: Int): String = if (m >= 60) "${m / 60} h${if (m % 60 != 0) " ${m % 60} min" else ""}" else "$m min"
