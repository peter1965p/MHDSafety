package com.paeffgen.mhdsafety.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PricesTest {
    private val today = LocalDate.of(2026, 11, 9)

    private fun item(price: Double, date: String, store: String?, country: String? = "DE", currency: String = "EUR", discounted: Boolean = false) =
        """{"price": $price, "currency": "$currency", "date": "$date", "price_is_discounted": $discounted,
           "location": {"osm_brand": ${store?.let { "\"$it\"" } ?: "null"}, "osm_name": "Filiale",
                        "osm_address_country_code": ${country?.let { "\"$it\"" } ?: "null"}}}"""

    private fun json(vararg items: String) = """{"items": [${items.joinToString(",")}], "total": ${items.size}}"""

    @Test
    fun onlyEuroFromGermany() {
        val sightings = Prices.parse(
            json(
                item(7.99, "2026-10-14", "Lidl", country = "PL", currency = "PLN"),
                item(2.29, "2026-04-18", "Lidl"),
                item(1.99, "2026-04-01", "Spar", country = "AT"),
            ),
        )
        assertEquals(listOf(2.29), sightings.map { it.price })
        assertEquals("Lidl", sightings.single().store)
    }

    @Test
    fun brandBeforeBranchName() {
        val s = Prices.parse(json(item(0.39, "2026-10-23", null))).single()
        assertEquals("Filiale", s.store)
    }

    @Test
    fun latestAndCheaper() {
        val hint = Prices.hint(
            Prices.parse(
                json(
                    item(0.39, "2026-10-23", "Netto"),
                    item(0.33, "2026-03-16", "Netto", discounted = true),
                    item(0.25, "2024-03-15", "REWE"), // zu alt
                ),
            ),
            today,
        )!!
        assertEquals(0.39, hint.latest.price, 1e-9)
        assertEquals(0.33, hint.cheapest!!.price, 1e-9)
        assertEquals(2, hint.count)
        assertEquals("0,33 € bei Netto (Angebot) am 16.03.", Prices.describe(hint.cheapest!!, today))
    }

    @Test
    fun noCheapestWhenLatestIsCheapest() {
        val hint = Prices.hint(Prices.parse(json(item(1.19, "2026-05-27", "ALDI Süd"), item(1.39, "2026-04-18", "ALDI Süd"))), today)!!
        assertNull(hint.cheapest)
        assertEquals("1,19 € bei ALDI Süd am 27.05.", Prices.describe(hint.latest, today))
    }

    @Test
    fun olderYearShowsFullDate() {
        val s = PriceSighting(1.99, LocalDate.of(2025, 12, 1), "Kaufland", false)
        assertEquals("1,99 € bei Kaufland am 01.12.2025", Prices.describe(s, today))
    }

    @Test
    fun nothingRecent() {
        assertNull(Prices.hint(Prices.parse(json(item(2.99, "2024-09-30", "ALDI Süd"))), today))
        assertNull(Prices.hint(emptyList(), today))
    }
}
