package io.github.oondraa.opensisakhk.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Line icons on a 24×24 grid, the same drawings as src/web/icons.tsx (circles and rects written out as paths).
// Icon() tints them with the content colour.

private fun line(name: String, vararg paths: String, width: Float = 2f) =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (d in paths) {
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

object Icons {
    val Calendar = line("calendar", rect(3.5f, 5f, 17f, 15.5f, 3.5f), "M3.5 10h17M8 3v4M16 3v4")
    val CalendarOff = line("calendar-off", rect(3.5f, 5f, 17f, 15.5f, 3.5f), "M3.5 10h17M8 3v4M16 3v4M10 13.5l4 4M14 13.5l-4 4")
    val Sun = line("sun", circle(12f, 12f, 4f), "M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4")
    val Utensils = line("utensils", "M4 3v6a3 3 0 0 0 3 3v9M10 3v6a3 3 0 0 1-3 3M7 3v5M20 15V3a5 5 0 0 0-4 5v5a2 2 0 0 0 2 2h2zm0 0v6")
    val ChevronLeft = line("chevron-left", "M15 5l-7 7 7 7", width = 2.4f)
    val ChevronRight = line("chevron-right", "M9 5l7 7-7 7", width = 2.4f)
    val ChevronDown = line("chevron-down", "M5 9l7 7 7-7", width = 2.6f)
    val User = line("user", circle(12f, 8f, 4f), "M4.5 20.5a7.5 7.5 0 0 1 15 0")
    val Lock = line("lock", rect(4.5f, 10.5f, 15f, 10f, 2.5f), "M8 10.5V8a4 4 0 0 1 8 0v2.5")
    val Eye = line("eye", "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z", circle(12f, 12f, 3f))
    val EyeOff = line("eye-off", "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z", circle(12f, 12f, 3f), "M4 20L20 4")
    val Logout = line("logout", "M14 4.5h3.5a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H14M10 16.5L5.5 12 10 7.5M5.5 12H15")
    val Today = line("today", rect(3.5f, 5f, 17f, 15.5f, 3.5f), "M3.5 10h17M8 3v4M16 3v4", circle(12f, 15f, 1.6f))
    val Dumbbell = line("dumbbell", rect(4.5f, 7f, 3f, 10f, 1.2f), rect(16.5f, 7f, 3f, 10f, 1.2f), "M7.5 12h9M2.5 10v4M21.5 10v4")
    val Door = line("door", "M4 21h16M6.5 21V4.5A1.5 1.5 0 0 1 8 3h8a1.5 1.5 0 0 1 1.5 1.5V21", "M14.5 12.5h.01")
    val Teacher = line("teacher", circle(9f, 7.5f, 3.5f), "M2.5 20.5a6.5 6.5 0 0 1 13 0M14 4.5h7.5v9H17")
    val Search = line("search", circle(10.5f, 10.5f, 6.5f), "m15.5 15.5 5 5")
    val Grades = line("grades", "M5 20v-7M12 20V5M19 20v-10")
    val Gear = line(
        "gear",
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
        circle(12f, 12f, 3f),
    )
    val Project = line("project", "M9 18h6M10 21.5h4", "M12 2.5a6.5 6.5 0 0 0-3.9 11.7c.6.5.9 1.1.9 1.8v.5h6V16c0-.7.3-1.3.9-1.8A6.5 6.5 0 0 0 12 2.5z")
    val Wallet = line("wallet", "M19 7.5V6a2 2 0 0 0-2-2H5.5A2.5 2.5 0 0 0 3 6.5v11A2.5 2.5 0 0 0 5.5 20H19a2 2 0 0 0 2-2v-8.5a2 2 0 0 0-2-2H5.5A2.5 2.5 0 0 1 3 6.5", "M16.5 14h.01")
    val Absence = line("absence", circle(12f, 12f, 8.5f), "M12 7.5V12l3 2", "M4.5 4.5l15 15")
    val Calculator = line("calculator", rect(5f, 2.5f, 14f, 19f, 3f), "M8.5 6.5h7v3h-7zM8.5 13.5h.01M12 13.5h.01M15.5 13.5h.01M8.5 17.5h.01M12 17.5h.01M15.5 17.5h.01")
    val Target = line("target", circle(12f, 12f, 8.5f), circle(12f, 12f, 4.5f), circle(12f, 12f, 0.8f))
    val Bug = line("bug", rect(7f, 7.5f, 10f, 13f, 5f), "M9 7.5a3 3 0 0 1 6 0M12 11v9.5M3.5 13H7M17 13h3.5M4.5 8.5L7.5 10M19.5 8.5L16.5 10M4.5 19l3-2M19.5 19l-3-2")
    val Coffee = line("coffee", "M4.5 9h12v5.5a5 5 0 0 1-5 5h-2a5 5 0 0 1-5-5z", "M16.5 10.5h1.5a2.5 2.5 0 0 1 0 5h-1.6M8.5 3.5v2.5M12.5 3.5v2.5")
    val Book = line("book", "M12 6.5C10.5 5 8 4.5 4 4.5v14c4 0 6.5.5 8 2 1.5-1.5 4-2 8-2v-14c-4 0-6.5.5-8 2zM12 6.5v14")
    val Alert = line("alert", "M10.3 3.9 2.6 17.5A2 2 0 0 0 4.3 20.5h15.4a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0zM12 9v4.5M12 17h.01")
    val Bell = line("bell", "M6 9.5a6 6 0 0 1 12 0c0 6 2.5 7.5 2.5 7.5h-17S6 15.5 6 9.5zM10 20.5a2.2 2.2 0 0 0 4 0")
    val Check = line("check", "M5 12.5l4.5 4.5L19 7.5", width = 2.4f)
    val Close = line("close", "M6 6l12 12M18 6L6 18", width = 2.2f)
    val External = line("external", "M14 4.5h5.5V10M19.5 4.5L11 13M17.5 14v4a2 2 0 0 1-2 2h-9.5a2 2 0 0 1-2-2V8.5a2 2 0 0 1 2-2H10")
    val Palette = line("palette", "M12 3.5a8.5 8.5 0 0 0 0 17c1.4 0 2-1 1.6-2.2-.4-1.1.3-2.3 1.5-2.3H17a3.5 3.5 0 0 0 3.5-3.5c0-5-3.8-9-8.5-9z", circle(7.5f, 11.5f, 1f), circle(10f, 7.5f, 1f), circle(14.5f, 7.5f, 1f))
}
