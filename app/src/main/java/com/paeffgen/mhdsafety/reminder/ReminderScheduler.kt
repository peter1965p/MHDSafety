package com.paeffgen.mhdsafety.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.paeffgen.mhdsafety.data.NotifyTime
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val WORK_NAME = "daily_expiry_reminder"

    /**
     * Plant den täglichen Check zur gewünschten Uhrzeit.
     * WorkManager übersteht Neustarts des Handys von selbst.
     *
     * @param replace true, wenn sich die Uhrzeit geändert hat; sonst bleibt ein bestehender Plan erhalten.
     */
    fun schedule(context: Context, time: NotifyTime, replace: Boolean) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(time.hour, time.minute)
        if (!next.isAfter(now)) next = next.plusDays(1)

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
