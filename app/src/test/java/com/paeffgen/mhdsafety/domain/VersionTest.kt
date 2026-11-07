package com.paeffgen.mhdsafety.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test
    fun parsesCommonForms() {
        assertEquals(Version(1, 2, 3), Version.parse("1.2.3"))
        assertEquals(Version(1, 2, 3), Version.parse("v1.2.3"))
        assertEquals(Version(1, 2, 0), Version.parse("1.2"))
        assertEquals(Version(1, 0, 0), Version.parse("1.0.0-debug"))
        assertNull(Version.parse("latest"))
    }

    @Test
    fun comparesNumericallyNotAlphabetically() {
        assertTrue(Version.parse("1.10.0")!! > Version.parse("1.9.2")!!)
        assertTrue(Version.parse("2.0.0")!! > Version.parse("1.99.99")!!)
        assertTrue(Version.parse("1.0.1")!! > Version.parse("1.0.0")!!)
        assertEquals(0, Version.parse("v1.0.0")!!.compareTo(Version.parse("1.0")!!))
    }
}
