package com.paeffgen.mhdsafety.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UpdateDialog(
    state: UpdateState,
    currentVersion: String,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val downloading = state is UpdateState.Downloading
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = {
            Text(
                when (state) {
                    is UpdateState.Available -> "Update auf ${state.info.version}"
                    is UpdateState.Downloading -> "Lade ${state.info.version} …"
                    is UpdateState.Failed -> "Update fehlgeschlagen"
                    else -> "Update"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    is UpdateState.Available -> {
                        Text("Installiert ist $currentVersion.", style = MaterialTheme.typography.bodyMedium)
                        if (state.info.notes.isNotBlank()) {
                            Text("Was ist neu:", style = MaterialTheme.typography.labelLarge)
                            Text(
                                state.info.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                            )
                        }
                        if (state.needsPermission) {
                            Text(
                                "Bitte erlaube MHD Safety, Apps zu installieren, und tippe dann nochmal auf „Installieren“.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    is UpdateState.Downloading -> {
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                        Text("${(state.progress * 100).toInt()} %", style = MaterialTheme.typography.bodyMedium)
                    }
                    is UpdateState.Failed -> Text(state.message)
                    else -> Unit
                }
            }
        },
        confirmButton = {
            if (state is UpdateState.Available) {
                TextButton(onClick = onInstall) { Text("Installieren") }
            }
        },
        dismissButton = {
            if (!downloading) {
                TextButton(onClick = onDismiss) {
                    Text(if (state is UpdateState.Available) "Später" else "OK")
                }
            }
        },
    )
}
