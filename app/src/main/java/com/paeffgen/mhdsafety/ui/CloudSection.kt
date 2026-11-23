package com.paeffgen.mhdsafety.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Konto-Bereich in den Einstellungen: anmelden, registrieren, Einladung annehmen, Personen verwalten. */
@Composable
fun CloudSection(viewModel: FoodViewModel) {
    val email by viewModel.cloudEmail.collectAsStateWithLifecycle()
    val message by viewModel.cloudMessage.collectAsStateWithLifecycle()
    val busy by viewModel.cloudBusy.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    Text("Konto & gemeinsame Liste", style = MaterialTheme.typography.titleMedium)
    val signedIn = email
    if (signedIn == null) {
        SignInForm(viewModel, busy)
    } else {
        AccountPanel(viewModel, signedIn)
        syncStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun SignInForm(viewModel: FoodViewModel, busy: Boolean) {
    var mode by rememberSaveable { mutableStateOf(SignInMode.LOGIN) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var showServer by rememberSaveable { mutableStateOf(false) }

    Text(
        "Mit einem Konto ist der Bestand gesichert und lässt sich mit anderen teilen – " +
            "jede Person meldet sich mit eigener E-Mail und eigenem Passwort am selben Konto an. " +
            "Ohne Anmeldung bleibt alles nur auf diesem Handy.",
        style = MaterialTheme.typography.bodyMedium,
    )
    val modes = listOf(SignInMode.LOGIN to "Anmelden", SignInMode.REGISTER to "Neues Konto", SignInMode.INVITE to "Einladung")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, (m, label) ->
            SegmentedButton(
                selected = mode == m,
                onClick = { mode = m },
                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
            ) { Text(label) }
        }
    }
    OutlinedTextField(
        value = email, onValueChange = { email = it.trim() }, label = { Text("E-Mail") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
    )
    if (mode == SignInMode.INVITE) {
        OutlinedTextField(
            value = code, onValueChange = { code = it.uppercase() }, label = { Text("Einladungscode (XXXX-XXXX)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OutlinedTextField(
        value = password, onValueChange = { password = it },
        label = { Text(if (mode == SignInMode.LOGIN) "Passwort" else "Eigenes Passwort (mind. 10 Zeichen)") },
        singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = { viewModel.signIn(mode, email, password, code) },
            enabled = !busy && email.isNotBlank() && password.isNotBlank() && (mode != SignInMode.INVITE || code.isNotBlank()),
        ) {
            Text(
                when {
                    busy -> "Bitte warten …"
                    mode == SignInMode.LOGIN -> "Anmelden"
                    mode == SignInMode.REGISTER -> "Konto anlegen"
                    else -> "Einladung annehmen"
                },
            )
        }
        if (mode == SignInMode.LOGIN) {
            TextButton(onClick = { viewModel.requestPasswordReset(email) }, enabled = email.isNotBlank()) {
                Text("Passwort vergessen")
            }
        }
    }
    TextButton(onClick = { showServer = !showServer }) { Text(if (showServer) "Server ausblenden" else "Server …") }
    if (showServer) ServerField(viewModel)
}

@Composable
private fun ServerField(viewModel: FoodViewModel) {
    val current by viewModel.apiUrl.collectAsStateWithLifecycle()
    var url by rememberSaveable(current) { mutableStateOf(current) }
    OutlinedTextField(
        value = url, onValueChange = { url = it.trim() }, label = { Text("Server-Adresse") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(onClick = { viewModel.setApiUrl(url) }, enabled = url != current && url.startsWith("http")) {
        Text("Übernehmen")
    }
}

@Composable
private fun AccountPanel(viewModel: FoodViewModel, email: String) {
    val info by viewModel.accountInfo.collectAsStateWithLifecycle()
    val invitation by viewModel.invitation.collectAsStateWithLifecycle()
    var newPerson by rememberSaveable { mutableStateOf("") }
    var confirmRemove by rememberSaveable { mutableStateOf<Long?>(null) }
    val context = LocalContext.current

    LaunchedEffect(email) { viewModel.refreshAccount() }

    Text("Angemeldet als $email.", style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = viewModel::syncNow) { Text("Jetzt abgleichen") }
        OutlinedButton(onClick = viewModel::signOut) { Text("Abmelden") }
    }

    Text("Personen mit Zugriff auf dieses Konto", style = MaterialTheme.typography.titleSmall)
    info?.logins?.forEach { login ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(login.email, style = MaterialTheme.typography.bodyMedium)
                if (login.pending) Text("Einladung offen", style = MaterialTheme.typography.bodySmall)
            }
            if (login.pending) TextButton(onClick = { viewModel.reinvite(login.id) }) { Text("Neuer Code") }
            if ((info?.logins?.size ?: 0) > 1) {
                TextButton(onClick = { confirmRemove = login.id }) { Text("Entfernen") }
            }
        }
    }

    OutlinedTextField(
        value = newPerson, onValueChange = { newPerson = it.trim() }, label = { Text("E-Mail der weiteren Person") },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { viewModel.invitePerson(newPerson); newPerson = "" }, enabled = newPerson.contains("@")) {
        Text("Weitere Person hinzufügen")
    }

    invitation?.let { inv ->
        AlertDialog(
            onDismissRequest = viewModel::dismissInvitation,
            title = { Text("Einladung für ${inv.email}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Diesen Code an die Person weitergeben. Sie wählt in der App „Einladung“, gibt ihre E-Mail, den Code und ein eigenes Passwort ein. Der Code gilt 7 Tage.")
                    Text(inv.code, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = "Einladung zu MHD Safety: In der App unter Einstellungen → Konto → „Einladung“ " +
                        "mit ${inv.email} und dem Code ${inv.code} ein eigenes Passwort festlegen."
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                            "Einladung teilen",
                        ),
                    )
                }) { Text("Teilen") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissInvitation) { Text("Fertig") } },
        )
    }

    confirmRemove?.let { id ->
        val who = info?.logins?.firstOrNull { it.id == id }?.email ?: ""
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Anmeldung entfernen?") },
            text = { Text("$who kann danach nicht mehr auf diesen Bestand zugreifen.") },
            confirmButton = {
                TextButton(onClick = { viewModel.removeLogin(id); confirmRemove = null }) { Text("Entfernen") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Abbrechen") } },
        )
    }
}
