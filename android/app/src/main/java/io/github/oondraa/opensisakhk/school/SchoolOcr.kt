package io.github.oondraa.opensisakhk.school

// Generated from src/shared/schoolOcr.ts (font table, grid and class order copied as-is); the reading logic is a
// line-by-line port. Reads the school-wide timetable JPGs (/TimeTable/{PO..PA}{1,2}.jpg): the grid sits at fixed
// pixel positions and the text is a bitmap font, so a tiny template matcher beats a real OCR.

import kotlinx.serialization.Serializable

@Serializable
data class Cell(
    /** Class row label, e.g. "T3". */
    val cls: String,
    val period: Int,
    /** Sub-column 0–2 (lesson groups). */
    val slot: Int,
    val subject: String,
    val teacher: String,
    val room: String,
    /** Pink background = changed/substituted. */
    val changed: Boolean,
)

// Left edges of the 36 sub-columns (12 periods × 3) plus the right edge.
private val COLS = intArrayOf(39,70,100,129,160,190,219,250,280,309,340,370,399,430,460,489,520,550,579,610,640,669,700,730,759,790,820,849,880,910,939,970,1000,1029,1060,1090,1119)
private const val ROW_TOP = 100
private const val ROW_HEIGHT = 44
private val LINE_OFFSETS = intArrayOf(4, 16, 28)
private const val GLYPH_H = 9
private const val DARK = 130

/** Row order printed on every image so far; checked against the image size below. */
val CLASSES = listOf("G1", "T1", "G2", "T2", "I2", "G3", "T3", "I3", "G4", "T4", "I4")

private val FONT_SRC = listOf(
    "0" to ".###. #...# #...# #...# #...# #...# #...# #...# .###.",
    "1" to "..#. ###. ..#. ..#. ..#. ..#. ..#. ..#. ####",
    "1" to "..#.. ###.. ..#.. ..#.. ..#.. ..#.. ..#.. ..#.. #####",
    "1" to ".#. ##. .#. .#. .#. .#. .#. .#. ###",
    "2" to "####. ....# ....# ....# ...#. ..#.. .#... #.... #####",
    "2" to "####.. ....#. ....#. ....#. ...#.. ..##.. .#.... #..... ######",
    "3" to "####. ....# ....# ....# ..##. ....# ....# ....# ####.",
    "3" to ".####. .....# .....# .....# ...##. .....# .....# #....# .####.",
    "4" to "....#. ...##. ..#.#. .#..#. #...#. ###### ....#. ....#. ....#.",
    "4" to "...#. ..##. .#.#. #..#. ...#. ##### ...#. ...#. ...#.",
    "5" to "##### #.... #.... ####. ....# ....# ....# ...## ####.",
    "6" to "..##. .#... #.... ####. #...# #...# #...# #...# .###.",
    "6" to "..### .#... #.... ####. #...# #...# #...# #...# .###.",
    "7" to "##### ....# ...#. ...#. ..##. ..#.. .#... .#... #....",
    "8" to ".###. #...# #...# ##..# .###. #...# #...# #...# .###.",
    "9" to ".###. #..## #...# #...# #...# .#### ....# ...#. .##..",
    "A" to "...#... ..###.. ..#.#.. ..#.#.. .#...#. .#...#. .#####. ......# #.....#",
    "A" to "..#... .###.. .#.#.. .#.#.. #...#. #...#. #####. .....# .....#",
    "B" to "####. #...# #...# #...# ####. #...# #...# #...# ####.",
    "C" to "..#### .#...# #..... #..... #..... #..... #..... .#...# ..###.",
    "D" to "####.. #...#. #....# #....# #....# #....# #....# #...#. ####..",
    "E" to "##### #.... #.... #.... ##### #.... #.... #.... #####",
    "E" to "###### #..... #..... #..... #####. #..... #..... #..... ######",
    "F" to "##### #.... #.... #.... ##### #.... #.... #.... #....",
    "G" to "..#### .#.... #..... #..... #..### #....# #....# .#...# ..###.",
    "H" to "#....# #....# #....# #....# ###### #....# #....# #....# #....#",
    "I" to "### .#. .#. .#. .#. .#. .#. .#. ###",
    "J" to ".### ...# ...# ...# ...# ...# ...# ...# ###.",
    "K" to "#...## #..##. #..#.. #.#... ###... #.##.. #..#.. #...#. #...##",
    "L" to "#.... #.... #.... #.... #.... #.... #.... #.... #####",
    "M" to "##...## ##...## ##...## #.#.#.# #.#.#.# #..#..# #..#..# #.....# #.....#",
    "N" to "##...# ##...# #.#..# #.#..# #..#.# #..#.# #...## #...## #....#",
    "O" to "..###.. .#...#. #.....# #.....# #.....# #.....# #.....# .#...#. ..###..",
    "P" to "####. #...# #...# #...# #...# ####. #.... #.... #....",
    "R" to "####.. #...#. #...#. #...#. ####.. #..#.. #...#. #...#. #....#",
    "S" to ".#### #.... #.... #.... .###. ....# ....# ....# ####.",
    "T" to "####### ...#... ...#... ...#... ...#... ...#... ...#... ...#... ...#...",
    "T" to "###### ..#... ..#... ..#... ..#... ..#... ..#... ..#... ..#...",
    "U" to "#....# #....# #....# #....# #....# #....# #....# #....# .####.",
    "V" to "#.....# .#....# .#...#. .#...#. ..#..#. ..#.#.. ..#.#.. ...##.. ...#...",
    "Y" to "#.....# .#...#. .#..#.. ..#.#.. ...#... ...#... ...#... ...#... ...#...",
    "Z" to "##### ....# ...#. ..##. ..#.. .#... ##... #.... #####",
    "Z" to ".##### .....# ....#. ...##. ...#.. ..#... .##... .#.... ######",
    "Z" to "#####. ....#. ...#.. ..##.. ..#... .#.... ##.... #..... ######",
    "p" to "..... ..... ####. #...# #...# #...# #...# #..## ####.",
    "r" to ".... .... #.#. ##.. #... #... #... #... #...",
    "s" to ".... .... .### #... #... .##. ...# ...# ###.",
    "t" to "#... #... #### #... #... #... #... #... .##.",
    "z" to "..... ..... .#### ..... ...#. ..#.. .##.. .#... #####",
    "z" to ".... .... #### ..#. ..#. .#.. ##.. #... ####",
)

