package com.paeffgen.mhdsafety.domain

import com.paeffgen.mhdsafety.data.DateType
import com.paeffgen.mhdsafety.data.FoodItem
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ExpiryStatus {
    /** Noch mehr als [Expiry.SOON_DAYS] Tage bis zum Alarm. */
    OK,

    /** Alarm kommt in den nächsten Tagen. */
    SOON,

    /** Heute ist Alarmtag. */
    DUE,

    /** Alarmtag ist vorbei. */
    EXPIRED,
}

object Expiry {
    const val DEFAULT_BUFFER_DAYS = 3
    const val MAX_BUFFER_DAYS = 14
    const val SOON_DAYS = 2

    /**
     * Tag, an dem Alarm geschlagen wird.
     * MHD: Datum + Puffer. Verbrauchsdatum: genau das Datum, ohne Puffer.
     * Ein eigenes Verbrauchsdatum ersetzt das, darf aber nie hinter
     * einem aufgedruckten Verbrauchsdatum liegen.
     */
    fun alarmDate(item: FoodItem, bufferDays: Int): LocalDate {
        val own = item.ownUseBy
        return when (item.dateType) {
            DateType.BEST_BEFORE -> own ?: item.date.plusDays(bufferDays.toLong())
            DateType.USE_BY -> if (own != null && own.isBefore(item.date)) own else item.date
        }
    }

    /** Tage bis zum Alarm (negativ = Alarm ist vorbei). */
    fun daysUntilAlarm(item: FoodItem, bufferDays: Int, today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, alarmDate(item, bufferDays))

    fun status(item: FoodItem, bufferDays: Int, today: LocalDate): ExpiryStatus {
        val days = daysUntilAlarm(item, bufferDays, today)
        return when {
            days < 0 -> ExpiryStatus.EXPIRED
            days == 0L -> ExpiryStatus.DUE
            days <= SOON_DAYS -> ExpiryStatus.SOON
            else -> ExpiryStatus.OK
        }
    }
}
