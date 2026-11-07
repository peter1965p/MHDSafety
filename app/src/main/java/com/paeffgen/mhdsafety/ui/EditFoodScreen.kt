package com.paeffgen.mhdsafety.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paeffgen.mhdsafety.R
import com.paeffgen.mhdsafety.data.Category
import com.paeffgen.mhdsafety.data.DateType
import com.paeffgen.mhdsafety.data.FoodItem
import com.paeffgen.mhdsafety.data.LookupResult
import com.paeffgen.mhdsafety.domain.Expiry
import com.paeffgen.mhdsafety.domain.ParsedDate
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditFoodScreen(
    viewModel: FoodViewModel,
    itemId: Long?,
    scannedBarcode: String?,
    scannedDate: ParsedDate?,
    onScanConsumed: () -> Unit,
    onScanBarcode: () -> Unit,
    onScanDate: () -> Unit,
    onDone: () -> Unit,
) {
    val bufferDays by viewModel.bufferDays.collectAsStateWithLifecycle()

    var loaded by rememberSaveable { mutableStateOf(itemId == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var dateEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var dateType by rememberSaveable { mutableStateOf(DateType.BEST_BEFORE) }
    var barcode by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf(Category.OTHER) }
    // Hat der Nutzer selbst gewählt? Dann nicht mehr automatisch umstellen.
    var dateTypeTouched by rememberSaveable { mutableStateOf(false) }
    var categoryTouched by rememberSaveable { mutableStateOf(false) }
    var existing by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pendingLookup by rememberSaveable { mutableStateOf<String?>(null) }
    var lookupMessage by rememberSaveable { mutableStateOf<String?>(null) }

    fun selectCategory(c: Category) {
        category = c
        if (!dateTypeTouched) dateType = if (c.usuallyUseBy) DateType.USE_BY else DateType.BEST_BEFORE
    }

    LaunchedEffect(itemId) {
        if (itemId != null && !loaded) {
            viewModel.load(itemId)?.let {
                name = it.name
                dateEpochDay = it.date.toEpochDay()
                dateType = it.dateType
                barcode = it.barcode
                category = it.category
                existing = true
                dateTypeTouched = true
                categoryTouched = true
            }
            loaded = true
        }
    }

    // Ergebnisse vom Scan-Bildschirm übernehmen.
    LaunchedEffect(scannedBarcode, scannedDate) {
        if (scannedBarcode == null && scannedDate == null) return@LaunchedEffect
        scannedBarcode?.let {
            barcode = it
            pendingLookup = it
        }
        scannedDate?.let {
            dateEpochDay = it.date.toEpochDay()
            if (it.useBy) {
                dateType = DateType.USE_BY
                dateTypeTouched = true
            }
        }
        onScanConsumed()
    }

    LaunchedEffect(pendingLookup) {
        val code = pendingLookup ?: return@LaunchedEffect
        lookupMessage = null
        lookupMessage = when (val result = viewModel.lookupProduct(code)) {
            is LookupResult.Found -> {
                if (name.isBlank()) name = result.name
                if (!categoryTouched && result.category != null) selectCategory(result.category)
                "Gefunden: ${result.name}"
            }
            LookupResult.NotFound -> "Produkt unbekannt, bitte Namen eintippen."
            LookupResult.NetworkError -> "Keine Verbindung, bitte Namen eintippen."
        }
        pendingLookup = null
    }

    val date = dateEpochDay?.let(LocalDate::ofEpochDay)
    val canSave = name.isNotBlank() && date != null

    fun buildItem() = FoodItem(
        id = itemId ?: 0,
        name = name.trim(),
        date = date!!,
        dateType = dateType,
        barcode = barcode,
        category = category,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing) "Bearbeiten" else "Neues Lebensmittel") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (existing) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Löschen")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // --- Produkt ---
            FilledTonalButton(onClick = onScanBarcode, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_barcode), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Barcode scannen")
            }
            if (pendingLookup != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Suche Produkt …", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                lookupMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                supportingText = barcode?.let { { Text("Barcode $it") } },
                modifier = Modifier.fillMaxWidth(),
            )

            // --- Kategorie ---
            Text("Kategorie", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = {
                            categoryTouched = true
                            selectCategory(c)
                        },
                        label = { Text("${c.emoji} ${c.label}") },
                    )
                }
            }

            // --- Datum ---
            Text("Was steht auf der Packung?", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = dateType == DateType.BEST_BEFORE,
                    onClick = { dateType = DateType.BEST_BEFORE; dateTypeTouched = true },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Mind. haltbar bis") }
                SegmentedButton(
                    selected = dateType == DateType.USE_BY,
                    onClick = { dateType = DateType.USE_BY; dateTypeTouched = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Zu verbrauchen bis") }
            }

            Text(
                date?.formatDe() ?: "Noch kein Datum",
                style = MaterialTheme.typography.headlineSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onScanDate, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_camera), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scannen")
                }
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Kalender")
                }
            }

            if (date != null) {
                val preview = FoodItem(name = name, date = date, dateType = dateType)
                val alarm = Expiry.alarmDate(preview, bufferDays)
                val hint = when (dateType) {
                    DateType.BEST_BEFORE ->
                        "Alarm am ${alarm.formatDe()} (MHD + $bufferDays Tage Puffer)."
                    DateType.USE_BY ->
                        "Alarm am ${alarm.formatDe()}. Beim Verbrauchsdatum gibt es keinen Puffer: " +
                            "danach nicht mehr essen."
                }
                Text(hint, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    viewModel.save(buildItem())
                    onDone()
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Speichern") }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dateEpochDay = utcMillisToLocalDate(it).toEpochDay() }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") }
            },
        ) { DatePicker(state = state) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Löschen?") },
            text = { Text("„$name“ wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(buildItem())
                    onDone()
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
            },
        )
    }
}
