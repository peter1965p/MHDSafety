package com.paeffgen.mhdsafety.data

import com.paeffgen.mhdsafety.domain.Expiry
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Räumt Einträge weg, deren Alarmtag länger als eingestellt vorbei ist (lokal oder in der Cloud). */
object AutoDelete {
    /** @return Anzahl gelöschter Einträge */
    suspend fun run(foods: FoodRepository, settings: SettingsRepository, today: LocalDate = LocalDate.now()): Int {
        val bufferDays = settings.bufferDays.first()
        val deleteAfterDays = settings.deleteAfterDays.first()
        val old = foods.getAll().filter { Expiry.shouldAutoDelete(it, bufferDays, deleteAfterDays, today) }
        if (old.isNotEmpty()) foods.deleteAll(old)
        return old.size
    }
}
