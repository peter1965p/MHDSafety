package com.paeffgen.mhdsafety.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Antwort der MHD-API. [body] ist null, wenn der Server nichts (oder kein JSON) geschickt hat. */
data class ApiResponse(val code: Int, val body: JSONObject?) {
    val ok: Boolean get() = code in 200..299

    /** Fehlertext des Servers (FastAPI: "detail"), sonst eine allgemeine Meldung. */
    fun message(): String {
        val detail = body?.opt("detail")
        return when {
            detail is String -> detail
            code == 429 -> "Zu viele Versuche. Bitte später erneut versuchen."
            code == 422 -> "Eingabe unvollständig oder ungültig."
            code >= 500 -> "Der Server hat ein Problem ($code)."
            else -> "Fehler $code"
        }
    }
}

/** Schlanker HTTP-Client für die MHD-API (ohne Zusatzbibliotheken). */
class ApiClient(private val baseUrl: () -> String) {

    /** @throws IOException wenn der Server nicht erreichbar ist */
    suspend fun call(method: String, path: String, body: JSONObject? = null, token: String? = null): ApiResponse =
        withContext(Dispatchers.IO) {
            val conn = URL(baseUrl().trimEnd('/') + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 10_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("Accept", "application/json")
                token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val code = conn.responseCode
                val stream = if (code >= 400) conn.errorStream else conn.inputStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = text.takeIf { it.isNotBlank() }?.let {
                    try { JSONObject(it) } catch (_: JSONException) { null }
                }
                ApiResponse(code, json)
            } finally {
                conn.disconnect()
            }
        }
}
