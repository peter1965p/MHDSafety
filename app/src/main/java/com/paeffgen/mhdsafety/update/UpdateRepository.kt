package com.paeffgen.mhdsafety.update

import android.content.Context
import com.paeffgen.mhdsafety.BuildConfig
import com.paeffgen.mhdsafety.domain.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: Version,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
)

sealed interface CheckResult {
    data class UpdateAvailable(val info: UpdateInfo) : CheckResult
    data object UpToDate : CheckResult
    data class Failed(val message: String) : CheckResult
}

/** Sucht auf GitHub (Releases von [BuildConfig.GITHUB_REPO]) nach einer neueren Version. */
class UpdateRepository(private val context: Context) {

    val currentVersion: Version = Version.parse(BuildConfig.VERSION_NAME) ?: Version(0, 0, 0)

    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "MHDSafety/${BuildConfig.VERSION_NAME}")

            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                // Noch gar kein Release veröffentlicht
                HttpURLConnection.HTTP_NOT_FOUND -> return@withContext CheckResult.UpToDate
                else -> return@withContext CheckResult.Failed("GitHub antwortet nicht (${connection.responseCode}).")
            }

            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val latest = Version.parse(json.optString("tag_name"))
                ?: return@withContext CheckResult.Failed("Unbekannte Versionsnummer auf GitHub.")
            if (latest <= currentVersion) return@withContext CheckResult.UpToDate

            val assets = json.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?: return@withContext CheckResult.Failed("Version $latest hat keine APK-Datei.")

            CheckResult.UpdateAvailable(
                UpdateInfo(
                    version = latest,
                    notes = json.optString("body").trim(),
                    apkUrl = apk.getString("browser_download_url"),
                    apkSize = apk.optLong("size"),
                ),
            )
        } catch (_: IOException) {
            CheckResult.Failed("Keine Internetverbindung.")
        } catch (_: JSONException) {
            CheckResult.Failed("Antwort von GitHub nicht lesbar.")
        } finally {
            connection.disconnect()
        }
    }

    /** Lädt die APK in den Cache. [onProgress] bekommt Werte von 0 bis 1. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // alte Downloads aufräumen
        val target = File(dir, "MHDSafety-${info.version}.apk")

        // GitHub leitet auf einen anderen Server um; HttpURLConnection folgt dem selbst.
        val connection = URL(info.apkUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "MHDSafety/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Download fehlgeschlagen (${connection.responseCode})")
            }
            val total = info.apkSize.takeIf { it > 0 } ?: connection.contentLengthLong
            var done = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
            if (info.apkSize > 0 && target.length() != info.apkSize) {
                target.delete()
                throw IOException("Download unvollständig")
            }
            target
        } finally {
            connection.disconnect()
        }
    }
}
