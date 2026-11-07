package com.paeffgen.mhdsafety.domain

import java.time.LocalDate

/**
 * Liest ein von Hand getipptes Datum.
 *
 * Erlaubt `09.11.2026`, `9.11.26`, `9.11.` / `9.11` (ohne Jahr) und reine Ziffern
 * `0911`, `091126`, `09112026`. Ohne Jahr wird das nächste passende Datum genommen
 * (liegt es mehr als 60 Tage zurück, ist das nächste Jahr gemeint).
 */
object ManualDate {
    private val withSeparators = Regex("""(\d{1,2})[.\-/ ](\d{1,2})(?:[.\-/ ](\d{2}|\d{4})?)?""")

    fun parse(input: String, today: LocalDate): LocalDate? {
        val text = input.trim()
        val (day, month, year) = when {
            text.all(Char::isDigit) -> when (text.length) {
                4 -> Triple(text.substring(0, 2), text.substring(2, 4), null)
                6 -> Triple(text.substring(0, 2), text.substring(2, 4), text.substring(4))
                8 -> Triple(text.substring(0, 2), text.substring(2, 4), text.substring(4))
                else -> return null
            }
            else -> withSeparators.matchEntire(text)?.groupValues
                ?.let { Triple(it[1], it[2], it[3].ifEmpty { null }) }
                ?: return null
        }
        val d = day.toInt()
        val m = month.toInt()
        if (year != null) {
            val y = year.toInt().let { if (it < 100) 2000 + it else it }
            return of(y, m, d)
        }
        val thisYear = of(today.year, m, d) ?: return null
        return if (thisYear.isBefore(today.minusDays(60))) of(today.year + 1, m, d) else thisYear
    }

    private fun of(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.of(y, m, d) }.getOrNull()
}
