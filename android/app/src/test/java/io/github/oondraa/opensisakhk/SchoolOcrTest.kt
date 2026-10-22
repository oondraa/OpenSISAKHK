package io.github.oondraa.opensisakhk

import io.github.oondraa.opensisakhk.school.Cell
import io.github.oondraa.opensisakhk.school.Rgb
import io.github.oondraa.opensisakhk.school.readSchoolDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

// Same cases as test/schoolOcr.test.ts, on the same real images. They aren't committed (they name teachers):
// download them into test/fixtures/ in the repo root to run these, otherwise they're skipped.
class SchoolOcrTest {
    private fun read(name: String): List<Cell> {
        val f = File("../../test/fixtures/$name")
        assumeTrue("fixture $name missing", f.exists())
        val img = ImageIO.read(f)
        val px = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
        return readSchoolDay(Rgb(img.width, img.height, px))
    }

    private fun List<Cell>.at(cls: String, period: Int, slot: Int = 0) = find { it.cls == cls && it.period == period && it.slot == slot }

    @Test
    fun readsGymLessonsAndChanges() {
        val cells = read("PO1-2026-10-12.jpg")
        cells.at("G1", 1)!!.let { assertEquals(listOf("TEV", "MH", "T0", false), listOf(it.subject, it.teacher, it.room, it.changed)) }
        cells.at("T1", 3)!!.let { assertEquals(listOf("TEV", "MH", "P0"), listOf(it.subject, it.teacher, it.room)) }
        cells.at("G4", 5)!!.let { assertEquals(listOf("TEV", "RO", "P0"), listOf(it.subject, it.teacher, it.room)) }
        cells.at("T1", 5, 1)!!.let { assertEquals(listOf("AGJ2", "KT", "403"), listOf(it.subject, it.teacher, it.room)) }
        cells.at("T2", 4)!!.let { assertEquals(listOf("ELZ", "HU", "B13", true), listOf(it.subject, it.teacher, it.room, it.changed)) }
        cells.at("G1", 3)!!.let { assertEquals(listOf("CJL", "206", true), listOf(it.subject, it.room, it.changed)) }
        val gym = cells.filter { it.room == "T0" || it.room == "P0" }
        assertTrue(gym.size > 5)
        assertTrue(gym.all { it.subject.startsWith("TEV") })
    }

    @Test
    fun readsRooms() {
        val tue = read("UT1-2026-10-13.jpg")
        val wed = read("ST1-2026-10-14.jpg")
        assertEquals("317", tue.at("G1", 4)?.room)
        assertEquals("403", tue.at("G2", 9)?.room)
        assertEquals("302", wed.at("I4", 8)?.room)
        assertEquals("B13", wed.at("G3", 3)?.room)
        assertEquals("211", tue.at("G3", 4)?.room)
        assertEquals("211", wed.at("T4", 4)?.room)
        val odd = (tue + wed).map { it.room }.filter { it.isNotEmpty() }.toSet().filterNot { Regex("^(\\d{3}|B1\\d|[PT]0)$").matches(it) }
        assertEquals(emptyList<String>(), odd)
    }
}
