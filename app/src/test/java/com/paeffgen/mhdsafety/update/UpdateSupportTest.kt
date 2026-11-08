package com.paeffgen.mhdsafety.update

import com.paeffgen.mhdsafety.domain.Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSupportTest {
    @Test
    fun statusCodes() {
        assertEquals(UpdateProblem.ServerError(503), UpdateSupport.problemForStatus(503))
        assertEquals(UpdateProblem.ServerError(504), UpdateSupport.problemForStatus(504))
        assertEquals(UpdateProblem.RateLimited, UpdateSupport.problemForStatus(403))
        assertEquals(UpdateProblem.RateLimited, UpdateSupport.problemForStatus(429))
        assertTrue(UpdateSupport.problemForStatus(401) is UpdateProblem.Other)
    }

    @Test
    fun serverErrorIsNotCalledNoInternet() {
        val message = UpdateSupport.message(UpdateProblem.ServerError(503))
        assertTrue(message.contains("gestört"))
        assertTrue(!message.contains("Internet"))
        assertEquals("Keine Internetverbindung.", UpdateSupport.message(UpdateProblem.NoInternet))
    }

    @Test
    fun versionFromRedirect() {
        assertEquals(
            Version(1, 3, 0),
            UpdateSupport.versionFromReleaseLocation("https://github.com/peter1965p/MHDSafety/releases/tag/v1.3.0"),
        )
        assertNull(UpdateSupport.versionFromReleaseLocation("https://github.com/peter1965p/MHDSafety/releases"))
        assertNull(UpdateSupport.versionFromReleaseLocation(null))
    }

    @Test
    fun apkUrlMatchesReleaseScript() {
        assertEquals(
            "https://github.com/peter1965p/MHDSafety/releases/download/v1.4.0/MHDSafety-1.4.0.apk",
            UpdateSupport.apkUrl("peter1965p/MHDSafety", Version(1, 4, 0)),
        )
    }
}
