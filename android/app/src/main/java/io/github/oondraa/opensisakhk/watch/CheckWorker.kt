package io.github.oondraa.opensisakhk.watch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.oondraa.opensisakhk.MainActivity
import io.github.oondraa.opensisakhk.Part
import io.github.oondraa.opensisakhk.R
import io.github.oondraa.opensisakhk.data.Absence
import io.github.oondraa.opensisakhk.data.Api
import io.github.oondraa.opensisakhk.data.Cache
import io.github.oondraa.opensisakhk.data.GradesResponse
import io.github.oondraa.opensisakhk.data.Prefs
import io.github.oondraa.opensisakhk.data.TimetableResponse
import io.github.oondraa.opensisakhk.data.monday
import io.github.oondraa.opensisakhk.data.today
import io.github.oondraa.opensisakhk.widget.Widgets
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.util.concurrent.TimeUnit

/** Notifications on this phone: on/off and the channels, the switch in Nastavení. */
object Notifications {
    private val CHANNELS = listOf(
        Triple(Part.Timetable, "Změny rozvrhu", "Suplování, odpadlé a přidané hodiny"),
        Triple(Part.Grades, "Nové známky", "Známka, která přibyla v SIS"),
        Triple(Part.Absence, "Absence", "Nově zapsaná absence"),
    )

    val enabled get() = Prefs.get("notify", Boolean.serializer()) ?: false

    fun permitted(context: Context) =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun setEnabled(on: Boolean) {
        Prefs.set("notify", Boolean.serializer(), on)
        // Switching on starts from what's there now, instead of announcing every grade of the semester.
        if (on) Cache.remove(SEEN)
    }

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        for ((part, name, text) in CHANNELS) {
            nm.createNotificationChannel(NotificationChannel(part.name, name, NotificationManager.IMPORTANCE_HIGH).apply { description = text })
        }
    }

    /** One notification per kind: a newer one replaces the older (same id), like the web push `tag`. */
    fun post(context: Context, part: Part, msg: Message) {
        if (!enabled || !permitted(context)) return
        val open = PendingIntent.getActivity(
            context, part.ordinal,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_PART, part.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, part.name)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFFFF5A1F.toInt())
            .setContentTitle(msg.title)
            .setContentText(msg.body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(part.ordinal, n)
        } catch (e: SecurityException) {
            // permission withdrawn meanwhile
        }
    }
}

private const val SEEN = "seen"

@Serializable
private data class SeenState(val t: Map<String, Map<String, String>>? = null, val g: Set<String>? = null, val a: Set<String>? = null)

/**
 * Every ~15 minutes while signed in: refreshes this and next week's timetable (the widgets and the app open on it),
 * and with notifications on, compares timetable, grades and absence with the last check and tells what's new.
 * It runs on the phone with the session it already has: no password goes to the server for this.
 */
class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!Api.signedIn.value) return Result.success()
        val seen = Cache.get(SEEN, SeenState.serializer())
        var next = seen ?: SeenState()
        val t = today()
        val notify = Notifications.enabled

        runCatching {
            val weeks = listOf(t.monday(), t.monday().plusWeeks(1)).map { m ->
                Api.timetable(m.toString()).also { Cache.set("timetable:$m", TimetableResponse.serializer(), it) }
            }
            val (slots, changes) = compareTimetable(seen?.t, weeks.flatMap { it.days }, t.toString())
            next = next.copy(t = slots)
            if (notify && changes.isNotEmpty()) Notifications.post(applicationContext, Part.Timetable, timetableMessage(changes, t.toString()))
        }
        if (notify) runCatching {
            val grades = Api.grades().also { Cache.set("grades", GradesResponse.serializer(), it) }
            val absence = Api.absence().also { Cache.set("absence", Absence.serializer(), it) }
            val before = if (seen?.g != null && seen.a != null) Seen(seen.g, seen.a) else null
            val (now, newGrades, newMissed) = compare(before, grades.subjects, absence)
            next = next.copy(g = now.grades, a = now.absence)
            if (newGrades.isNotEmpty()) Notifications.post(applicationContext, Part.Grades, gradesMessage(newGrades))
            if (newMissed.isNotEmpty()) Notifications.post(applicationContext, Part.Absence, absenceMessage(newMissed))
        }
        if (Api.signedIn.value) Cache.set(SEEN, SeenState.serializer(), next)
        Widgets.update(applicationContext)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("check", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
