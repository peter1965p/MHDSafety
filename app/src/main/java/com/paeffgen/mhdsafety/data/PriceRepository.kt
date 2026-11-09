package com.paeffgen.mhdsafety.data

import com.paeffgen.mhdsafety.domain.PriceHint
import com.paeffgen.mhdsafety.domain.Prices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/** Was über die Preise eines Produkts bekannt ist. */
sealed interface PriceLookup {
    data class Known(val hint: PriceHint) : PriceLookup
    data object Unknown : PriceLookup
    data object Offline : PriceLookup
}

/**
 * Gemeldete Preise von Open Prices (prices.openfoodfacts.org) – kostenlos, ohne Anmeldung.
 * Ergebnisse bleiben ein paar Stunden im Speicher, damit Hin- und Herblättern nichts kostet.
 */
class PriceRepository {
    private data class Cached(val at: Long, val result: PriceLookup)

    private val cache = ConcurrentHashMap<String, Cached>()

    suspend fun lookup(barcode: String): PriceLookup {
        if (!barcode.all(Char::isDigit)) return PriceLookup.Unknown
        cache[barcode]?.takeIf { System.currentTimeMillis() - it.at < CACHE_MS }?.let { return it.result }
        val result = fetch(barcode)
        if (result != PriceLookup.Offline) cache[barcode] = Cached(System.currentTimeMillis(), result)
        return result
    }

    private suspend fun fetch(barcode: String): PriceLookup = withContext(Dispatchers.IO) {
        val connection = URL(Prices.url(barcode)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "MHDSafety/1.0 (Android)")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext PriceLookup.Offline
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            Prices.hint(Prices.parse(json), LocalDate.now())?.let { PriceLookup.Known(it) } ?: PriceLookup.Unknown
        } catch (_: Exception) {
            PriceLookup.Offline
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CACHE_MS = 6 * 60 * 60 * 1000L
    }
}
