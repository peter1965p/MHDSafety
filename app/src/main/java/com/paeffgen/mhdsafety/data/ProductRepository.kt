package com.paeffgen.mhdsafety.data

import com.paeffgen.mhdsafety.domain.CategoryMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface LookupResult {
    data class Found(val name: String, val category: Category?) : LookupResult
    data object NotFound : LookupResult
    data object NetworkError : LookupResult
}

class ProductRepository(private val dao: ProductDao) {

    /** Erst im eigenen Gedächtnis suchen, dann bei Open Food Facts. */
    suspend fun lookup(barcode: String): LookupResult {
        if (!barcode.all(Char::isDigit)) return LookupResult.NotFound
        dao.get(barcode)?.let { return LookupResult.Found(it.name, it.category) }
        return fetchFromOpenFoodFacts(barcode)
    }

    suspend fun remember(barcode: String, name: String, category: Category) {
        dao.upsert(Product(barcode, name, category))
    }

    private suspend fun fetchFromOpenFoodFacts(barcode: String): LookupResult = withContext(Dispatchers.IO) {
        val url = URL(
            "https://world.openfoodfacts.org/api/v2/product/$barcode" +
                "?fields=product_name,product_name_de,brands,categories_tags&lc=de",
        )
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            // Open Food Facts bittet um einen aussagekräftigen User-Agent.
            connection.setRequestProperty("User-Agent", "MHDSafety/1.0 (Android)")

            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@withContext LookupResult.NotFound
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext LookupResult.NetworkError

            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val product = json.optJSONObject("product")
            if (json.optInt("status") != 1 || product == null) return@withContext LookupResult.NotFound

            // "Ritter Sport, Alfred Ritter" -> "Ritter Sport"
            val brand = product.optString("brands").substringBefore(',').trim()
            val productName = listOf("product_name_de", "product_name")
                .map { product.optString(it).trim() }
                .firstOrNull { it.isNotEmpty() }
            val name = when {
                productName == null -> brand.ifEmpty { return@withContext LookupResult.NotFound }
                brand.isEmpty() || productName.contains(brand, ignoreCase = true) -> productName
                else -> "$brand $productName"
            }

            val tagsJson = product.optJSONArray("categories_tags")
            val tags = (0 until (tagsJson?.length() ?: 0)).map { tagsJson!!.getString(it) }
            LookupResult.Found(name, CategoryMapper.map(tags))
        } catch (_: IOException) {
            LookupResult.NetworkError
        } catch (_: org.json.JSONException) {
            LookupResult.NotFound
        } finally {
            connection.disconnect()
        }
    }
}
