package com.paeffgen.mhdsafety.domain

import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ein gemeldeter Preis aus Open Prices (Kassenbons der Open-Food-Facts-Community). */
data class PriceSighting(
    val price: Double,
    val date: LocalDate,
    val store: String?,
    val discounted: Boolean,
)

/** Was die App zu einem Produkt anzeigt. */
data class PriceHint(val latest: PriceSighting, val cheapest: PriceSighting?, val count: Int)

object Prices {
    /** Ältere Preise sagen über heute kaum noch etwas. */
    private const val MAX_AGE_DAYS = 365L

    fun url(barcode: String) =
        "https://prices.openfoodfacts.org/api/v1/prices?product_code=$barcode&size=50&order_by=-date"

    /** Nur Euro-Preise aus Deutschland – derselbe Ladencode kann in Polen etwas ganz anderes sein. */
    fun parse(json: String): List<PriceSighting> {
        val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            if (item.optString("currency") != "EUR") return@mapNotNull null
            val location = item.optJSONObject("location")
            val country = location?.optString("osm_address_country_code").orEmpty()
            if (country.isNotEmpty() && country != "DE") return@mapNotNull null
            val price = item.optDouble("price").takeIf { !it.isNaN() && it > 0 } ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(item.optString("date")) }.getOrNull() ?: return@mapNotNull null
            val store = location?.let { loc ->
                listOf("osm_brand", "osm_name").map { loc.optString(it).trim() }
                    .firstOrNull { it.isNotEmpty() && it != "null" }
            }
            PriceSighting(price, date, store, discounted = item.optBoolean("price_is_discounted"))
        }
    }

    /** Neuester Preis, dazu der günstigste – aber nur, wenn der wirklich billiger war. */
    fun hint(sightings: List<PriceSighting>, today: LocalDate): PriceHint? {
        val recent = sightings.filter { !it.date.isBefore(today.minusDays(MAX_AGE_DAYS)) && !it.date.isAfter(today) }
        val latest = recent.maxByOrNull { it.date } ?: return null
        val cheapest = recent.minByOrNull { it.price }?.takeIf { it.price < latest.price }
        return PriceHint(latest, cheapest, recent.size)
    }

    fun formatPrice(price: Double): String = String.format(Locale.GERMANY, "%.2f €", price)

    private val shortDate = DateTimeFormatter.ofPattern("dd.MM.")
    private val longDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun describe(s: PriceSighting, today: LocalDate): String {
        val date = (if (s.date.year == today.year) shortDate else longDate).format(s.date)
        return formatPrice(s.price) +
            (s.store?.let { " bei $it" } ?: "") +
            (if (s.discounted) " (Angebot)" else "") +
            " am $date"
    }
}
