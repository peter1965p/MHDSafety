package com.paeffgen.mhdsafety.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.paeffgen.mhdsafety.MhdSafetyApp
import com.paeffgen.mhdsafety.domain.Reminders
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Läuft einmal täglich und verschickt die Sammel-Benachrichtigung. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MhdSafetyApp
        val settings = app.settings
        val today = LocalDate.now()

        // Nicht zweimal am selben Tag benachrichtigen (z. B. nach Ändern der Uhrzeit).
        if (settings.lastNotified.first() == today) return Result.success()

        val reminder = Reminders.collect(
            items = app.database.foodDao().getAll(),
            bufferDays = settings.bufferDays.first(),
            preAlarm = settings.preAlarm.first(),
            today = today,
        )
        if (!reminder.isEmpty) Notifications.showDaily(applicationContext, reminder)
        settings.setLastNotified(today)
        return Result.success()
    }
}
