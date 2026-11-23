package com.paeffgen.mhdsafety.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoLinkTest {
    @Test
    fun erkenntAlleYoutubeFormen() {
        val id = "dQw4w9WgXcQ"
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://m.youtube.com/watch?feature=share&v=$id",
            "https://youtu.be/$id?si=abc",
            "https://youtube.com/shorts/$id?feature=share",
            "Schau mal: https://youtube.com/shorts/$id lecker!",
        ).forEach { assertEquals(it, id, VideoLink.youtubeId(it)) }
        assertEquals("https://www.youtube.com/watch?v=$id", VideoLink.canonical("https://youtu.be/$id"))
    }

    @Test
    fun quellen() {
        assertEquals("youtube", VideoLink.source("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("instagram", VideoLink.source("https://www.instagram.com/reel/abc/"))
        assertEquals("tiktok", VideoLink.source("https://vm.tiktok.com/xyz/"))
        assertNull(VideoLink.source("https://www.chefkoch.de/rezepte/123"))
    }

    @Test
    fun zutatenAusBeschreibungMitKopf() {
        val text = """
            Mein bestes Rezept!
            Zutaten:
            - 200 g Mehl
            - 2 Eier
            • 1 Prise Salz
            Zubereitung:
            1. Alles verrühren
        """.trimIndent()
        assertEquals(listOf("200 g Mehl", "2 Eier", "1 Prise Salz"), VideoLink.ingredientsFromDescription(text))
    }

    @Test
    fun zutatenOhneKopfNurMitMengeUndEinheit() {
        val text = "Abonniert den Kanal\n250 ml Milch\n3 EL Zucker\nInstagram: https://x"
        assertEquals(listOf("250 ml Milch", "3 EL Zucker"), VideoLink.ingredientsFromDescription(text))
        assertTrue(VideoLink.ingredientsFromDescription("Einfach nur ein Video").isEmpty())
    }
}
