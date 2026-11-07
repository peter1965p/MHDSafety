package com.paeffgen.mhdsafety.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.paeffgen.mhdsafety.BuildConfig
import com.paeffgen.mhdsafety.MhdSafetyApp
import com.paeffgen.mhdsafety.reminder.Notifications
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Schaut einmal am Tag im Hintergrund nach Updates und meldet sich per Benachrichtigung. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MhdSafetyApp
        if (BuildConfig.DEBUG || !app.settings.autoUpdate.first()) return Result.success()

        val result = app.updates.check()
        if (result is CheckResult.UpdateAvailable) {
            val version = result.info.version.toString()
            // Pro Version nur einmal benachrichtigen
            if (app.settings.notifiedUpdateVersion.first() != version) {
                Notifications.showUpdate(applicationContext, version)
                app.settings.setNotifiedUpdateVersion(version)
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily_update_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
