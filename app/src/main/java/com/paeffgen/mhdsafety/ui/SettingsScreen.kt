package com.paeffgen.mhdsafety.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paeffgen.mhdsafety.data.NotifyTime
import com.paeffgen.mhdsafety.data.ThemeMode
import com.paeffgen.mhdsafety.domain.Expiry
import com.paeffgen.mhdsafety.reminder.Notifications
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: FoodViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val bufferDays by viewModel.bufferDays.collectAsStateWithLifecycle()
    val notifyTime by viewModel.notifyTime.collectAsStateWithLifecycle()
    val preAlarm by viewModel.preAlarm.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val autoUpdate by viewModel.autoUpdate.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    // Neu prüfen, wenn man aus den System-Einstellungen zurückkommt.
    var notificationsOn by remember { mutableStateOf(Notifications.canNotify(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsOn = Notifications.canNotify(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Darstellung", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    ) { Text(mode.label) }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Puffer nach MHD", style = MaterialTheme.typography.titleMedium)
            Text(
                "Alarm kommt $bufferDays ${if (bufferDays == 1) "Tag" else "Tage"} nach dem " +
                    "Mindesthaltbarkeitsdatum.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = bufferDays.toFloat(),
                onValueChange = { viewModel.setBufferDays(it.roundToInt()) },
                valueRange = 0f..Expiry.MAX_BUFFER_DAYS.toFloat(),
                steps = Expiry.MAX_BUFFER_DAYS - 1,
            )
            Text(
                "Gilt nicht für „Zu verbrauchen bis“. Dort kommt der Alarm immer am Datum selbst.",
                style = MaterialTheme.typography.bodySmall,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Benachrichtigung", style = MaterialTheme.typography.titleMedium)
            if (!notificationsOn) {
                Text(
                    "Benachrichtigungen sind für diese App ausgeschaltet. Ohne sie gibt es keinen Alarm.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }) { Text("Benachrichtigungen einschalten") }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Uhrzeit", style = MaterialTheme.typography.bodyLarge)
                    Text("Einmal täglich", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { showTimePicker = true }) { Text("$notifyTime Uhr") }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Vorwarnung", style = MaterialTheme.typography.bodyLarge)
                    Text("Schon einen Tag vor dem Alarm erinnern", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = preAlarm, onCheckedChange = viewModel::setPreAlarm)
            }

            OutlinedButton(
                onClick = viewModel::sendTestNotification,
                enabled = notificationsOn,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Test-Benachrichtigung senden") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Über die App", style = MaterialTheme.typography.titleMedium)
            Text("Version ${viewModel.currentVersion}", style = MaterialTheme.typography.bodyLarge)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Automatisch nach Updates suchen", style = MaterialTheme.typography.bodyLarge)
                    Text("Beim Start und einmal täglich auf GitHub", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = autoUpdate, onCheckedChange = viewModel::setAutoUpdate)
            }

            OutlinedButton(
                onClick = { viewModel.checkForUpdates() },
                enabled = updateState !is UpdateState.Checking && updateState !is UpdateState.Downloading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Jetzt nach Updates suchen") }
            when (val s = updateState) {
                UpdateState.Checking -> Text("Suche …", style = MaterialTheme.typography.bodyMedium)
                UpdateState.UpToDate -> Text("Du hast die neueste Version. 👍", style = MaterialTheme.typography.bodyMedium)
                is UpdateState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                is UpdateState.Available -> Text("Version ${s.info.version} ist verfügbar.", style = MaterialTheme.typography.bodyMedium)
                else -> Unit
            }

            Text(
                "Produktdaten: Open Food Facts (ODbL) · Tux: Larry Ewing · Hintergrundbild mit Gemini erstellt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(
            initialHour = notifyTime.hour,
            initialMinute = notifyTime.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setNotifyTime(NotifyTime(state.hour, state.minute))
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Abbrechen") }
            },
            text = { TimePicker(state = state) },
        )
    }
}
