package com.paeffgen.mhdsafety

import android.app.Application
import com.paeffgen.mhdsafety.data.AppDatabase
import com.paeffgen.mhdsafety.data.AutoDelete
import com.paeffgen.mhdsafety.data.ProductRepository
import com.paeffgen.mhdsafety.data.SettingsRepository
import com.paeffgen.mhdsafety.reminder.Notifications
import com.paeffgen.mhdsafety.reminder.ReminderScheduler
import com.paeffgen.mhdsafety.update.UpdateRepository
import com.paeffgen.mhdsafety.update.UpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MhdSafetyApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val settings by lazy { SettingsRepository(this) }
    val products by lazy { ProductRepository(database.productDao()) }
    val updates by lazy { UpdateRepository(this) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        UpdateWorker.schedule(this)
        appScope.launch {
            AutoDelete.run(database.foodDao(), settings)
        }
        appScope.launch {
            ReminderScheduler.schedule(this@MhdSafetyApp, settings.notifyTime.first(), replace = false)
        }
    }
}
