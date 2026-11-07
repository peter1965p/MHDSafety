package com.paeffgen.mhdsafety.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ManualDateTest {
    private val today = LocalDate.of(2026, 11, 7)
    private val nov9 = LocalDate.of(2026, 11, 9)

    @Test
    fun fullAndShortForms() {
        assertEquals(nov9, ManualDate.parse("09.11.2026", today))
        assertEquals(nov9, ManualDate.parse("9.11.26", today))
        assertEquals(nov9, ManualDate.parse("9.11.", today))
        assertEquals(nov9, ManualDate.parse("9.11", today))
        assertEquals(nov9, ManualDate.parse("0911", today))
        assertEquals(nov9, ManualDate.parse("091126", today))
        assertEquals(nov9, ManualDate.parse("09112026", today))
    }

    @Test
    fun withoutYear_rollsOverToNextYear() {
        assertEquals(LocalDate.of(2027, 2, 15), ManualDate.parse("15.2.", today))
        // Kürzlich abgelaufen bleibt dieses Jahr
        assertEquals(LocalDate.of(2026, 11, 1), ManualDate.parse("1.11.", today))
    }

    @Test
    fun invalid() {
        assertNull(ManualDate.parse("", today))
        assertNull(ManualDate.parse("31.02.2027", today))
        assertNull(ManualDate.parse("091", today))
        assertNull(ManualDate.parse("abc", today))
    }
}
