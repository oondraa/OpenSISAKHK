package io.github.oondraa.opensisakhk.school

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import io.github.oondraa.opensisakhk.data.Api
import io.github.oondraa.opensisakhk.data.Cache
import io.github.oondraa.opensisakhk.data.SchoolWeeks
import io.github.oondraa.opensisakhk.data.defaultSchoolDay
import io.github.oondraa.opensisakhk.data.minutes
import io.github.oondraa.opensisakhk.data.monday
import io.github.oondraa.opensisakhk.data.nextSchoolDay
import io.github.oondraa.opensisakhk.data.nowMinutes
import io.github.oondraa.opensisakhk.data.today
import io.github.oondraa.opensisakhk.data.weekdayIndex
import io.github.oondraa.opensisakhk.timetable.PERIODS
import io.github.oondraa.opensisakhk.ui.Loaded
import io.github.oondraa.opensisakhk.ui.rememberCached
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.text.Collator
import java.time.LocalDate
import java.util.Locale

// Port of src/web/school.ts, Rooms.tsx (helpers), freeRooms.ts and whoTeaches.ts.

private val DAY_CODES = listOf("PO", "UT", "ST", "CT", "PA")

/** Public image name for a school day ("ST1"), null when the school doesn't publish that week. */
fun schoolFile(weeks: SchoolWeeks?, d: LocalDate): String? {
    val iso = d.toString()
    val week = weeks?.weeks?.find { it.start <= iso && iso <= it.end } ?: return null
    return if (d.weekdayIndex < 5) "${DAY_CODES[d.weekdayIndex]}${week.week}" else null
}

/** Cache key of a week's five school days, read from images "1" or "2". */
fun roomsKey(weeks: SchoolWeeks?, d: LocalDate): String? = schoolFile(weeks, d)?.let { "rooms:${it.drop(2)}:${d.monday()}" }

val WEEK_SER = ListSerializer(ListSerializer(Cell.serializer()))

/** Downloads one school-wide timetable image and reads it, right here on the phone. */
suspend fun readSchoolFile(file: String): List<Cell> {
    val bytes = Api.schoolImage(file)
    return withContext(Dispatchers.Default) {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        bmp.recycle()
        readSchoolDay(Rgb(bmp.width, bmp.height, px))
    }
}

fun cachedWeeks() = Cache.get("school-weeks", SchoolWeeks.serializer())

/** The cached read of `d`'s week, if any (home tiles, start days). */
fun cachedSchoolWeek(d: LocalDate): List<List<Cell>>? = roomsKey(cachedWeeks(), d)?.let { Cache.get(it, WEEK_SER) }

class SchoolWeek(val weeks: Loaded<SchoolWeeks>, val key: String?, val week: Loaded<List<List<Cell>>>)

/** The school timetable of `selected`'s whole week: the room list comes from all of it, and switching days is then instant. */
@Composable
fun rememberSchoolWeek(selected: LocalDate): SchoolWeek {
    val weeks = rememberCached("school-weeks", SchoolWeeks.serializer()) { Api.schoolWeeks() }
    val key = roomsKey(weeks.data, selected)
    val monday = selected.monday()
    val week = rememberCached(key, WEEK_SER) {
        coroutineScope {
            (0..4).map { i -> async { schoolFile(weeks.data, monday.plusDays(i.toLong()))?.let { readSchoolFile(it) } ?: emptyList() } }.awaitAll()
        }
    }
    return SchoolWeek(weeks, key, week)
}

fun lastPeriodOf(day: List<Cell>) = maxOf(8, day.maxOfOrNull { it.period } ?: 0)

/** Period to show first: the one on now (or next) today, otherwise the first one anybody has. */
fun autoPeriod(day: List<Cell>, live: Boolean, now: Int): Int {
    val first = day.minOfOrNull { it.period } ?: 1
    if (!live) return first
    val last = lastPeriodOf(day)
    val current = PERIODS.withIndex().indexOfFirst { (p, it) -> p <= last && now < minutes(it.second) }
    return if (current < 0) last else maxOf(first, current)
}

