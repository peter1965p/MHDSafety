package com.paeffgen.mhdsafety.data

import com.paeffgen.mhdsafety.domain.QuantityUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SyncCodecTest {

    @Test
    fun `neuester Stand gewinnt`() {
        // nichts lokal -> Server übernehmen
        assertTrue(SyncCodec.remoteWins(null, false, null, 1000))
        // lokal älter -> Server gewinnt, auch wenn lokal noch nicht hochgeladen
        assertTrue(SyncCodec.remoteWins(500, true, null, 1000))
        // lokal neuer und noch nicht hochgeladen -> lokal bleibt
        assertFalse(SyncCodec.remoteWins(2000, true, null, 1000))
        // lokal neuer, aber schon hochgeladen -> Server-Stand ist maßgeblich
        assertTrue(SyncCodec.remoteWins(2000, false, null, 1000))
        // lokal gelöscht (Tombstone neuer) -> Server-Stand holt den Eintrag nicht zurück
        assertFalse(SyncCodec.remoteWins(null, false, 3000, 1000))
        assertTrue(SyncCodec.remoteWins(null, false, 500, 1000))
    }

    @Test
    fun `Artikel hin und zurueck`() {
        val item = FoodItem(
            id = 1234567890123L, name = "Joghurt", date = LocalDate.of(2026, 12, 1), dateType = DateType.USE_BY,
            barcode = "4000000000000", category = Category.DAIRY, ownUseBy = LocalDate.of(2026, 11, 28),
            quantity = 250.0, unit = QuantityUnit.GRAM, updatedAt = 42, dirty = true,
        )
        val back = SyncCodec.foodFromJson(JSONObject(SyncCodec.foodToJson(item).toString()))
        assertEquals(item.copy(dirty = false), back)
    }

    @Test
    fun `Artikel ohne optionale Felder und mit unbekannten Werten`() {
        val json = JSONObject("""{"id":5,"name":"Brot","date":20000,"dateType":"QUATSCH","barcode":null,
            "category":null,"ownUseBy":null,"quantity":null,"unit":"FASS","updatedAt":7,"deleted":false}""")
        val item = SyncCodec.foodFromJson(json)!!
        assertEquals(DateType.BEST_BEFORE, item.dateType)
        assertEquals(Category.OTHER, item.category)
        assertNull(item.quantity)
        assertNull(item.unit)
        assertNull(item.barcode)
    }

    @Test
    fun `Rezept hin und zurueck`() {
        val recipe = Recipe(
            id = 99, url = "https://example.test/r", name = "Pfannkuchen", ingredients = listOf("200 g Mehl", "2 Eier"),
            servings = "4 Portionen", imageUrl = null, createdAt = 123, updatedAt = 456, dirty = true,
        )
        val back = SyncCodec.recipeFromJson(JSONObject(SyncCodec.recipeToJson(recipe).toString()))
        assertEquals(recipe.copy(dirty = false), back)
    }

    @Test
    fun `Tombstone traegt nur id, Zeit und deleted`() {
        val json = SyncCodec.tombstoneToJson(Tombstone(Tombstone.ITEMS, 8, 900))
        assertEquals(8L, json.getLong("id"))
        assertEquals(900L, json.getLong("updatedAt"))
        assertTrue(json.getBoolean("deleted"))
    }

    @Test
    fun `neue ids sind positiv und verschieden`() {
        val ids = (1..1000).map { SyncCodec.newId() }.toSet()
        assertEquals(1000, ids.size)
        assertTrue(ids.all { it > 0 })
    }
}
