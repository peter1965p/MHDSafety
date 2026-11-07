package com.paeffgen.mhdsafety.domain

import com.paeffgen.mhdsafety.data.DateType
import com.paeffgen.mhdsafety.data.FoodItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RemindersTest {
    private val today = LocalDate.of(2026, 11, 7)

    private fun item(name: String, date: LocalDate, type: DateType = DateType.BEST_BEFORE) =
        FoodItem(name = name, date = date, dateType = type)

    private val items = listOf(
        item("Joghurt", today.minusDays(3)),           // MHD + 3 = heute
        item("Hack", today, DateType.USE_BY),          // heute
        item("Milch", today.minusDays(2)),             // morgen
        item("Käse", today.minusDays(10)),             // abgelaufen
        item("Nudeln", today.plusDays(100)),           // ok
    )

    @Test
    fun collect_sortsItemsIntoBuckets() {
        val r = Reminders.collect(items, bufferDays = 3, preAlarm = true, today = today)
        assertEquals(listOf("Joghurt", "Hack"), r.dueToday.map { it.name })
        assertEquals(listOf("Milch"), r.dueTomorrow.map { it.name })
        assertEquals(listOf("Käse"), r.expired.map { it.name })
        assertEquals("2 heute fällig, 1 abgelaufen, 1 morgen fällig", r.title())
    }

    @Test
    fun collect_withoutPreAlarm_skipsTomorrow() {
        val r = Reminders.collect(items, bufferDays = 3, preAlarm = false, today = today)
        assertTrue(r.dueTomorrow.isEmpty())
    }

    @Test
    fun collect_nothingDue_isEmpty() {
        val r = Reminders.collect(listOf(item("Nudeln", today.plusDays(100))), 3, true, today)
        assertTrue(r.isEmpty)
    }
}
