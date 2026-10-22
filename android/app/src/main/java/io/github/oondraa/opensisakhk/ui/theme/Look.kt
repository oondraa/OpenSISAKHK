package io.github.oondraa.opensisakhk.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.oondraa.opensisakhk.data.Prefs
import kotlinx.serialization.builtins.serializer

/** Light, dark or the system's; wallpaper colours (Material You) or the app's own ember. Set in Nastavení. */
object Look {
    enum class Mode { System, Light, Dark }

    var mode by mutableStateOf(Mode.System)
        private set
    var dynamic by mutableStateOf(true)
        private set

    fun load() {
        mode = Prefs.get("theme", String.serializer())?.let { n -> Mode.entries.find { it.name == n } } ?: Mode.System
        dynamic = Prefs.get("dynamicColor", Boolean.serializer()) ?: true
    }

    fun chooseMode(m: Mode) {
        mode = m
        Prefs.set("theme", String.serializer(), m.name)
    }

    fun chooseDynamic(on: Boolean) {
        dynamic = on
        Prefs.set("dynamicColor", Boolean.serializer(), on)
    }
}
