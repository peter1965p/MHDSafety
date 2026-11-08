package com.paeffgen.mhdsafety.update

import com.paeffgen.mhdsafety.domain.Version

/** Warum eine Update-Suche nicht geklappt hat. */
sealed interface UpdateProblem {
    /** Name des Servers nicht auflösbar: das Handy ist wirklich offline. */
    data object NoInternet : UpdateProblem
    data object Timeout : UpdateProblem
    data class ServerError(val code: Int) : UpdateProblem
    data object RateLimited : UpdateProblem
    data class Other(val detail: String?) : UpdateProblem
}

/** Reine Hilfsfunktionen rund um GitHub-Releases, ohne Netz – damit testbar. */
object UpdateSupport {
    /** Pausen vor dem 2. und 3. Versuch. */
    val retryDelaysMs = listOf(1_500L, 4_000L)

    fun problemForStatus(code: Int): UpdateProblem = when (code) {
        403, 429 -> UpdateProblem.RateLimited
        in 500..599 -> UpdateProblem.ServerError(code)
        else -> UpdateProblem.Other("Fehler $code")
    }

    fun message(problem: UpdateProblem): String = when (problem) {
        UpdateProblem.NoInternet -> "Keine Internetverbindung."
        UpdateProblem.Timeout -> "GitHub antwortet gerade nicht rechtzeitig. Bitte später nochmal versuchen."
        is UpdateProblem.ServerError -> "GitHub ist gerade gestört (Fehler ${problem.code}). Bitte später nochmal versuchen."
        UpdateProblem.RateLimited -> "Zu viele Anfragen an GitHub. In einer Stunde klappt es wieder."
        is UpdateProblem.Other -> "Update-Suche fehlgeschlagen" + (problem.detail?.let { ": $it" } ?: ".")
    }

    /** Aus der Weiterleitung von …/releases/latest auf …/releases/tag/v1.3.0 die Version lesen. */
    fun versionFromReleaseLocation(location: String?): Version? {
        val tag = location?.substringAfter("/releases/tag/", missingDelimiterValue = "")?.substringBefore('?')
        return tag?.takeIf { it.isNotEmpty() }?.let(Version::parse)
    }

    /** Das Release-Skript legt die APK immer als MHDSafety-<version>.apk ab. */
    fun apkUrl(repo: String, version: Version): String =
        "https://github.com/$repo/releases/download/v$version/MHDSafety-$version.apk"
}
