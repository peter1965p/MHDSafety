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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paeffgen.mhdsafety.R
import com.paeffgen.mhdsafety.data.Category
import com.paeffgen.mhdsafety.data.DateType
import com.paeffgen.mhdsafety.data.FoodItem
import com.paeffgen.mhdsafety.data.LookupResult
import com.paeffgen.mhdsafety.data.PriceLookup
import com.paeffgen.mhdsafety.domain.Expiry
import com.paeffgen.mhdsafety.domain.ManualDate
import com.paeffgen.mhdsafety.domain.ParsedDate
import com.paeffgen.mhdsafety.domain.Prices
import com.paeffgen.mhdsafety.domain.Quantity
import com.paeffgen.mhdsafety.domain.QuantityUnit
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
    // Was im Datumsfeld steht (getippt oder aus Scan/Kalender übernommen).
    var dateText by rememberSaveable { mutableStateOf("") }
    var ownUseByEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var dateType by rememberSaveable { mutableStateOf(DateType.BEST_BEFORE) }
    var barcode by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf(Category.OTHER) }
    // Hat der Nutzer selbst gewählt? Dann nicht mehr automatisch umstellen.
    var dateTypeTouched by rememberSaveable { mutableStateOf(false) }
    var categoryTouched by rememberSaveable { mutableStateOf(false) }
    var existing by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showOwnDatePicker by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pendingLookup by rememberSaveable { mutableStateOf<String?>(null) }
    var lookupMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var quantityText by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(QuantityUnit.GRAM) }
    // Packungsgröße vom Scan, wird beim Speichern zum Barcode gemerkt.
    var packageAmount by rememberSaveable { mutableStateOf<Double?>(null) }
    var packageUnit by rememberSaveable { mutableStateOf<QuantityUnit?>(null) }

    fun selectCategory(c: Category) {
        category = c
        if (!dateTypeTouched) dateType = if (c.usuallyUseBy) DateType.USE_BY else DateType.BEST_BEFORE
    }

    LaunchedEffect(itemId) {
        if (itemId != null && !loaded) {
            viewModel.load(itemId)?.let {
                name = it.name
                dateEpochDay = it.date.toEpochDay()
                dateText = it.date.formatDe()
                ownUseByEpochDay = it.ownUseBy?.toEpochDay()
                dateType = it.dateType
                barcode = it.barcode
                category = it.category
                quantityText = it.quantity?.let(Quantity::formatNumber).orEmpty()
                unit = it.unit ?: QuantityUnit.GRAM
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
            dateText = it.date.formatDe()
            if (it.useBy) {
                dateType = DateType.USE_BY
                dateTypeTouched = true
            }
        }
        onScanConsumed()
    }

    // Gemeldete Preise nachladen, sobald ein Barcode bekannt ist – still im Hintergrund.
    var priceLookup by remember { mutableStateOf<PriceLookup?>(null) }
    LaunchedEffect(barcode) {
        priceLookup = null
        barcode?.let { priceLookup = viewModel.lookupPrice(it) }
    }

    LaunchedEffect(pendingLookup) {
        val code = pendingLookup ?: return@LaunchedEffect
        lookupMessage = null
        lookupMessage = when (val result = viewModel.lookupProduct(code)) {
            is LookupResult.Found -> {
                if (name.isBlank()) name = result.name
                if (!categoryTouched && result.category != null) selectCategory(result.category)
                result.packageSize?.let { size ->
                    packageAmount = size.amount
                    packageUnit = size.unit
                    if (quantityText.isBlank()) {
                        quantityText = Quantity.formatNumber(size.amount)
                        unit = size.unit
                    }
                }
                "Gefunden: ${result.name}" + (result.packageSize?.let { " (${it.format()})" } ?: "")
            }
            LookupResult.NotFound -> "Produkt unbekannt, bitte Namen eintippen."
            LookupResult.NetworkError -> "Keine Verbindung, bitte Namen eintippen."
        }
        pendingLookup = null
    }

    val date = dateEpochDay?.let(LocalDate::ofEpochDay)
    val ownUseBy = ownUseByEpochDay?.let(LocalDate::ofEpochDay)
    val quantity = Quantity.parseNumber(quantityText)
    val quantityInvalid = quantityText.isNotBlank() && quantity == null
    val canSave = name.isNotBlank() && date != null && !quantityInvalid

    fun buildItem() = FoodItem(
        id = itemId ?: 0,
        name = name.trim(),
        date = date!!,
        dateType = dateType,
        barcode = barcode,
        category = category,
        ownUseBy = ownUseBy,
        quantity = quantity,
        unit = if (quantity != null) unit else null,
    )

    fun packageSize(): Quantity? {
        val a = packageAmount
        val u = packageUnit
        return if (a != null && u != null) Quantity(a, u) else null
    }

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
            // --- Produkt: von Hand tippen, Barcode nur bei Bedarf ---
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                trailingIcon = {
                    IconButton(onClick = onScanBarcode) {
                        Icon(painterResource(R.drawable.ic_barcode), contentDescription = "Barcode scannen")
                    }
                },
                supportingText = barcode?.let { { Text("Barcode $it") } },
                modifier = Modifier.fillMaxWidth(),
            )
            if (pendingLookup != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Suche Produkt …", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                lookupMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            priceLookup?.let { PriceInfo(it) }

            // --- Menge: beim Scan die Packungsgröße, später was noch übrig ist ---
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text(if (existing) "Noch da" else "Menge") },
                    placeholder = { Text("optional") },
                    singleLine = true,
                    isError = quantityInvalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                SingleChoiceSegmentedButtonRow(Modifier.weight(1.3f)) {
                    QuantityUnit.entries.forEachIndexed { index, u ->
                        SegmentedButton(
                            selected = unit == u,
                            onClick = { unit = u },
                            shape = SegmentedButtonDefaults.itemShape(index, QuantityUnit.entries.size),
                            icon = {},
                        ) { Text(u.label) }
                    }
                }
            }
            if (existing && quantity != null) {
                TextButton(onClick = { quantityText = Quantity.formatNumber(quantity / 2) }) {
                    Text("Hälfte verbraucht")
                }
            }


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

            val dateTextInvalid = dateText.isNotBlank() && date == null
            OutlinedTextField(
                value = dateText,
                onValueChange = { text ->
                    dateText = text
                    dateEpochDay = ManualDate.parse(text, LocalDate.now())?.toEpochDay()
                },
                label = { Text("Datum") },
                placeholder = { Text("z. B. 09.11. oder 0911") },
                singleLine = true,
                isError = dateTextInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Kalender")
                        }
                        IconButton(onClick = onScanDate) {
                            Icon(painterResource(R.drawable.ic_camera), contentDescription = "Datum scannen")
                        }
                    }
                },
                supportingText = {
                    when {
                        dateTextInvalid -> Text("Datum nicht erkannt")
                        date != null && dateText != date.formatDe() -> Text("= ${date.formatDe()}")
                        else -> Unit
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // --- Eigenes Verbrauchsdatum, z. B. nach dem Öffnen ---
            if (date != null) {
                Text("Selbst verbrauchen bis (optional)", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Z. B. wenn die Packung offen ist. Darf vor dem Packungsdatum liegen.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = { showOwnDatePicker = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.DateRange, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(ownUseBy?.formatDe() ?: "Datum wählen")
                    }
                    if (ownUseBy != null) {
                        TextButton(onClick = { ownUseByEpochDay = null }) { Text("Entfernen") }
                    }
                }
            }

            if (date != null) {
                val preview = FoodItem(name = name, date = date, dateType = dateType, ownUseBy = ownUseBy)
                val alarm = Expiry.alarmDate(preview, bufferDays)
                val hint = when {
                    ownUseBy != null && dateType == DateType.USE_BY && ownUseBy.isAfter(date) ->
                        "Alarm am ${alarm.formatDe()}. Das eigene Datum liegt hinter dem " +
                            "Verbrauchsdatum der Packung, deshalb zählt das Packungsdatum."
                    ownUseBy != null ->
                        "Alarm am ${alarm.formatDe()} (eigenes Verbrauchsdatum, kein Puffer)."
                    dateType == DateType.BEST_BEFORE ->
                        "Alarm am ${alarm.formatDe()} (MHD + $bufferDays Tage Puffer)."
                    else ->
                        "Alarm am ${alarm.formatDe()}. Beim Verbrauchsdatum gibt es keinen Puffer: " +
                            "danach nicht mehr essen."
                }
                Text(hint, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    viewModel.save(buildItem(), packageSize())
                    onDone()
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Speichern") }
        }
    }

    if (showDatePicker) {
        DatePickDialog(
            initial = date ?: LocalDate.now(),
            onPicked = {
                dateEpochDay = it.toEpochDay()
                dateText = it.formatDe()
            },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showOwnDatePicker) {
        DatePickDialog(
            // Vorschlag: heute, aber nie hinter dem Packungsdatum.
            initial = ownUseBy ?: minOf(LocalDate.now(), date ?: LocalDate.now()),
            onPicked = { ownUseByEpochDay = it.toEpochDay() },
            onDismiss = { showOwnDatePicker = false },
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickDialog(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toUtcMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(utcMillisToLocalDate(it)) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    ) { DatePicker(state = state) }
}

/** „Zuletzt gesehen“ und „am günstigsten“ aus Open Prices. */
@Composable
private fun PriceInfo(lookup: PriceLookup) {
    val today = LocalDate.now()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (lookup) {
        is PriceLookup.Known -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "💶 Zuletzt gesehen: ${Prices.describe(lookup.hint.latest, today)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            lookup.hint.cheapest?.let {
                Text("Am günstigsten: ${Prices.describe(it, today)}", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Gemeldet von der Open-Prices-Community, ${lookup.hint.count} " +
                    (if (lookup.hint.count == 1) "Preis" else "Preise") + " im letzten Jahr",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
        PriceLookup.Unknown -> Text(
            "💶 Noch kein Preis aus dem letzten Jahr gemeldet.",
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
        PriceLookup.Offline -> Unit
    }
}
