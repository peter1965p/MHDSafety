package com.paeffgen.mhdsafety.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.time.LocalDate

class DateImageTest {
    /** Sensorbild 4×2, Pixelwert = Index, mit Zeilen-Padding (rowStride 6). */
    private fun sensor(): ByteBuffer {
        val bytes = ByteArray(6 * 2)
        for (y in 0 until 2) for (x in 0 until 4) bytes[y * 6 + x] = (y * 4 + x).toByte()
        return ByteBuffer.wrap(bytes)
    }

    private fun crop(rotation: Int): List<Int> {
        val uw = if (rotation % 180 == 0) 4 else 2
        val uh = if (rotation % 180 == 0) 2 else 4
        return DateImage.uprightCrop(sensor(), 6, 1, 4, 2, rotation, Box(0, 0, uw, uh)).pixels.toList()
    }

    @Test
    fun cropRotatesUpright() {
        // Sensor:  0 1 2 3
        //          4 5 6 7
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), crop(0))
        // 90° im Uhrzeigersinn: linke Spalte von unten nach oben wird oberste Zeile.
        assertEquals(listOf(4, 0, 5, 1, 6, 2, 7, 3), crop(90))
        assertEquals(listOf(7, 6, 5, 4, 3, 2, 1, 0), crop(180))
        assertEquals(listOf(3, 7, 2, 6, 1, 5, 0, 4), crop(270))
    }

    @Test
    fun frameMapsIntoImage() {
        // Sensor 1920×1080, hochkant gehalten → aufrecht 1080×1920; Vorschau 1080×2340 schneidet oben/unten ab.
        val frame = Box(81, 1006, 999, 1334)
        val box = DateImage.uprightBox(1920, 1080, 90, 1080, 2340, frame, extendUp = 0)!!
        // Maßstab 2340/1920 = 1,21875 → Rahmen 918 px breit wird 753 px.
        assertEquals(753, box.width, 1)
        assertEquals(960, (box.top + box.bottom) / 2, 1)
        assertEquals(540, (box.left + box.right) / 2, 1)
        assertNull(DateImage.uprightBox(1920, 1080, 90, 0, 0, frame, 0))
    }

    @Test
    fun extendUpIsCountedAboveFrame() {
        val frame = Box(81, 1006, 999, 1334)
        val box = DateImage.uprightBox(1920, 1080, 90, 1080, 2340, frame, extendUp = 328)!!
        val above = DateImage.rowsAboveFrame(box, frame, 328)
        assertEquals(box.height / 2, above, 1)
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) =
        assertTrue("erwartet $expected, war $actual", kotlin.math.abs(expected - actual) <= tolerance)

    /** Hellgrauer Hintergrund mit einem kaum dunkleren Strich – wie blasser Druck. */
    private fun faint(): Gray {
        val w = 60
        val h = 30
        return Gray(w, h, IntArray(w * h) { i -> if (i % w in 28..31 && i / w in 5..24) 185 else 200 })
    }

    @Test
    fun faintPrintBecomesBlack() {
        val out = DateImage.normalize(faint(), darkText = true)
        assertTrue("Strich ${out[29, 15]}", out[29, 15] < 60)
        assertTrue("Grund ${out[5, 15]}", out[5, 15] > 230)
    }

    @Test
    fun invertedHandlesLightText() {
        val w = 60
        val h = 30
        val light = Gray(w, h, IntArray(w * h) { i -> if (i % w in 28..31 && i / w in 5..24) 120 else 60 })
        val out = DateImage.normalize(light, darkText = false)
        assertTrue(out[29, 15] < 60)
        assertTrue(out[5, 15] > 230)
    }

    @Test
    fun dotsGetJoined() {
        // Punktreihe: jeder dritte Pixel schwarz
        val w = 10
        val gray = Gray(w, 1, IntArray(w) { if (it % 3 == 0) 0 else 255 })
        val joined = DateImage.joinDots(gray, radius = 1)
        assertTrue(joined.pixels.all { it == 0 })
    }

    @Test
    fun votesNeedRepeatsButTolerateGaps() {
        val votes = DateVotes()
        val date = ParsedDate(LocalDate.of(2027, 3, 12), useBy = false)
        val wrong = ParsedDate(LocalDate.of(2027, 8, 12), useBy = false)
        assertNull(votes.add(date))
        assertNull(votes.add(null))
        assertNull(votes.add(wrong))
        assertEquals(date.date, votes.add(date)?.date)
    }

    @Test
    fun votesTieIsUndecided() {
        val votes = DateVotes()
        val a = ParsedDate(LocalDate.of(2027, 3, 12), useBy = false)
        val b = ParsedDate(LocalDate.of(2027, 8, 12), useBy = true)
        votes.add(a); votes.add(b); votes.add(b)
        assertEquals(b, votes.add(null))
        votes.clear()
        votes.add(a); votes.add(b); votes.add(a)
        assertNull(votes.add(b))
    }
}
