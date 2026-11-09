package com.paeffgen.mhdsafety

import android.app.Application
import com.paeffgen.mhdsafety.data.AppDatabase
import com.paeffgen.mhdsafety.data.AutoDelete
import com.paeffgen.mhdsafety.data.CloudAccount
import com.paeffgen.mhdsafety.data.CloudFoodStore
import com.paeffgen.mhdsafety.data.CloudRecipeStore
import com.paeffgen.mhdsafety.data.PriceRepository
import com.paeffgen.mhdsafety.data.FoodRepository
import com.paeffgen.mhdsafety.data.RecipeFetcher
import com.paeffgen.mhdsafety.data.RecipeRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
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
    val prices by lazy { PriceRepository() }
    val updates by lazy { UpdateRepository(this) }
    val account by lazy { CloudAccount(this) }
    val foods by lazy { FoodRepository(database.foodDao(), CloudFoodStore(), account) }
    val recipes by lazy { RecipeRepository(database.recipeDao(), CloudRecipeStore(), account) }
    val recipeFetcher by lazy { RecipeFetcher() }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Absturzberichte nur von der echten App, nicht von der Test-Version.
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        Notifications.createChannel(this)
        UpdateWorker.schedule(this)
        appScope.launch {
            runCatching { AutoDelete.run(foods, settings) }
        }
        appScope.launch {
            ReminderScheduler.schedule(this@MhdSafetyApp, settings.notifyTime.first(), replace = false)
        }
    }
}
