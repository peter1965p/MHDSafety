package com.paeffgen.mhdsafety.domain

import com.paeffgen.mhdsafety.data.DateType
import com.paeffgen.mhdsafety.data.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ExpiryTest {
    private val today = LocalDate.of(2026, 11, 7)

    private fun item(date: LocalDate, type: DateType = DateType.BEST_BEFORE) =
        FoodItem(name = "Test", date = date, dateType = type)

    @Test
    fun bestBefore_addsBuffer() {
        assertEquals(LocalDate.of(2026, 11, 10), Expiry.alarmDate(item(today), 3))
    }

    @Test
    fun useBy_ignoresBuffer() {
        assertEquals(today, Expiry.alarmDate(item(today, DateType.USE_BY), 3))
    }

    @Test
    fun bestBefore_justPastMhd_isStillNotExpired() {
        // MHD war gestern, mit 3 Tagen Puffer noch 2 Tage bis Alarm
        val status = Expiry.status(item(today.minusDays(1)), 3, today)
        assertEquals(ExpiryStatus.SOON, status)
    }

    @Test
    fun status_transitions() {
        assertEquals(ExpiryStatus.OK, Expiry.status(item(today.plusDays(3)), 0, today))
        assertEquals(ExpiryStatus.SOON, Expiry.status(item(today.plusDays(2)), 0, today))
        assertEquals(ExpiryStatus.DUE, Expiry.status(item(today), 0, today))
        assertEquals(ExpiryStatus.EXPIRED, Expiry.status(item(today.minusDays(1)), 0, today))
    }

    @Test
    fun useBy_expiresDayAfterDate() {
        assertEquals(ExpiryStatus.DUE, Expiry.status(item(today, DateType.USE_BY), 3, today))
        assertEquals(ExpiryStatus.EXPIRED, Expiry.status(item(today.minusDays(1), DateType.USE_BY), 3, today))
    }

    @Test
    fun ownUseBy_beforeMhd_replacesMhdAndBuffer() {
        // Sylvis Fall: MHD 09.11., selbst verbrauchen bis 08.11.
        val i = FoodItem(name = "Test", date = LocalDate.of(2026, 11, 9), ownUseBy = LocalDate.of(2026, 11, 8))
        assertEquals(LocalDate.of(2026, 11, 8), Expiry.alarmDate(i, 3))
    }

    @Test
    fun ownUseBy_neverLaterThanPrintedUseBy() {
        val printed = LocalDate.of(2026, 11, 9)
        val later = FoodItem(name = "Hack", date = printed, dateType = DateType.USE_BY, ownUseBy = printed.plusDays(2))
        val earlier = later.copy(ownUseBy = printed.minusDays(1))
        assertEquals(printed, Expiry.alarmDate(later, 3))
        assertEquals(printed.minusDays(1), Expiry.alarmDate(earlier, 3))
    }

    @Test
    fun autoDelete_afterGracePeriod() {
        // Verbrauchsdatum (= Alarmtag) vor 7 bzw. 8 Tagen
        val sevenAgo = item(today.minusDays(7), DateType.USE_BY)
        val eightAgo = item(today.minusDays(8), DateType.USE_BY)
        assertEquals(false, Expiry.shouldAutoDelete(sevenAgo, 3, 7, today))
        assertEquals(true, Expiry.shouldAutoDelete(eightAgo, 3, 7, today))
    }

    @Test
    fun autoDelete_countsFromAlarmIncludingBuffer() {
        // MHD vor 10 Tagen + 3 Tage Puffer = Alarm vor 7 Tagen -> bleibt noch
        assertEquals(false, Expiry.shouldAutoDelete(item(today.minusDays(10)), 3, 7, today))
        assertEquals(true, Expiry.shouldAutoDelete(item(today.minusDays(11)), 3, 7, today))
    }

    @Test
    fun autoDelete_zeroMeansDayAfterAlarm() {
        assertEquals(false, Expiry.shouldAutoDelete(item(today, DateType.USE_BY), 3, 0, today))
        assertEquals(true, Expiry.shouldAutoDelete(item(today.minusDays(1), DateType.USE_BY), 3, 0, today))
    }
}
