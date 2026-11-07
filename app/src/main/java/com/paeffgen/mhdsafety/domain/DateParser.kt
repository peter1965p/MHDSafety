package com.paeffgen.mhdsafety.domain

import java.time.LocalDate
import java.time.YearMonth

/** Ein auf der Packung gefundenes Datum. */
data class ParsedDate(val date: LocalDate, val useBy: Boolean)

/**
 * Findet Haltbarkeitsdaten in per Texterkennung gelesenem Packungstext.
 *
 * Unterstützt u. a. `12.03.2027`, `12.03.27`, `12/03/2027`, `12-03-27`, `12 03 2027`,
 * `03/2027`, `03.27` (nur Monat → Monatsende) und `12 MÄR 2027` / `MAR 27`.
 */
object DateParser {
    // Texterkennung verwechselt gern O/0 und I/l/1 – in Zahlen erlauben wir das.
    private const val D = "[0-9OoIl]"
    private const val SEP = "\\s*[./\\-]\\s*"

    private val fullDate = Regex("(?<![0-9])($D{1,2})$SEP($D{1,2})$SEP($D{4}|$D{2})(?![0-9])")
    private val spacedDate = Regex("(?<![0-9])([0-9]{2}) ([0-9]{2}) ([0-9]{4}|[0-9]{2})(?![0-9])")
    private val monthYear = Regex("(?<![0-9./\\-])($D{1,2})$SEP($D{4}|$D{2})(?![0-9./\\-])")
    private val namedMonth = Regex(
        "(?:(?<![0-9])([0-9]{1,2})\\.?\\s*)?" +
            "(JAN|FEB|MÄR|MAR|MRZ|APR|MAI|MAY|JUN|JUL|AUG|SEP|OKT|OCT|NOV|DEZ|DEC)[A-ZÄ]*\\.?" +
            "\\s*([0-9]{4}|[0-9]{2})(?![0-9])",
        RegexOption.IGNORE_CASE,
    )
    private val keyword = Regex(
        "mhd|haltbar|verbrauch|best before|use by|exp|bis",
        RegexOption.IGNORE_CASE,
    )
    private val useByKeyword = Regex("verbrauchen|use by", RegexOption.IGNORE_CASE)

    private val monthNames = mapOf(
        "JAN" to 1, "FEB" to 2, "MÄR" to 3, "MAR" to 3, "MRZ" to 3, "APR" to 4, "MAI" to 5, "MAY" to 5,
        "JUN" to 6, "JUL" to 7, "AUG" to 8, "SEP" to 9, "OKT" to 10, "OCT" to 10, "NOV" to 11,
        "DEZ" to 12, "DEC" to 12,
    )

    private data class Candidate(val date: LocalDate, val position: Int, val dayPrecise: Boolean)

    /** Das wahrscheinlichste Haltbarkeitsdatum im Text, oder null. */
    fun parse(text: String, today: LocalDate): ParsedDate? {
        val candidates = candidates(text, today)
        if (candidates.isEmpty()) return null

        // Taggenaue Daten schlagen "nur Monat/Jahr".
        val pool = candidates.filter { it.dayPrecise }.ifEmpty { candidates }

        // Steht ein Stichwort wie "MHD" oder "bis" davor, nimm das erste Datum danach.
        // Sonst das späteste (ein Herstellungsdatum liegt immer vor dem MHD).
        val keywordEnd = keyword.findAll(text).lastOrNull()?.range?.last
        val best = keywordEnd
            ?.let { end -> pool.filter { it.position > end }.minByOrNull { it.position } }
            ?: pool.maxBy { it.date }

        return ParsedDate(best.date, useBy = mentionsUseBy(text))
    }

    /** Steht "zu verbrauchen bis" / "use by" im Text? */
    fun mentionsUseBy(text: String): Boolean = useByKeyword.containsMatchIn(text)

    private fun candidates(text: String, today: LocalDate): List<Candidate> {
        val result = mutableListOf<Candidate>()
        val taken = mutableListOf<IntRange>()
        fun overlaps(r: IntRange) = taken.any { it.first <= r.last && r.first <= it.last }

        fun add(match: MatchResult, date: LocalDate?, dayPrecise: Boolean) {
            if (date == null || overlaps(match.range) || !isPlausible(date, today)) return
            taken += match.range
            result += Candidate(date, match.range.first, dayPrecise)
        }

        for (m in fullDate.findAll(text)) {
            val (d, mo, y) = m.destructured
            add(m, dateOf(num(d), num(mo), year(y)), dayPrecise = true)
        }
        for (m in spacedDate.findAll(text)) {
            val (d, mo, y) = m.destructured
            add(m, dateOf(num(d), num(mo), year(y)), dayPrecise = true)
        }
        for (m in namedMonth.findAll(text)) {
            val (d, name, y) = m.destructured
            val month = monthNames[name.uppercase().take(3)] ?: continue
            if (d.isEmpty()) {
                add(m, endOfMonth(month, year(y)), dayPrecise = false)
            } else {
                add(m, dateOf(d.toInt(), month, year(y)), dayPrecise = true)
            }
        }
        for (m in monthYear.findAll(text)) {
            val (mo, y) = m.destructured
            add(m, endOfMonth(num(mo), year(y)), dayPrecise = false)
        }
        return result
    }

    private fun num(s: String): Int =
        s.replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1').toInt()

    private fun year(s: String): Int = num(s).let { if (it < 100) 2000 + it else it }

    private fun dateOf(day: Int, month: Int, year: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    private fun endOfMonth(month: Int, year: Int): LocalDate? =
        runCatching { YearMonth.of(year, month).atEndOfMonth() }.getOrNull()

    /** Haltbarkeitsdaten liegen selten mehr als ein Jahr zurück oder zehn Jahre voraus. */
    private fun isPlausible(date: LocalDate, today: LocalDate): Boolean =
        !date.isBefore(today.minusYears(1)) && !date.isAfter(today.plusYears(10))
}