/** Today until its last period is over, then the next school day; decided from cache so it doesn't flip after a paint. */
fun schoolStartDay(): LocalDate {
    val t = today()
    val day = defaultSchoolDay()
    val week = cachedSchoolWeek(t)
    if (day != t || week == null) return day
    return if (nowMinutes() >= minutes(PERIODS[lastPeriodOf(week[t.weekdayIndex])].second)) nextSchoolDay(t) else t
}

private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("cs"))

/** "B13" < "206" < "211", numbers by value like localeCompare(…, { numeric: true }). */
private val roomOrder = Comparator<String> { a, b ->
    val na = a.toIntOrNull()
    val nb = b.toIntOrNull()
    if (na != null && nb != null) na.compareTo(nb) else collator.compare(a, b)
}

/** The sports hall and the weight room: nobody goes there to sit and study. */
private val NOT_CLASSROOMS = setOf("T0", "P0")

/** Elective blocks (B06): the school image names the block, not the rooms its groups are spread over. */
fun isBlock(c: Cell) = c.room.isEmpty() && Regex("^B\\d+$").matches(c.subject)

/** Every classroom the school timetable uses on any of these days. */
fun allRooms(days: List<List<Cell>>): List<String> =
    days.flatten().map { it.room }.filter { it.isNotEmpty() && it !in NOT_CLASSROOMS }.distinct().sortedWith(roomOrder)

/** `until`: period in which a lesson takes it, null = free for the rest of the day. */
data class FreeRoom(val room: String, val until: Int?)

/** All rooms minus the ones taken in `period`, each with how long it stays free. */
fun freeRooms(rooms: List<String>, day: List<Cell>, period: Int): List<FreeRoom> {
    fun taken(r: String, p: Int) = day.any { it.period == p && it.room == r }
    val later = day.map { it.period }.distinct().filter { it > period }.sorted()
    return rooms.filter { !taken(it, period) }.map { room -> FreeRoom(room, later.find { taken(room, it) }) }
}

/** Blocks running in `period` ("B06"), whose rooms may be taken without the image saying so. */
fun blocksAt(day: List<Cell>, period: Int) = day.filter { it.period == period && isBlock(it) }.map { it.subject }.distinct()

data class Teaching(val teacher: String, val subject: String, val classes: List<String>, val rooms: List<String>, val changed: Boolean)

/** `next`: their next period that day, null = done for the day. */
data class NotTeaching(val teacher: String, val next: Int?)

/**
 * Who teaches in `period` of a day and where. Everyone else who teaches that day is listed with when they teach
 * next: the image only knows lessons, so that's the closest to "is in school".
 */
fun teachersAt(day: List<Cell>, period: Int): Pair<List<Teaching>, List<NotTeaching>> {
    val now = day.filter { it.teacher.isNotEmpty() && it.period == period }.groupBy { it.teacher }
    val teaching = now.map { (teacher, cells) ->
        Teaching(
            teacher,
            cells.map { it.subject }.filter { it.isNotEmpty() }.distinct().joinToString(", "),
            cells.map { it.cls }.distinct(),
            cells.map { it.room }.filter { it.isNotEmpty() }.distinct().sortedWith(roomOrder),
            cells.any { it.changed },
        )
    }.sortedWith(compareBy(collator) { it.teacher })
    val free = day.map { it.teacher }.filter { it.isNotEmpty() && it !in now }.distinct().map { teacher ->
        NotTeaching(teacher, day.filter { it.teacher == teacher && it.period > period }.minOfOrNull { it.period })
    }.sortedWith(compareBy<NotTeaching> { it.next ?: 99 }.thenBy(collator) { it.teacher }) // still teaching first, soonest on top
    return teaching to free
}
