package io.github.oondraa.opensisakhk.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.oondraa.opensisakhk.MainActivity
import io.github.oondraa.opensisakhk.Part
import io.github.oondraa.opensisakhk.data.Api
import io.github.oondraa.opensisakhk.data.Cache
import io.github.oondraa.opensisakhk.data.Lesson
import io.github.oondraa.opensisakhk.data.TimetableResponse
import io.github.oondraa.opensisakhk.data.WEEKDAYS
import io.github.oondraa.opensisakhk.data.inMinutes
import io.github.oondraa.opensisakhk.data.minutes
import io.github.oondraa.opensisakhk.data.monday
import io.github.oondraa.opensisakhk.data.nowMinutes
import io.github.oondraa.opensisakhk.data.today
import io.github.oondraa.opensisakhk.data.weekdayIndex
import io.github.oondraa.opensisakhk.timetable.held
import io.github.oondraa.opensisakhk.timetable.periodOf
import io.github.oondraa.opensisakhk.timetable.startDay
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** The day a widget shows (today until its last lesson is over, then the next school day) and its lessons. */
private class Shown(val date: LocalDate, val lessons: List<Lesson>?, val now: Int) {
    val live get() = date == today()
    val current get() = if (live) lessons?.find { minutes(it.start) <= now && now < minutes(it.end) } else null
    val next get() = lessons?.find { !live || minutes(it.start) > now }
    val label get() = when (date) {
        today() -> "Dnes"
        today().plusDays(1) -> "Zítra"
        else -> WEEKDAYS[date.weekdayIndex]
    }
}

private fun shown(): Shown {
    val t = today()
    val day = startDay(Cache.get("timetable:${t.monday()}", TimetableResponse.serializer())?.days)
    val week = Cache.get("timetable:${day.monday()}", TimetableResponse.serializer())
    val lessons = week?.days?.find { it.date == day.toString() }?.let { held(it.lessons).sortedBy { l -> minutes(l.start) } }
    return Shown(day, lessons, nowMinutes())
}

private fun open(context: Context) = actionStartActivity(
    Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_PART, Part.Timetable.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
)

@Composable
private fun Frame(context: Context, content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp).padding(16.dp).clickable(open(context)),
        contentAlignment = Alignment.TopStart,
    ) { content() }
}

@Composable
private fun Small(text: String, color: ColorProvider = GlanceTheme.colors.onSurfaceVariant, bold: Boolean = false, size: Int = 13) =
    Text(text, style = TextStyle(color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal), maxLines = 1)

/** "Teď a další": the lesson on now (or next) big, with its room, and what follows. */
class NowWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = shown()
        provideContent { GlanceTheme { Frame(context) { NowContent(s) } } }
    }
}

