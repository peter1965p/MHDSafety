package com.paeffgen.mhdsafety.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paeffgen.mhdsafety.BuildConfig
import com.paeffgen.mhdsafety.MhdSafetyApp
import com.paeffgen.mhdsafety.data.FoodItem
import com.paeffgen.mhdsafety.data.LookupResult
import com.paeffgen.mhdsafety.data.SignInResult
import com.paeffgen.mhdsafety.data.NotifyTime
import com.paeffgen.mhdsafety.data.ThemeMode
import com.paeffgen.mhdsafety.domain.Expiry
import com.paeffgen.mhdsafety.reminder.Notifications
import com.paeffgen.mhdsafety.reminder.ReminderScheduler
import com.paeffgen.mhdsafety.update.CheckResult
import com.paeffgen.mhdsafety.update.UpdateInfo
import com.paeffgen.mhdsafety.update.UpdateInstaller
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo, val needsPermission: Boolean = false) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class FoodViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MhdSafetyApp
    private val foods = app.foods
    private val settings = app.settings

    val items: StateFlow<List<FoodItem>?> = foods.items.state(null)

    /** Angemeldetes Konto für die gemeinsame Liste (null = nur auf dem Handy). */
    val cloudEmail: StateFlow<String?> = app.account.email
    private val _cloudMessage = MutableStateFlow<String?>(null)
    val cloudMessage: StateFlow<String?> = _cloudMessage.asStateFlow()
    private val _cloudBusy = MutableStateFlow(false)
    val cloudBusy: StateFlow<Boolean> = _cloudBusy.asStateFlow()
    val bufferDays: StateFlow<Int> = settings.bufferDays.state(Expiry.DEFAULT_BUFFER_DAYS)
    val deleteAfterDays: StateFlow<Int> = settings.deleteAfterDays.state(Expiry.DEFAULT_DELETE_AFTER_DAYS)
    val notifyTime: StateFlow<NotifyTime> = settings.notifyTime.state(NotifyTime.DEFAULT)
    val preAlarm: StateFlow<Boolean> = settings.preAlarm.state(true)
    val groupByCategory: StateFlow<Boolean> = settings.groupByCategory.state(true)
    val themeMode: StateFlow<ThemeMode> = settings.themeMode.state(ThemeMode.DARK)
    val autoUpdate: StateFlow<Boolean> = settings.autoUpdate.state(true)

    val currentVersion: String = BuildConfig.VERSION_NAME
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()
    private val _updateDialogVisible = MutableStateFlow(false)
    val updateDialogVisible: StateFlow<Boolean> = _updateDialogVisible.asStateFlow()

    init {
        // Beim Start still nachschauen (nicht in der Entwickler-Version).
        viewModelScope.launch {
            if (!BuildConfig.DEBUG && settings.autoUpdate.first()) checkForUpdates(silent = true)
        }
    }

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    suspend fun load(id: Long): FoodItem? = foods.getById(id)

    fun save(item: FoodItem) {
        viewModelScope.launch {
            foods.save(item)
            // Produkt merken, damit es beim nächsten Scan sofort erkannt wird.
            item.barcode?.let { app.products.remember(it, item.name, item.category) }
        }
    }

    suspend fun lookupProduct(barcode: String): LookupResult = app.products.lookup(barcode)

    fun delete(item: FoodItem) {
        viewModelScope.launch { foods.delete(item) }
    }

    fun setBufferDays(days: Int) {
        viewModelScope.launch { settings.setBufferDays(days) }
    }

    fun setDeleteAfterDays(days: Int) {
        viewModelScope.launch { settings.setDeleteAfterDays(days) }
    }

    fun setNotifyTime(time: NotifyTime) {
        viewModelScope.launch {
            settings.setNotifyTime(time)
            ReminderScheduler.schedule(app, time, replace = true)
        }
    }

    fun setPreAlarm(enabled: Boolean) {
        viewModelScope.launch { settings.setPreAlarm(enabled) }
    }

    fun setGroupByCategory(enabled: Boolean) {
        viewModelScope.launch { settings.setGroupByCategory(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setAutoUpdate(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoUpdate(enabled) }
    }

    /** @param silent beim automatischen Check keine Fehler oder "aktuell"-Meldung zeigen */
    fun checkForUpdates(silent: Boolean = false) {
        val state = _updateState.value
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            _updateState.value = when (val result = app.updates.check()) {
                is CheckResult.UpdateAvailable -> {
                    _updateDialogVisible.value = true
                    UpdateState.Available(result.info)
                }
                CheckResult.UpToDate -> if (silent) UpdateState.Idle else UpdateState.UpToDate
                is CheckResult.Failed -> if (silent) UpdateState.Idle else UpdateState.Failed(result.message)
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateDialogVisible.value = false
    }

    fun installUpdate() {
        val info = (_updateState.value as? UpdateState.Available)?.info ?: return
        if (!UpdateInstaller.canInstall(app)) {
            _updateState.value = UpdateState.Available(info, needsPermission = true)
            UpdateInstaller.openInstallPermissionSettings(app)
            return
        }
        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(info, 0f)
            try {
                val apk = app.updates.download(info) { p -> _updateState.value = UpdateState.Downloading(info, p) }
                _updateState.value = UpdateState.Available(info)
                UpdateInstaller.install(app, apk)
            } catch (e: IOException) {
                _updateState.value = UpdateState.Failed("Download fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}")
            }
        }
    }

    /** @param activityContext eine Activity, damit der Google-Konto-Dialog erscheinen kann */
    fun signIn(activityContext: Context) {
        if (_cloudBusy.value) return
        viewModelScope.launch {
            _cloudBusy.value = true
            _cloudMessage.value = when (val result = app.account.signIn(activityContext)) {
                is SignInResult.Success -> {
                    val moved = foods.moveLocalToCloud()
                    if (moved > 0) "Angemeldet. $moved Einträge vom Handy in die gemeinsame Liste übernommen." else null
                }
                is SignInResult.NotAllowed ->
                    "${result.email} ist nicht freigeschaltet. Die Adresse muss in Firebase unter „allowed“ eingetragen sein."
                SignInResult.Cancelled -> null
                is SignInResult.Failed -> result.message
            }
            _cloudBusy.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            app.account.signOut()
            _cloudMessage.value = "Abgemeldet. Die gemeinsame Liste bleibt in der Cloud, auf dem Handy wird wieder lokal gespeichert."
        }
    }

    fun sendTestNotification() = Notifications.showTest(app)
}
