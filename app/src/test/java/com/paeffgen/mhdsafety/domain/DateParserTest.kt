package com.paeffgen.mhdsafety.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DateParserTest {
    private val today = LocalDate.of(2026, 11, 7)

    private fun parse(text: String) = DateParser.parse(text, today)?.date

    @Test
    fun fullDates() {
        assertEquals(LocalDate.of(2027, 3, 12), parse("12.03.2027"))
        assertEquals(LocalDate.of(2027, 3, 12), parse("12.03.27"))
        assertEquals(LocalDate.of(2027, 3, 12), parse("12/03/2027"))
        assertEquals(LocalDate.of(2027, 3, 12), parse("12-03-27"))
        assertEquals(LocalDate.of(2027, 3, 12), parse("12 03 2027"))
        assertEquals(LocalDate.of(2026, 11, 9), parse("9.11.26"))
    }

    @Test
    fun withSurroundingText() {
        assertEquals(LocalDate.of(2027, 1, 5), parse("Mindestens haltbar bis: 05.01.2027 L2345"))
    }

    @Test
    fun ocrConfusions() {
        assertEquals(LocalDate.of(2027, 1, 10), parse("1O.O1.2O27"))
        assertEquals(LocalDate.of(2027, 1, 11), parse("l1.01.27"))
    }

    @Test
    fun monthOnly_meansEndOfMonth() {
        assertEquals(LocalDate.of(2027, 3, 31), parse("MHD 03/2027"))
        assertEquals(LocalDate.of(2028, 2, 29), parse("02.28"))
    }

    @Test
    fun namedMonths() {
        assertEquals(LocalDate.of(2027, 3, 12), parse("12 MÄR 2027"))
        assertEquals(LocalDate.of(2027, 5, 31), parse("BEST BEFORE MAY 27"))
        assertEquals(LocalDate.of(2027, 10, 3), parse("03 OKT 27"))
    }

    @Test
    fun prefersDayPreciseOverMonthOnly() {
        assertEquals(LocalDate.of(2027, 2, 14), parse("03/2027 14.02.2027"))
    }

    @Test
    fun keyword_picksDateAfterIt() {
        // Herstellungsdatum vorne, MHD danach
        assertEquals(LocalDate.of(2026, 12, 1), parse("hergestellt 01.11.26 MHD 01.12.26"))
    }

    @Test
    fun noKeyword_picksLatest() {
        assertEquals(LocalDate.of(2027, 6, 1), parse("01.11.26 01.06.27"))
    }

    @Test
    fun rejectsImplausibleAndInvalid() {
        assertNull(parse("31.02.2027"))
        assertNull(parse("12.03.1999"))
        assertNull(parse("Charge 4012345678"))
        assertNull(parse("Fett 3.5 %"))
    }

    @Test
    fun detectsUseBy() {
        assertTrue(DateParser.parse("zu verbrauchen bis 10.11.26", today)!!.useBy)
        assertFalse(DateParser.parse("mindestens haltbar bis 10.11.26", today)!!.useBy)
    }
}
