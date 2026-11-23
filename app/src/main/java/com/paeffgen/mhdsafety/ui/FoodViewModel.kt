package com.paeffgen.mhdsafety.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paeffgen.mhdsafety.BuildConfig
import com.paeffgen.mhdsafety.MhdSafetyApp
import com.paeffgen.mhdsafety.data.FoodItem
import com.paeffgen.mhdsafety.data.LookupResult
import com.paeffgen.mhdsafety.data.PriceLookup
import com.paeffgen.mhdsafety.data.AccountInfo
import com.paeffgen.mhdsafety.data.Invitation
import com.paeffgen.mhdsafety.data.SessionExpired
import com.paeffgen.mhdsafety.data.SignInResult
import com.paeffgen.mhdsafety.data.NotifyTime
import com.paeffgen.mhdsafety.data.ThemeMode
import com.paeffgen.mhdsafety.domain.Expiry
import com.paeffgen.mhdsafety.domain.Quantity
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
    private val _accountInfo = MutableStateFlow<AccountInfo?>(null)
    /** Konto mit allen Anmeldungen (null = noch nicht geladen oder nicht angemeldet). */
    val accountInfo: StateFlow<AccountInfo?> = _accountInfo.asStateFlow()
    private val _invitation = MutableStateFlow<Invitation?>(null)
    /** Gerade erzeugter Einladungscode, zum Weitergeben an die neue Person. */
    val invitation: StateFlow<Invitation?> = _invitation.asStateFlow()
    private val _apiUrl = MutableStateFlow(app.authStore.apiUrl)
    val apiUrl: StateFlow<String> = _apiUrl.asStateFlow()
    val syncStatus: StateFlow<String?> = app.sync.status
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

    /** @param packageSize Packungsgröße vom Scan, wird zum Barcode gemerkt */
    fun save(item: FoodItem, packageSize: Quantity? = null) {
        viewModelScope.launch {
            foods.save(item)
            // Produkt merken, damit es beim nächsten Scan sofort erkannt wird.
            item.barcode?.let { app.products.remember(it, item.name, item.category, packageSize) }
        }
    }

    suspend fun lookupProduct(barcode: String): LookupResult = app.products.lookup(barcode)

    suspend fun lookupPrice(barcode: String): PriceLookup = app.prices.lookup(barcode)

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

    /** Anmelden, Registrieren oder Einladung annehmen – danach Daten übernehmen und abgleichen. */
    fun signIn(mode: SignInMode, email: String, password: String, code: String = "") {
        if (_cloudBusy.value) return
        viewModelScope.launch {
            _cloudBusy.value = true
            val result = when (mode) {
                SignInMode.LOGIN -> app.account.login(email, password)
                SignInMode.REGISTER -> app.account.register(email, password)
                SignInMode.INVITE -> app.account.acceptInvite(email, code, password)
            }
            _cloudMessage.value = when (result) {
                is SignInResult.Success -> {
                    val moved = app.sync.adoptLocalData(result.accountId)
                    app.sync.syncNow()
                    refreshAccount()
                    if (moved > 0) "Angemeldet. $moved Einträge vom Handy ins Konto übernommen." else "Angemeldet."
                }
                is SignInResult.Failed -> result.message
            }
            _cloudBusy.value = false
        }
    }

    fun requestPasswordReset(email: String) {
        viewModelScope.launch { _cloudMessage.value = app.account.requestPasswordReset(email) }
    }

    fun signOut() {
        viewModelScope.launch {
            app.sync.syncNow()
            app.account.signOut()
            app.sync.clearLocal()
            _accountInfo.value = null
            _invitation.value = null
            _cloudMessage.value = "Abgemeldet. Der Bestand bleibt im Konto und ist nach der nächsten Anmeldung wieder da."
        }
    }

    fun syncNow() {
        viewModelScope.launch { app.sync.syncNow() }
    }

    /** Anmeldungen des Kontos neu laden. */
    fun refreshAccount() {
        viewModelScope.launch {
            _accountInfo.value = cloudCall { app.account.info() } ?: _accountInfo.value
        }
    }

    fun invitePerson(email: String) {
        viewModelScope.launch {
            cloudCall { app.account.invite(email) }?.fold(
                onSuccess = { _invitation.value = it; refreshAccount() },
                onFailure = { _cloudMessage.value = it.message },
            )
        }
    }

    fun reinvite(loginId: Long) {
        viewModelScope.launch {
            cloudCall { app.account.reinvite(loginId) }?.fold(
                onSuccess = { _invitation.value = it },
                onFailure = { _cloudMessage.value = it.message },
            )
        }
    }

    fun removeLogin(loginId: Long) {
        viewModelScope.launch {
            cloudCall { app.account.removeLogin(loginId) }?.fold(
                onSuccess = { refreshAccount() },
                onFailure = { _cloudMessage.value = it.message },
            )
        }
    }

    fun dismissInvitation() {
        _invitation.value = null
    }

    fun setApiUrl(url: String) {
        if (url.isBlank()) return
        app.authStore.apiUrl = url
        _apiUrl.value = app.authStore.apiUrl
    }

    /** Fängt Netz- und Sitzungsfehler ab und zeigt sie als Meldung. */
    private suspend fun <T> cloudCall(block: suspend () -> T): T? = try {
        block()
    } catch (e: SessionExpired) {
        _cloudMessage.value = e.message
        null
    } catch (e: IOException) {
        _cloudMessage.value = "Server nicht erreichbar: ${e.message ?: ""}".trim()
        null
    }

    fun sendTestNotification() = Notifications.showTest(app)
}

enum class SignInMode { LOGIN, REGISTER, INVITE }
