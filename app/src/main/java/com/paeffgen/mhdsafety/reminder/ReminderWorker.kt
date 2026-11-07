package com.paeffgen.mhdsafety.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.paeffgen.mhdsafety.MhdSafetyApp
import com.paeffgen.mhdsafety.data.AutoDelete
import com.paeffgen.mhdsafety.domain.Reminders
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Läuft einmal täglich und verschickt die Sammel-Benachrichtigung. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MhdSafetyApp
        val settings = app.settings
        val today = LocalDate.now()

        // Lange Abgelaufenes zuerst wegräumen, damit es nicht ewig in der Erinnerung steht.
        runCatching { AutoDelete.run(app.foods, settings, today) }

        // Nicht zweimal am selben Tag benachrichtigen (z. B. nach Ändern der Uhrzeit).
        if (settings.lastNotified.first() == today) return Result.success()

        val reminder = Reminders.collect(
            items = app.foods.getAll(),
            bufferDays = settings.bufferDays.first(),
            preAlarm = settings.preAlarm.first(),
            today = today,
        )
        if (!reminder.isEmpty) Notifications.showDaily(applicationContext, reminder)
        settings.setLastNotified(today)
        return Result.success()
    }
}
