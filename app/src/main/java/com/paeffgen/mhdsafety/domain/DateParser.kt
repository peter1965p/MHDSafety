package com.paeffgen.mhdsafety.domain

import java.time.LocalDate
import java.time.YearMonth

/** Ein auf der Packung gefundenes Datum. */
data class ParsedDate(val date: LocalDate, val useBy: Boolean)

/**
 * Findet Haltbarkeitsdaten in per Texterkennung gelesenem Packungstext.
 *
 * Unterstützt u. a. `12.03.2027`, `12.03.27`, `12/03/2027`, `12-03-27`, `12 03 2027`,
 * `03/2027`, `03.27` (nur Monat → Monatsende), `12 MÄR 2027` / `MAR 27`, `2027-03-12`
 * und – als Notnagel – zusammengeschriebene Daten wie `120327`.
 *
 * Punktmatrix-Drucke (Tintenstrahl) und blasse Aufdrucke liest die Texterkennung oft schief:
 * Ziffern werden zu Buchstaben (`S` statt 5, `B` statt 8), Trennzeichen zu `,` oder `:`
 * und zwischen einzelne Ziffern rutschen Leerzeichen. Das wird hier großzügig ausgeglichen.
 */
object DateParser {
    // Texterkennung verwechselt Ziffern mit ähnlich aussehenden Zeichen – in Zahlen erlauben wir das.
    private val lookalikes = mapOf(
        'O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0',
        'I' to '1', 'l' to '1', 'i' to '1', '|' to '1', '!' to '1',
        'Z' to '2', 'z' to '2',
        'S' to '5', 's' to '5',
        'G' to '6', 'b' to '6',
        'B' to '8',
        'g' to '9', 'q' to '9',
    )
    private const val D = "[0-9OoDQIli|!ZzSsGbBgq]" // genau die Schlüssel von lookalikes
    private const val SEP = "\\s*[./\\-]\\s*"
    /** Zwischen Tag, Monat und Jahr liest die Texterkennung auch mal Komma, Doppelpunkt oder Mittelpunkt. */
    private const val SEP_LOOSE = "\\s*[./\\-,:·]\\s*"

    private val fullDate = Regex("(?<![0-9])($D{1,2})$SEP_LOOSE($D{1,2})$SEP_LOOSE($D{4}|$D{2})(?![0-9])")
    private val isoDate = Regex("(?<![0-9])(20[0-9]{2})$SEP([0-9]{1,2})$SEP([0-9]{1,2})(?![0-9])")
    /** `120327` oder `12032027` – nur wenn sonst nichts gefunden wird, Chargennummern sehen ähnlich aus. */
    private val compactDate = Regex("(?<![0-9A-Za-z])([0-9]{2})([0-9]{2})(20[0-9]{2}|[0-9]{2})(?![0-9A-Za-z])")
    /** Punktmatrix: „1 2 . 0 3 . 2 7“ – einzeln stehende Ziffern mit Leerzeichen dazwischen. */
    private val splitDigits = Regex("(?<![0-9])([0-9]) (?=[0-9](?![0-9]))")
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
    fun parse(raw: String, today: LocalDate): ParsedDate? {
        val text = splitDigits.replace(raw, "$1")
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

        return ParsedDate(best.date, useBy = mentionsUseBy(raw))
    }

    /** Steht "zu verbrauchen bis" / "use by" im Text? */
    fun mentionsUseBy(text: String): Boolean = useByKeyword.containsMatchIn(text)

    private fun candidates(text: String, today: LocalDate): List<Candidate> {
        val result = mutableListOf<Candidate>()
        val taken = mutableListOf<IntRange>()
        fun overlaps(r: IntRange) = taken.any { it.first <= r.last && r.first <= it.last }

        fun add(match: MatchResult, date: LocalDate?, dayPrecise: Boolean, minDigits: Int = 0): Boolean {
            if (date == null || overlaps(match.range) || !isPlausible(date, today)) return false
            // Mit den Ersatzbuchstaben passt das Muster sonst auch auf Wortschnipsel.
            if (match.value.count { it.isDigit() } < minDigits) return false
            taken += match.range
            result += Candidate(date, match.range.first, dayPrecise)
            return true
        }

        isoDate.scan(text) { m ->
            val (y, mo, d) = m.destructured
            add(m, dateOf(d.toInt(), mo.toInt(), y.toInt()), dayPrecise = true)
        }
        fullDate.scan(text) { m ->
            val (d, mo, y) = m.destructured
            add(m, dateOf(num(d), num(mo), year(y)), dayPrecise = true, minDigits = 3)
        }
        spacedDate.scan(text) { m ->
            val (d, mo, y) = m.destructured
            add(m, dateOf(num(d), num(mo), year(y)), dayPrecise = true)
        }
        namedMonth.scan(text) { m ->
            val (d, name, y) = m.destructured
            val month = monthNames[name.uppercase().take(3)] ?: return@scan false
            if (d.isEmpty()) {
                add(m, endOfMonth(month, year(y)), dayPrecise = false)
            } else {
                add(m, dateOf(d.toInt(), month, year(y)), dayPrecise = true)
            }
        }
        monthYear.scan(text) { m ->
            val (mo, y) = m.destructured
            add(m, endOfMonth(num(mo), year(y)), dayPrecise = false, minDigits = 3)
        }
        if (result.isEmpty()) {
            compactDate.scan(text) { m ->
                val (d, mo, y) = m.destructured
                add(m, dateOf(d.toInt(), mo.toInt(), year(y)), dayPrecise = true)
            }
        }
        return result
    }

    /**
     * Wie findAll, aber ein verworfener Treffer verbraucht den Text nicht:
     * aus „bis: 05.01.2027“ darf „s: 05.01“ nicht das echte Datum verdecken.
     * @param accept true, wenn der Treffer übernommen wurde
     */
    private inline fun Regex.scan(text: String, accept: (MatchResult) -> Boolean) {
        var start = 0
        while (start < text.length) {
            val m = find(text, start) ?: return
            start = if (accept(m)) m.range.last + 1 else m.range.first + 1
        }
    }

    private fun num(s: String): Int = s.map { lookalikes[it] ?: it }.joinToString("").toInt()

    private fun year(s: String): Int = num(s).let { if (it < 100) 2000 + it else it }

    private fun dateOf(day: Int, month: Int, year: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    private fun endOfMonth(month: Int, year: Int): LocalDate? =
        runCatching { YearMonth.of(year, month).atEndOfMonth() }.getOrNull()

    /** Haltbarkeitsdaten liegen selten mehr als ein Jahr zurück oder zehn Jahre voraus. */
    private fun isPlausible(date: LocalDate, today: LocalDate): Boolean =
        !date.isBefore(today.minusYears(1)) && !date.isAfter(today.plusYears(10))
}
