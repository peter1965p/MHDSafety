package com.paeffgen.mhdsafety.data

import com.paeffgen.mhdsafety.domain.RecipeParser
import com.paeffgen.mhdsafety.domain.VideoLink
import org.json.JSONObject
import java.net.URLEncoder
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
private const val UA = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

private const val UA_DESKTOP = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"

class RecipeFetcher {
    suspend fun fetch(url: String): FetchResult =
        if (VideoLink.isVideo(url)) fetchVideo(url) else fetchPage(url)

    /** Video-Rezept: Titel/Kanal/Vorschaubild per oEmbed (ohne API-Schlüssel), Zutaten aus der Beschreibung, wenn frei lesbar. */
    private suspend fun fetchVideo(shared: String): FetchResult = withContext(Dispatchers.IO) {
        val source = VideoLink.source(shared)
        val url = VideoLink.canonical(shared)
        val videoId = VideoLink.youtubeId(shared)
        val enc = URLEncoder.encode(url, "UTF-8")
        val oembed = when (source) {
            VideoLink.YOUTUBE -> "https://www.youtube.com/oembed?url=$enc&format=json"
            VideoLink.TIKTOK -> "https://www.tiktok.com/oembed?url=$enc"
            else -> null
        }
        val meta = oembed?.let { runCatching { JSONObject(String(get(it), Charsets.UTF_8)) }.getOrNull() }
        val title = meta?.optString("title")?.takeIf { it.isNotBlank() }
        val channel = meta?.optString("author_name")?.takeIf { it.isNotBlank() }
        val thumb = meta?.optString("thumbnail_url")?.takeIf { it.isNotBlank() } ?: videoId?.let(VideoLink::thumbnail)
        val ingredients = if (videoId != null) {
            runCatching { VideoLink.ingredientsFromDescription(youtubeDescription(String(get(url, desktop = true), Charsets.UTF_8))) }.getOrDefault(emptyList())
        } else emptyList()
        val name = title ?: when (source) {
            VideoLink.INSTAGRAM -> "Rezept von Instagram"
            VideoLink.TIKTOK -> "Rezept von TikTok"
            else -> "Video-Rezept"
        }
        FetchResult.Found(
            Recipe(
                url = url,
                name = name.take(150),
                ingredients = ingredients,
                imageUrl = thumb,
                videoId = videoId,
                source = source,
                notes = channel?.let { "Video von $it" },
            ),
        )
    }

    /** Die Videobeschreibung steht im Seitenquelltext als "shortDescription":"…". */
    private fun youtubeDescription(html: String): String {
        val start = html.indexOf("\"shortDescription\":\"")
        if (start < 0) return ""
        val rest = html.substring(start + 20)
        val sb = StringBuilder()
        var i = 0
        while (i < rest.length) {
            val c = rest[i]
            if (c == '\\' && i + 1 < rest.length) {
                when (val n = rest[i + 1]) {
                    'n' -> sb.append('\n')
                    'u' -> if (i + 5 < rest.length) { sb.append(rest.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                    else -> sb.append(n)
                }
                i += 2
                continue
            }
            if (c == '"') break
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    /** @param desktop YouTube liefert die Videobeschreibung nur in der Desktop-Seite (mit Zustimmungs-Cookie). */
    private fun get(url: String, desktop: Boolean = false): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", if (desktop) UA_DESKTOP else UA)
            c.setRequestProperty("Accept-Language", "de-DE,de;q=0.9")
            if (desktop) c.setRequestProperty("Cookie", "CONSENT=YES+1")
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readNBytesCompat(3 * 1024 * 1024) }
        } finally {
            c.disconnect()
        }
    }

    private suspend fun fetchPage(url: String): FetchResult = withContext(Dispatchers.IO) {
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
