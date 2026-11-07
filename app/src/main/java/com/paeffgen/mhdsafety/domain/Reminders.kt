package com.paeffgen.mhdsafety.domain

import com.paeffgen.mhdsafety.data.DateType
import com.paeffgen.mhdsafety.data.FoodItem
import java.time.LocalDate

/** Was die tägliche Benachrichtigung enthalten soll. */
data class DailyReminder(
    val expired: List<FoodItem>,
    val dueToday: List<FoodItem>,
    val dueTomorrow: List<FoodItem>,
) {
    val isEmpty: Boolean get() = expired.isEmpty() && dueToday.isEmpty() && dueTomorrow.isEmpty()

    /** z. B. "2 heute fällig, 1 abgelaufen" */
    fun title(): String = listOfNotNull(
        dueToday.size.takeIf { it > 0 }?.let { "$it heute fällig" },
        expired.size.takeIf { it > 0 }?.let { "$it abgelaufen" },
        dueTomorrow.size.takeIf { it > 0 }?.let { "$it morgen fällig" },
    ).joinToString(", ")

    /** Eine Zeile pro Lebensmittel, wichtigstes zuerst. */
    fun lines(): List<String> =
        dueToday.map { "⏰ ${it.name}: ${dueTodayText(it)}" } +
            expired.map { "❌ ${it.name}: ${expiredText(it)}" } +
            dueTomorrow.map { "🔔 ${it.name}: morgen fällig" }

    private fun dueTodayText(item: FoodItem) = when (item.dateType) {
        DateType.USE_BY -> "heute verbrauchen"
        DateType.BEST_BEFORE -> "Puffer endet heute"
    }

    private fun expiredText(item: FoodItem) = when (item.dateType) {
        DateType.USE_BY -> "nicht mehr essen"
        DateType.BEST_BEFORE -> "drüber, bitte prüfen"
    }
}

object Reminders {
    fun collect(items: List<FoodItem>, bufferDays: Int, preAlarm: Boolean, today: LocalDate): DailyReminder {
        val byDays = items.groupBy { Expiry.daysUntilAlarm(it, bufferDays, today) }
        return DailyReminder(
            expired = items.filter { Expiry.daysUntilAlarm(it, bufferDays, today) < 0 }
                .sortedBy { Expiry.alarmDate(it, bufferDays) },
            dueToday = byDays[0L].orEmpty(),
            dueTomorrow = if (preAlarm) byDays[1L].orEmpty() else emptyList(),
        )
    }
}
