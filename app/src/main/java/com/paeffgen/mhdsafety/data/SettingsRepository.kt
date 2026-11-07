package com.paeffgen.mhdsafety.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paeffgen.mhdsafety.domain.Expiry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val bufferDaysKey = intPreferencesKey("buffer_days")
    private val notifyHourKey = intPreferencesKey("notify_hour")
    private val notifyMinuteKey = intPreferencesKey("notify_minute")
    private val preAlarmKey = booleanPreferencesKey("pre_alarm")
    private val groupByCategoryKey = booleanPreferencesKey("group_by_category")
    private val lastNotifiedKey = longPreferencesKey("last_notified_epoch_day")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val autoUpdateKey = booleanPreferencesKey("auto_update")
    private val notifiedUpdateKey = stringPreferencesKey("notified_update_version")

    /** Tage, die nach dem MHD noch draufgerechnet werden, bevor Alarm kommt. */
    val bufferDays: Flow<Int> = context.dataStore.data.map {
        it[bufferDaysKey] ?: Expiry.DEFAULT_BUFFER_DAYS
    }

    /** Uhrzeit der täglichen Erinnerung als Minuten seit Mitternacht. */
    val notifyTime: Flow<NotifyTime> = context.dataStore.data.map {
        NotifyTime(it[notifyHourKey] ?: NotifyTime.DEFAULT.hour, it[notifyMinuteKey] ?: NotifyTime.DEFAULT.minute)
    }

    /** Auch einen Tag vor dem Alarm schon erinnern. */
    val preAlarm: Flow<Boolean> = context.dataStore.data.map { it[preAlarmKey] ?: true }

    val groupByCategory: Flow<Boolean> = context.dataStore.data.map { it[groupByCategoryKey] ?: true }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeModeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK
    }

    /** Automatisch auf GitHub nach neuen Versionen suchen. */
    val autoUpdate: Flow<Boolean> = context.dataStore.data.map { it[autoUpdateKey] ?: true }

    /** Für welche Version schon eine Update-Benachrichtigung kam. */
    val notifiedUpdateVersion: Flow<String?> = context.dataStore.data.map { it[notifiedUpdateKey] }

    val lastNotified: Flow<LocalDate?> = context.dataStore.data.map { prefs ->
        prefs[lastNotifiedKey]?.let(LocalDate::ofEpochDay)
    }

    suspend fun setBufferDays(days: Int) {
        context.dataStore.edit { it[bufferDaysKey] = days.coerceIn(0, Expiry.MAX_BUFFER_DAYS) }
    }

    suspend fun setNotifyTime(time: NotifyTime) {
        context.dataStore.edit {
            it[notifyHourKey] = time.hour
            it[notifyMinuteKey] = time.minute
        }
    }

    suspend fun setPreAlarm(enabled: Boolean) {
        context.dataStore.edit { it[preAlarmKey] = enabled }
    }

    suspend fun setGroupByCategory(enabled: Boolean) {
        context.dataStore.edit { it[groupByCategoryKey] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setAutoUpdate(enabled: Boolean) {
        context.dataStore.edit { it[autoUpdateKey] = enabled }
    }

    suspend fun setNotifiedUpdateVersion(version: String) {
        context.dataStore.edit { it[notifiedUpdateKey] = version }
    }

    suspend fun setLastNotified(date: LocalDate) {
        context.dataStore.edit { it[lastNotifiedKey] = date.toEpochDay() }
    }
}

enum class ThemeMode(val label: String) {
    DARK("Dunkel"),
    LIGHT("Hell"),
    SYSTEM("Wie System"),
}

data class NotifyTime(val hour: Int, val minute: Int) {
    override fun toString(): String = "%02d:%02d".format(hour, minute)

    companion object {
        val DEFAULT = NotifyTime(9, 0)
    }
}
