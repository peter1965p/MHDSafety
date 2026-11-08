package com.paeffgen.mhdsafety.data

import com.paeffgen.mhdsafety.domain.RecipeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

sealed interface FetchResult {
    data class Found(val recipe: Recipe) : FetchResult
    data object NoRecipe : FetchResult
    data class Failed(val message: String) : FetchResult
}

/** Lädt eine Rezeptseite und liest das Rezept mit [RecipeParser] aus. */
class RecipeFetcher {
    suspend fun fetch(url: String): FetchResult = withContext(Dispatchers.IO) {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return@withContext FetchResult.Failed("Ungültiger Link: $url")
        }
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            // Manche Seiten liefern ohne Browser-Kennung nur eine Fehlerseite.
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36",
            )
            connection.setRequestProperty("Accept-Language", "de-DE,de;q=0.9")

            val code = connection.responseCode
            if (code !in 200..299) return@withContext FetchResult.Failed("Die Seite antwortet nicht (Fehler $code).")

            val charset = connection.contentType
                ?.substringAfter("charset=", "")
                ?.substringBefore(';')
                ?.trim('"', ' ')
                ?.let { runCatching { Charset.forName(it) }.getOrNull() }
                ?: Charsets.UTF_8
            // Rezeptseiten sind groß, aber 5 MB reichen allemal.
            val bytes = connection.inputStream.use { it.readNBytesCompat(5 * 1024 * 1024) }
            val finalUrl = connection.url.toString()
            RecipeParser.parse(String(bytes, charset), finalUrl)
                ?.let { FetchResult.Found(it) }
                ?: FetchResult.NoRecipe
        } catch (e: IOException) {
            FetchResult.Failed("Seite konnte nicht geladen werden: ${e.message ?: "keine Verbindung"}")
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < max) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
