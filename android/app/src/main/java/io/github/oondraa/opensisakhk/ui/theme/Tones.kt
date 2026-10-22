package io.github.oondraa.opensisakhk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The web app's tone colours (styles.css --green, --teal, --orange, --red), each a touch darker in light mode
// so text in them stays readable on white.

object Tones {
    val green @Composable get() = if (isSystemInDarkTheme()) Color(0xFF30D158) else Color(0xFF248A3D)
    val teal @Composable get() = if (isSystemInDarkTheme()) Color(0xFF40C8E0) else Color(0xFF0E7C93)
    val orange @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF9F0A) else Color(0xFFC25E00)
    val red @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFF453A) else Color(0xFFD70015)
    val neutral @Composable get() = if (isSystemInDarkTheme()) Color(0xFF8E8E93) else Color(0xFF6C6C70)
}

/** Grade colour: under 1.75 great, under 2.75 good, under 3.75 meh, else bad (src/web/Grades.tsx tone). */
@Composable
fun gradeTone(n: Double?): Color = when {
    n == null -> Tones.neutral
    n < 1.75 -> Tones.green
    n < 2.75 -> Tones.teal
    n < 3.75 -> Tones.orange
    else -> Tones.red
}

/** Each part's own tint, as on the web home screen and in the Více list. */
object Tints {
    val timetable = Color(0xFFFF5A1F)
    val lunch = Color(0xFFFF375F)
    val gym = Color(0xFF34C759)
    val rooms = Color(0xFFAF52DE)
    val teachers = Color(0xFF30B0C7)
    val grades = Color(0xFF5856D6)
    val absence = Color(0xFFFF9500)
    val projects = Color(0xFF30B0C7)
    val finance = Color(0xFF0A84FF)
    val settings = Color(0xFF8E8E93)
}
