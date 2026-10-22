package io.github.oondraa.opensisakhk.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.KSerializer

/** Last successful responses, so the app opens instantly and works offline (the web app's `cache`). */
object Cache {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("cache", Context.MODE_PRIVATE)
    }

    fun <T> get(key: String, ser: KSerializer<T>): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() }

    fun <T> set(key: String, ser: KSerializer<T>, value: T) {
        prefs.edit().putString(key, json.encodeToString(ser, value)).apply()
    }

    fun remove(key: String) = prefs.edit().remove(key).apply()

    fun clear() = prefs.edit().clear().apply()
}

/** Device preferences (home order, theme, grade targets): they outlive the account, a logout keeps them. */
object Prefs {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    }

    fun <T> get(key: String, ser: KSerializer<T>): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() }

    fun <T> set(key: String, ser: KSerializer<T>, value: T) {
        prefs.edit().putString(key, json.encodeToString(ser, value)).apply()
    }
}
