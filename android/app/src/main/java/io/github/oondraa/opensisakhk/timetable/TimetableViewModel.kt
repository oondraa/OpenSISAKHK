package io.github.oondraa.opensisakhk.timetable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.oondraa.opensisakhk.data.Api
import io.github.oondraa.opensisakhk.data.Cache
import io.github.oondraa.opensisakhk.data.LunchDay
import io.github.oondraa.opensisakhk.data.LunchResponse
import io.github.oondraa.opensisakhk.data.TimetableResponse
import io.github.oondraa.opensisakhk.data.Unauthorized
import io.github.oondraa.opensisakhk.data.monday
import io.github.oondraa.opensisakhk.data.today
import kotlinx.coroutines.launch
import io.github.oondraa.opensisakhk.App
import io.github.oondraa.opensisakhk.widget.Widgets
import java.time.LocalDate

/** Weeks of the timetable by their Monday: the cached copy at once, then a fresh one from SIS. */
class TimetableViewModel : ViewModel() {
    private val weeks = mutableStateMapOf<LocalDate, TimetableResponse>()
    private val loading = mutableStateMapOf<LocalDate, Boolean>()
    private val failed = mutableStateMapOf<LocalDate, Boolean>()
    private val fetched = mutableSetOf<LocalDate>()

    /** Canteen orders (Strava), when connected. A failure just leaves the plain lunch row. */
    var lunch by mutableStateOf(Cache.get("lunch", LunchResponse.serializer()))
        private set

    var selected by mutableStateOf(startDay(Cache.get(key(today().monday()), TimetableResponse.serializer())?.days))
        private set
    private var touched = false

    init {
        ensure(selected.monday())
        viewModelScope.launch {
            runCatching { Api.lunch() }.onSuccess {
                lunch = it
                Cache.set("lunch", LunchResponse.serializer(), it)
            }
        }
    }

    private fun key(monday: LocalDate) = "timetable:$monday"

    fun select(day: LocalDate) {
        touched = true
        selected = day
    }

    fun week(monday: LocalDate): TimetableResponse? = weeks[monday]

    /** Called when a week comes into view: its cached copy now, a fresh one once per app run. */
    fun ensure(monday: LocalDate) {
        if (monday !in weeks) Cache.get(key(monday), TimetableResponse.serializer())?.let { weeks[monday] = it }
        if (fetched.add(monday)) refresh(monday)
    }

    fun isLoading(monday: LocalDate) = loading[monday] == true
    fun hasFailed(monday: LocalDate) = failed[monday] == true

    fun refresh(monday: LocalDate) {
        if (loading[monday] == true) return
        loading[monday] = true
        viewModelScope.launch {
            try {
                val fresh = Api.timetable(monday.toString())
                weeks[monday] = fresh
                failed[monday] = false
                Cache.set(key(monday), TimetableResponse.serializer(), fresh)
                App.scope.launch { Widgets.update(App.instance) }
                // Without a cached timetable the first guess is today; correct it once this week's lessons arrive.
                if (!touched && monday == today().monday()) selected = startDay(fresh.days)
            } catch (e: Unauthorized) {
                // Api has signed out; the app shows the login.
            } catch (e: Exception) {
                failed[monday] = true
            } finally {
                loading[monday] = false
            }
        }
    }

    /** Strava lists days from today ~3 weeks ahead; outside that range there's nothing to say. */
    fun lunchFor(date: String): LunchDay? {
        val ds = lunch?.takeIf { it.connected }?.days.orEmpty()
        if (ds.isEmpty() || date < ds.first().date || date > ds.last().date) return null
        return ds.find { it.date == date } ?: LunchDay(date, null)
    }
}
