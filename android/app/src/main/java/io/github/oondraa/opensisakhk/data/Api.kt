package io.github.oondraa.opensisakhk.data

import android.content.Context
import android.content.SharedPreferences
import io.github.oondraa.opensisakhk.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** 401 from the worker: wrong password on login, or the session is gone (the app goes back to the login). */
class Unauthorized(code: String) : Exception(code)

/** Any other failed call; `code` is the worker's error ("rate_limited", "upstream", …) or "HTTP 500". */
class ApiError(val code: String, val status: Int) : Exception(code)

val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * The same worker API as the web app. The session is the worker's sealed `osis` cookie, kept in app-private storage
 * exactly as a browser keeps it: the app never sees the password after login.
 */
object Api {
    private val base = BuildConfig.API_BASE.toHttpUrl()
    private lateinit var prefs: SharedPreferences
    private lateinit var client: OkHttpClient
    private val cookies = mutableListOf<Cookie>()

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn

    /** Bumped on every login: screens key their state by it, so one student's data never shows to the next. */
    var generation = 0
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        prefs.getStringSet("cookies", emptySet())!!.mapNotNullTo(cookies) { Cookie.parse(base, it) }
        client = OkHttpClient.Builder()
            .cookieJar(Jar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .build()
        _signedIn.value = hasSession()
    }

    private fun hasSession() = synchronized(cookies) { cookies.any { it.name == "osis" && it.expiresAt > System.currentTimeMillis() } }

    private object Jar : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(Api.cookies) {
                for (c in cookies) {
                    Api.cookies.removeAll { it.name == c.name && it.path == c.path && it.domain == c.domain }
                    if (c.expiresAt > System.currentTimeMillis()) Api.cookies += c
                }
                prefs.edit().putStringSet("cookies", Api.cookies.map { it.toString() }.toSet()).apply()
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            return synchronized(cookies) { cookies.filter { it.expiresAt > now && it.matches(url) } }
        }
    }

    private suspend fun <T> call(path: String, ser: KSerializer<T>, body: String? = null): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE}/api$path")
            .apply { if (body != null) post(body.toRequestBody("application/json".toMediaType())) }
            .build()
        client.newCall(request).execute().use { res ->
            val text = res.body?.string().orEmpty()
            val err = runCatching { json.decodeFromString<ErrorBody>(text) }.getOrNull()
            if (res.code == 401) {
                if (path != "/login") signedOut()
                throw Unauthorized(err?.error ?: "unauthorized")
            }
            if (!res.isSuccessful) throw ApiError(err?.message ?: err?.error ?: "HTTP ${res.code}", res.code)
            json.decodeFromString(ser, text)
        }
    }

    private suspend inline fun <reified T> get(path: String): T = call(path, serializer())

    suspend fun login(username: String, password: String) {
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
        }
        call("/login", serializer<Ok>(), body.toString())
        generation++
        _signedIn.value = hasSession()
    }

    suspend fun logout() {
        runCatching { call("/logout", serializer<Ok>(), "{}") }
        signedOut()
    }

    /** Forgets the session and everything cached for it. */
    fun signedOut() {
        synchronized(cookies) {
            cookies.clear()
            prefs.edit().remove("cookies").apply()
        }
        Cache.clear()
        _signedIn.value = false
    }

    private suspend fun post(path: String, body: kotlinx.serialization.json.JsonObject) {
        call(path, serializer<Ok>(), body.toString())
    }

    suspend fun timetable(monday: String): TimetableResponse = get("/timetable?week=$monday")

    /** Permanent timetable (no substitutions or cancellations) of the week starting `monday`. */
    suspend fun permanentTimetable(monday: String): TimetableResponse = get("/timetable?week=$monday&static=1")
    suspend fun lunch(): LunchResponse = get("/lunch")
    suspend fun me(): Me = get("/me")
    suspend fun grades(): GradesResponse = get("/grades")
    suspend fun absence(): Absence = get("/absence")
    suspend fun canteen(): CanteenResponse = get("/canteen")
    suspend fun finance(): Finance = get("/finance")
    suspend fun projects(election: String? = null): ProjectsResponse = get("/projects${if (election != null) "?election=$election" else ""}")
    suspend fun schoolWeeks(): SchoolWeeks = get("/school/weeks")

    /** Orders `veta` for the day (switching from the ordered meal), or cancels the day with null. Real money. */
    suspend fun order(date: String, veta: String?): CanteenResponse =
        call("/canteen/order", serializer(), buildJsonObject { put("date", date); put("veta", veta) }.toString())

    suspend fun setClass(cls: String) = post("/me/class", buildJsonObject { put("cls", cls) })
    suspend fun stravaConnect(jmeno: String, heslo: String) = post("/strava", buildJsonObject { put("jmeno", jmeno); put("heslo", heslo) })
    suspend fun stravaDisconnect() = post("/strava/disconnect", buildJsonObject {})
    suspend fun watch(on: Boolean) = post("/watch", buildJsonObject { put("on", on) })

    /** Bug report or idea, sent to the developer's Telegram. */
    suspend fun report(kind: String, text: String, meta: Map<String, String>) = post(
        "/report",
        buildJsonObject {
            put("kind", kind)
            put("text", text)
            put("meta", buildJsonObject { meta.forEach { (k, v) -> put(k, v) } })
        },
    )

    /** The student's SIS profile photo, null when there's none. */
    suspend fun photo(): ByteArray? = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url("${BuildConfig.API_BASE}/api/me/photo").build()).execute().use { res ->
            if (res.isSuccessful) res.body?.bytes() else null
        }
    }

    /** One school-wide timetable image ("ST1"), read on the phone like the web app does. */
    suspend fun schoolImage(file: String): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url("${BuildConfig.API_BASE}/api/school/$file.jpg").build()).execute().use { res ->
            if (!res.isSuccessful) throw ApiError("HTTP ${res.code}", res.code)
            res.body!!.bytes()
        }
    }

    @kotlinx.serialization.Serializable
    private class Ok
}