@Composable
private fun NowContent(s: Shown) {
    val wide = LocalSize.current.width >= 220.dp
    val c = GlanceTheme.colors
    val lessons = s.lessons
    val current = s.current
    val next = s.next
    Column(GlanceModifier.fillMaxSize()) {
        when {
            lessons == null -> {
                Small("Rozvrh", c.primary, bold = true)
                Spacer(GlanceModifier.defaultWeight())
                Text("Otevři appku", style = TextStyle(color = c.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold))
            }
            lessons.isEmpty() -> {
                Small(s.label, c.primary, bold = true)
                Spacer(GlanceModifier.defaultWeight())
                Text("Volno", style = TextStyle(color = c.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold))
                Small("Žádná hodina")
            }
            current == null && next == null -> Unit
            else -> {
                val l = current ?: next!!
                Small(
                    when {
                        current != null -> "Teď · končí ${inMinutes(minutes(current.end) - s.now)}"
                        s.live -> "Další · ${inMinutes(minutes(l.start) - s.now)}"
                        else -> "${s.label} · ${l.start}"
                    },
                    c.primary, bold = true,
                )
                Spacer(GlanceModifier.defaultWeight())
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(l.subject, style = TextStyle(color = c.onSurface, fontSize = 32.sp, fontWeight = FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    Text(l.room, style = TextStyle(color = c.onSurfaceVariant, fontSize = 22.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                }
                val after = if (current != null) next else lessons.getOrNull(lessons.indexOf(l) + 1)
                Small(
                    if (after != null) "Pak ${after.subject} · ${after.room}${if (wide) " · ${after.start}" else ""}"
                    else if (current != null) "Poslední hodina" else "${l.start}–${l.end} · ${l.teacher}",
                )
            }
        }
    }
}

/** "Rozvrh dne": the whole day, one row per lesson, the one on now highlighted. */
class DayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = shown()
        provideContent { GlanceTheme { Frame(context) { DayContent(s) } } }
    }
}

@Composable
private fun DayContent(s: Shown) {
    val c = GlanceTheme.colors
    val lessons = s.lessons
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(s.label, style = TextStyle(color = c.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
            Small("${s.date.dayOfMonth}. ${s.date.monthValue}.")
        }
        Spacer(GlanceModifier.height(8.dp))
        when {
            lessons == null -> Small("Otevři appku, ať se rozvrh načte.")
            lessons.isEmpty() -> Text("Volno, žádná hodina.", style = TextStyle(color = c.onSurfaceVariant, fontSize = 15.sp))
            else -> LazyColumn {
                items(lessons, itemId = { minutes(it.start).toLong() * 100 + it.subject.hashCode().mod(100) }) { l ->
                    val on = s.live && minutes(l.start) <= s.now && s.now < minutes(l.end)
                    val past = s.live && s.now >= minutes(l.end)
                    val strong = if (past) c.onSurfaceVariant else c.onSurface
                    Column {
                        Row(
                            GlanceModifier.fillMaxWidth().cornerRadius(12.dp).background(if (on) c.primaryContainer else c.widgetBackground).padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(periodOf(l.start, l.end), style = TextStyle(color = if (on) c.onPrimaryContainer else c.onSurfaceVariant, fontSize = 15.sp, fontWeight = FontWeight.Medium), modifier = GlanceModifier.width(30.dp))
                            Column(GlanceModifier.defaultWeight()) {
                                Text(l.subject, style = TextStyle(color = if (on) c.onPrimaryContainer else strong, fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                                Small("${l.start}–${l.end} · ${l.teacher}", if (on) c.onPrimaryContainer else c.onSurfaceVariant, size = 12)
                            }
                            Text(l.room, style = TextStyle(color = if (on) c.onPrimaryContainer else strong, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                        }
                        Spacer(GlanceModifier.height(2.dp))
                    }
                }
            }
        }
    }
}

class NowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowWidget()
}

class DayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DayWidget()
}

object Widgets {
    /** Redraws every widget and books the next redraw for the next lesson start or end today. */
    suspend fun update(context: Context) {
        NowWidget().updateAll(context)
        DayWidget().updateAll(context)
        val ids = GlanceAppWidgetManager(context).let { it.getGlanceIds(NowWidget::class.java) + it.getGlanceIds(DayWidget::class.java) }
        if (ids.isEmpty() || !Api.signedIn.value) return
        val s = shown()
        val now = nowMinutes()
        val nextChange = if (s.live) s.lessons.orEmpty().flatMap { listOf(minutes(it.start), minutes(it.end)) }.filter { it > now }.minOrNull() else null
        // After the day's last lesson the widget moves on to the next day; the periodic check redraws it later too.
        val delay = (nextChange ?: (now + 60)) - now
        WorkManager.getInstance(context).enqueueUniqueWork(
            "widget-tick",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<TickWorker>().setInitialDelay(delay * 60L - java.time.LocalTime.now().second, TimeUnit.SECONDS).build(),
        )
    }
}

/** Redraws the widgets at a lesson boundary. */
class TickWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Widgets.update(applicationContext)
        return Result.success()
    }
}