private class Glyph(val ch: Char, val w: Int, val bits: BooleanArray)

private val FONT: List<Glyph> = FONT_SRC.map { (ch, src) ->
    val rows = src.split(" ")
    check(rows.size == GLYPH_H) { "glyph $ch has ${rows.size} rows" }
    Glyph(ch[0], rows[0].length, rows.joinToString("").map { it == '#' }.toBooleanArray())
}

/** An image as packed ARGB pixels (Android Bitmap.getPixels, or ImageIO in tests). */
class Rgb(val width: Int, val height: Int, val argb: IntArray) {
    private fun r(i: Int) = (argb[i] shr 16) and 0xFF
    private fun g(i: Int) = (argb[i] shr 8) and 0xFF
    private fun b(i: Int) = argb[i] and 0xFF

    fun dark(x: Int, y: Int): Boolean {
        val i = y * width + x
        return (r(i) + g(i) + b(i)) / 3.0 < DARK
    }

    fun pink(x: Int, y: Int): Boolean {
        val i = y * width + x
        val r = r(i)
        val g = g(i)
        return r > 235 && g < 228 && b(i) < 228 && r - g > 15
    }
}

private fun readLine(bm: Rgb, x0: Int, x1: Int, y0: Int): String {
    val w = x1 - x0
    fun bit(x: Int, y: Int) = x < w && bm.dark(x0 + x, y0 + y)
    fun colDark(x: Int): Boolean {
        for (y in 0 until GLYPH_H) if (bit(x, y)) return true
        return false
    }
    fun cost(g: Glyph, x: Int): Int {
        var miss = 0
        for (y in 0 until GLYPH_H) for (k in 0 until g.w) if (bit(x + k, y) != g.bits[y * g.w + k]) miss++
        // A glyph should be followed by a gap; ink right after it means we cut a wider glyph short.
        if (x + g.w < w && colDark(x + g.w)) miss += 2
        return miss
    }

    val out = StringBuilder()
    var gap = 0
    var x = 0
    while (x < w) {
        if (!colDark(x)) {
            gap++
            x++
            continue
        }
        if (out.isNotEmpty() && gap >= 4) out.append(' ')
        gap = 0
        var best: Glyph? = null
        var bestScore = Double.POSITIVE_INFINITY
        for (g in FONT) {
            if (x + g.w > w + 1) continue
            val score = cost(g, x).toDouble() / (g.w * GLYPH_H) - g.w * 0.002 // tie → prefer wider glyph
            if (score < bestScore) {
                bestScore = score
                best = g
            }
        }
        if (best != null && bestScore < 0.12) {
            out.append(best.ch)
            x += best.w
        } else {
            // Unknown ink (accents, the small font used inside block cells): skip it.
            while (x < w && colDark(x)) x++
        }
    }
    return out.toString().trim()
}

/** Reads every non-empty sub-cell of one day image. */
fun readSchoolDay(img: Rgb): List<Cell> {
    require(img.width == 1242 && img.height >= ROW_TOP + CLASSES.size * ROW_HEIGHT) { "Unexpected timetable image size ${img.width}×${img.height}" }
    val cells = mutableListOf<Cell>()
    CLASSES.forEachIndexed { r, cls ->
        for (c in 0 until 36) {
            val x0 = COLS[c] + 2
            val x1 = COLS[c + 1] - 1
            val top = ROW_TOP + r * ROW_HEIGHT
            val (subject, teacher, rawRoom) = LINE_OFFSETS.map { readLine(img, x0, x1, top + it) }
            if (subject.isEmpty() && teacher.isEmpty() && rawRoom.isEmpty()) continue
            // The narrow "1" leaves a gap wide enough to read as a space ("21 1"); a room number never has one.
            val room = rawRoom.replace(" ", "")
            // SIS highlights only the changed line (e.g. just the room), so check each line separately.
            val changed = LINE_OFFSETS.any { off ->
                var pink = 0
                var total = 0
                for (y in top + off - 1 until top + off + GLYPH_H) for (x in x0 until x1) {
                    total++
                    if (img.pink(x, y)) pink++
                }
                pink.toDouble() / total > 0.3
            }
            cells += Cell(cls, c / 3, c % 3, subject, teacher, room, changed)
        }
    }
    return cells
}
