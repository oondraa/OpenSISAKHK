package io.github.oondraa.opensisakhk

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.oondraa.opensisakhk.data.Prefs
import io.github.oondraa.opensisakhk.ui.Icons
import io.github.oondraa.opensisakhk.ui.theme.Tints
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** Every part of the app (src/web/sections.ts). Settings has no place in the custom order. */
enum class Part(val label: String, val icon: ImageVector, val tint: Color) {
    Timetable("Rozvrh", Icons.Calendar, Tints.timetable),
    Lunch("Obědy", Icons.Utensils, Tints.lunch),
    Gym("Posilovna", Icons.Dumbbell, Tints.gym),
    Rooms("Volné učebny", Icons.Door, Tints.rooms),
    Teachers("Učitelé", Icons.Teacher, Tints.teachers),
    Grades("Známky", Icons.Grades, Tints.grades),
    Absence("Absence", Icons.Absence, Tints.absence),
    Projects("Projekty", Icons.Project, Tints.projects),
    Finance("Finance", Icons.Wallet, Tints.finance),
    Settings("Nastavení", Icons.Gear, Tints.settings),
}

/** Home: the timetable is always the big card on top, these follow (the first four as widgets). */
val HOME_DEFAULT = listOf(Part.Grades, Part.Absence, Part.Lunch, Part.Rooms, Part.Gym, Part.Projects, Part.Teachers, Part.Finance)

/** The saved order, repaired: unknown parts dropped, parts added in a later version appended. */
fun homeOrder(): List<Part> {
    val saved = Prefs.get("homeOrder", ListSerializer(String.serializer())).orEmpty().mapNotNull { n -> Part.entries.find { it.name == n } }
    return (saved + HOME_DEFAULT).distinct().filter { it in HOME_DEFAULT }
}

fun saveHomeOrder(order: List<Part>) = Prefs.set("homeOrder", ListSerializer(String.serializer()), order.map { it.name })
