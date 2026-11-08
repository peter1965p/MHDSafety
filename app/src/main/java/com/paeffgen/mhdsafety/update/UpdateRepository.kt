package com.paeffgen.mhdsafety.update

import android.content.Context
import com.paeffgen.mhdsafety.BuildConfig
import com.paeffgen.mhdsafety.domain.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

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

    /** Ergebnis eines einzelnen Versuchs. */
    private sealed interface Attempt {
        data class Done(val result: CheckResult) : Attempt
        data class Retry(val problem: UpdateProblem) : Attempt
    }

    /**
     * Fragt die GitHub-Schnittstelle, bei Störungen bis zu dreimal.
     * Streikt sie ganz, wird die Version direkt von der Release-Seite gelesen.
     */
    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        var problem: UpdateProblem = UpdateProblem.Other(null)
        for (attempt in 0..UpdateSupport.retryDelaysMs.size) {
            if (attempt > 0) delay(UpdateSupport.retryDelaysMs[attempt - 1])
            when (val result = checkApiOnce()) {
                is Attempt.Done -> return@withContext result.result
                is Attempt.Retry -> problem = result.problem
            }
            // Offline: weitere Versuche bringen nichts.
            if (problem == UpdateProblem.NoInternet) break
        }
        // Ohne Internet hilft auch der Notweg nicht.
        if (problem != UpdateProblem.NoInternet) checkReleasePage()?.let { return@withContext it }
        CheckResult.Failed(UpdateSupport.message(problem))
    }

    private fun checkApiOnce(): Attempt {
        val connection = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "MHDSafety/${BuildConfig.VERSION_NAME}")

            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                // Noch gar kein Release veröffentlicht
                HttpURLConnection.HTTP_NOT_FOUND -> return Attempt.Done(CheckResult.UpToDate)
                else -> return when (val p = UpdateSupport.problemForStatus(code)) {
                    is UpdateProblem.Other -> Attempt.Done(CheckResult.Failed(UpdateSupport.message(p)))
                    else -> Attempt.Retry(p)
                }
            }

            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val latest = Version.parse(json.optString("tag_name"))
                ?: return Attempt.Done(CheckResult.Failed("Unbekannte Versionsnummer auf GitHub."))
            if (latest <= currentVersion) return Attempt.Done(CheckResult.UpToDate)

            val assets = json.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?: return Attempt.Done(CheckResult.Failed("Version $latest hat keine APK-Datei."))

            return Attempt.Done(
                CheckResult.UpdateAvailable(
                    UpdateInfo(
                        version = latest,
                        notes = json.optString("body").trim(),
                        apkUrl = apk.getString("browser_download_url"),
                        apkSize = apk.optLong("size"),
                    ),
                ),
            )
        } catch (_: UnknownHostException) {
            return Attempt.Retry(UpdateProblem.NoInternet)
        } catch (_: SocketTimeoutException) {
            return Attempt.Retry(UpdateProblem.Timeout)
        } catch (e: IOException) {
            return Attempt.Retry(UpdateProblem.Other(e.message ?: e.javaClass.simpleName))
        } catch (_: JSONException) {
            return Attempt.Retry(UpdateProblem.Other("Antwort von GitHub nicht lesbar"))
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Notweg ohne Schnittstelle: github.com/…/releases/latest leitet auf …/releases/tag/vX.Y.Z weiter.
     * Die APK heißt immer MHDSafety-X.Y.Z.apk. Ohne Release-Notizen, aber das Update kommt an.
     */
    private fun checkReleasePage(): CheckResult? {
        val connection = URL("https://github.com/${BuildConfig.GITHUB_REPO}/releases/latest")
            .openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "MHDSafety/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode !in 300..399) return null
            val latest = UpdateSupport.versionFromReleaseLocation(connection.getHeaderField("Location")) ?: return null
            if (latest <= currentVersion) {
                CheckResult.UpToDate
            } else {
                CheckResult.UpdateAvailable(
                    UpdateInfo(
                        version = latest,
                        notes = "",
                        apkUrl = UpdateSupport.apkUrl(BuildConfig.GITHUB_REPO, latest),
                        apkSize = 0,
                    ),
                )
            }
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** Lädt die APK in den Cache. [onProgress] bekommt Werte von 0 bis 1. */
    /** Lädt die APK in den Cache, bei Störungen ein zweites Mal. [onProgress] bekommt Werte von 0 bis 1. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = try {
        downloadOnce(info, onProgress)
    } catch (_: IOException) {
        delay(UpdateSupport.retryDelaysMs.first())
        onProgress(0f)
        downloadOnce(info, onProgress)
    }

    private suspend fun downloadOnce(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
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
                val code = connection.responseCode
                throw IOException(if (code in 500..599) "GitHub ist gerade gestört (Fehler $code)" else "Fehler $code")
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
