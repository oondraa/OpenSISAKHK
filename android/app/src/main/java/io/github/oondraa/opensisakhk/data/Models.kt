package io.github.oondraa.opensisakhk.data

import kotlinx.serialization.Serializable

// The worker's JSON, field for field as the web app reads it (src/worker/parse.ts, src/web/api.ts).

@Serializable
data class Lesson(
    val id: String? = null,
    val subject: String = "",
    val start: String,
    val end: String,
    val room: String = "",
    val group: String = "",
    val teacher: String = "",
    val changed: Boolean = false,
    /** Struck out in SIS: stays in the list, but isn't taught. */
    val cancelled: Boolean = false,
    /** Put in on top of the timetable, often in place of a cancelled one. */
    val added: Boolean = false,
    val meet: String? = null,
)

@Serializable
data class TimeSpan(val start: String, val end: String)

@Serializable
data class Day(
    val date: String,
    val label: String = "",
    val lessons: List<Lesson> = emptyList(),
    val lunch: TimeSpan? = null,
)

@Serializable
data class TimetableResponse(val week: String, val days: List<Day> = emptyList())

@Serializable
data class OrderedMeal(val label: String, val number: Int? = null, val name: String = "")

@Serializable
data class LunchDay(val date: String, val ordered: OrderedMeal? = null)

@Serializable
data class LunchResponse(val connected: Boolean = false, val days: List<LunchDay> = emptyList(), val error: String? = null)

@Serializable
data class Me(
    val name: String? = null,
    val hasPhoto: Boolean = false,
    /** null = not confirmed yet */
    val `class`: String? = null,
    val pushDevices: Int = 0,
    val watching: Boolean = false,
    val strava: Boolean = false,
)

@Serializable
internal data class ErrorBody(val error: String? = null, val message: String? = null)

// ---------- grades ----------

@Serializable
data class Grade(
    val date: String = "",
    val value: String = "",
    val numeric: Double? = null,
    val type: String = "",
    val typeName: String = "",
    val weight: Double? = null,
    val name: String = "",
    val comment: String = "",
)

@Serializable
data class Subject(val name: String, val average: Double? = null, val grades: List<Grade> = emptyList())

@Serializable
data class GradesResponse(val subjects: List<Subject> = emptyList())

// ---------- absence ----------

@Serializable
data class AbsenceSubject(
    val subject: String,
    /** Share of taught lessons missed, in %. */
    val percent: Double = 0.0,
    val taught: Int = 0,
    val missed: Int = 0,
    val unexcused: Int = 0,
)

@Serializable
data class MissedLesson(val date: String, val period: Int, val subject: String = "", val teacher: String = "", val code: String = "")

@Serializable
data class Absence(val subjects: List<AbsenceSubject> = emptyList(), val total: AbsenceSubject? = null, val missed: List<MissedLesson> = emptyList())

// ---------- canteen ----------

@Serializable
data class Meal(
    val veta: String,
    val number: Int? = null,
    val label: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val ordered: Boolean = false,
    /** can be ordered, changed or cancelled right now */
    val editable: Boolean = false,
    val allergens: List<String> = emptyList(),
)

@Serializable
data class MenuDay(
    val date: String,
    val soup: String? = null,
    val meals: List<Meal> = emptyList(),
    /** the canteen doesn't cook that day: the reason */
    val closed: String? = null,
    /** ordering deadline in Prague local time, "2026-10-13T12:00:00" */
    val deadline: String? = null,
)

@Serializable
data class CanteenResponse(val connected: Boolean = false, val balance: Double? = null, val days: List<MenuDay> = emptyList(), val error: String? = null)

// ---------- finance ----------

@Serializable
data class FinanceRow(val date: String, val text: String = "", val amount: Double = 0.0)

@Serializable
data class FinanceSection(val title: String, val rows: List<FinanceRow> = emptyList(), val total: Double? = null)

@Serializable
data class FinanceBalance(val kind: String, val amount: Double = 0.0, val text: String = "")

@Serializable
data class FinanceAccount(val id: String, val title: String, val sections: List<FinanceSection> = emptyList(), val balance: FinanceBalance? = null)

@Serializable
data class Labeled(val label: String, val value: String = "", val url: String = "")

@Serializable
data class Finance(
    val accounts: List<FinanceAccount> = emptyList(),
    val symbols: List<Labeled> = emptyList(),
    val bankAccount: String? = null,
    val documents: List<Labeled> = emptyList(),
    /** SIS's banner over the statement, e.g. after an outage */
    val notice: String? = null,
    /** bank payments are loaded up to this day (YYYY-MM-DD) */
    val paymentsAsOf: String? = null,
)

// ---------- projects ----------

@Serializable
data class ProjectRole(val name: String, val choices: List<String> = emptyList())

@Serializable
data class Project(
    val number: String,
    val name: String = "",
    val leader: String = "",
    val company: String = "",
    val goal: String = "",
    val comment: String = "",
    val roles: List<ProjectRole> = emptyList(),
    val total: String = "",
)

@Serializable
data class ProjectChoice(val order: Int = 0, val number: String = "", val name: String = "", val role: String = "", val approved: Boolean = false)

@Serializable
data class Election(val id: String, val label: String)

@Serializable
data class Block(val subject: String, val block: String)

@Serializable
data class ProjectProfile(val `class`: String? = null, val email: String? = null, val groups: List<String> = emptyList(), val blocks: List<Block> = emptyList())

@Serializable
data class ProjectsResponse(
    val elections: List<Election> = emptyList(),
    val election: Election? = null,
    val mine: List<ProjectChoice> = emptyList(),
    val projects: List<Project> = emptyList(),
    val profile: ProjectProfile = ProjectProfile(),
)

// ---------- school timetable (weight room, free rooms, teachers) ----------

@Serializable
data class SchoolWeek(val start: String, val end: String, val week: Int)

@Serializable
data class SchoolWeeks(val weeks: List<SchoolWeek> = emptyList())
