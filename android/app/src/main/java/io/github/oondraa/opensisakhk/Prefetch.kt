package io.github.oondraa.opensisakhk

import io.github.oondraa.opensisakhk.data.Absence
import io.github.oondraa.opensisakhk.data.Api
import io.github.oondraa.opensisakhk.data.Cache
import io.github.oondraa.opensisakhk.data.CanteenResponse
import io.github.oondraa.opensisakhk.data.GradesResponse
import io.github.oondraa.opensisakhk.data.Me
import io.github.oondraa.opensisakhk.data.ProjectsResponse
import io.github.oondraa.opensisakhk.data.SchoolWeeks
import io.github.oondraa.opensisakhk.data.monday
import io.github.oondraa.opensisakhk.data.today
import io.github.oondraa.opensisakhk.school.WEEK_SER
import io.github.oondraa.opensisakhk.school.readSchoolFile
import io.github.oondraa.opensisakhk.school.roomsKey
import io.github.oondraa.opensisakhk.school.schoolFile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.KSerializer

/**
 * Loads what the home tiles show, all at once and quietly: a part that fails just keeps its last copy. `onEach` runs
 * after every part that arrived, so the home fills in as they come.
 */
suspend fun prefetch(onEach: () -> Unit) = coroutineScope {
    suspend fun <T> keep(key: String, ser: KSerializer<T>, fetch: suspend () -> T) {
        runCatching { fetch() }.onSuccess {
            Cache.set(key, ser, it)
            onEach()
        }
    }
    listOf(
        async { keep("me", Me.serializer()) { Api.me() } },
        async { keep("grades", GradesResponse.serializer()) { Api.grades() } },
        async { keep("absence", Absence.serializer()) { Api.absence() } },
        async { keep("canteen", CanteenResponse.serializer()) { Api.canteen() } },
        async { keep("projects:latest", ProjectsResponse.serializer()) { Api.projects() } },
        async {
            // This week's school timetable, read once per week (it's five images to download and read).
            val weeks = runCatching { Api.schoolWeeks() }.getOrNull() ?: return@async
            Cache.set("school-weeks", SchoolWeeks.serializer(), weeks)
            val t = today()
            val key = roomsKey(weeks, t) ?: return@async
            if (Cache.get(key, WEEK_SER) == null) keep(key, WEEK_SER) {
                (0..4).map { i -> async { schoolFile(weeks, t.monday().plusDays(i.toLong()))?.let { readSchoolFile(it) } ?: emptyList() } }.awaitAll()
            }
        },
    ).awaitAll()
}
