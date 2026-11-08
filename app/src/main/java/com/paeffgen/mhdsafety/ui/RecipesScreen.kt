package com.paeffgen.mhdsafety.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paeffgen.mhdsafety.domain.Expiry
import com.paeffgen.mhdsafety.domain.ExpiryStatus
import com.paeffgen.mhdsafety.domain.IngredientMatcher
import com.paeffgen.mhdsafety.domain.RecipeCheck
import java.time.LocalDate

/** Wie viele Lebensmittel als Suchvorschlag angeboten werden. */
private const val MAX_CHIPS = 12

/** Ohne bald Ablaufendes werden die ersten so vielen vorausgewählt. */
private const val DEFAULT_SELECTION = 3

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipesScreen(
    viewModel: RecipeViewModel,
    onOpen: (Long) -> Unit,
    onImport: (String) -> Unit,
    onBack: () -> Unit,
) {
    val ranked by viewModel.ranked.collectAsStateWithLifecycle()
    val foods by viewModel.foods.collectAsStateWithLifecycle()
    val bufferDays by viewModel.bufferDays.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var showPasteDialog by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()

    // Suchvorschläge aus dem Bestand: dringendstes zuerst, gleiche Begriffe nur einmal.
    val candidates = remember(foods, bufferDays) {
        foods.filter { IngredientMatcher.usable(it, bufferDays, today) }
            .sortedBy { Expiry.alarmDate(it, bufferDays) }
            .map { IngredientMatcher.searchTerm(it.name) to Expiry.status(it, bufferDays, today) }
            .distinctBy { it.first.lowercase() }
            .take(MAX_CHIPS)
    }
    // Vorausgewählt: alles, was bald abläuft – oder sonst die ersten paar.
    var selected by rememberSaveable(candidates.map { it.first }) {
        val urgent = candidates.filter { it.second != ExpiryStatus.OK }.map { it.first }
        mutableStateOf(urgent.ifEmpty { candidates.take(DEFAULT_SELECTION).map { it.first } }.toSet())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rezepte") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Was koche ich?", style = MaterialTheme.typography.titleMedium)
                        if (candidates.isEmpty()) {
                            Text(
                                "Noch nichts im Bestand. Trag Lebensmittel ein, dann gibt es hier passende Rezeptideen.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Text(
                                "Aus eurem Bestand, bald Ablaufendes ist schon ausgewählt:",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                candidates.forEach { (term, status) ->
                                    FilterChip(
                                        selected = term in selected,
                                        onClick = { selected = if (term in selected) selected - term else selected + term },
                                        label = {
                                            Text(term, color = if (status != ExpiryStatus.OK) status.color() else MaterialTheme.colorScheme.onSurface)
                                        },
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    val terms = candidates.map { it.first }.filter { it in selected }
                                    uriHandler.openUri(RecipeViewModel.searchUrl(terms))
                                },
                                enabled = selected.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null)
                                Text("  Rezepte im Netz suchen")
                            }
                        }
                        Text(
                            "Passendes Rezept gefunden? Im Browser auf „Teilen“ → MHD Safety tippen. " +
                                "Die App liest die Zutaten und vergleicht sie mit eurem Bestand.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = { showPasteDialog = true }) { Text("Link einfügen") }
                    }
                }
            }

            val list = ranked
            when {
                list == null -> Unit
                list.isEmpty() -> item {
                    Text(
                        "Noch keine Rezepte gespeichert.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                else -> {
                    item {
                        Text(
                            "Eure Rezepte – passend zum Bestand sortiert",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(list, key = { it.recipe.id }) { check ->
                        RecipeRow(check, onClick = { onOpen(check.recipe.id) })
                    }
                }
            }
        }
    }

    if (showPasteDialog) {
        var link by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text("Rezept-Link") },
            text = {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    placeholder = { Text("https://www.chefkoch.de/rezepte/…") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPasteDialog = false
                        onImport(link)
                    },
                    enabled = link.isNotBlank(),
                ) { Text("Laden") }
            },
            dismissButton = { TextButton(onClick = { showPasteDialog = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun RecipeRow(check: RecipeCheck, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(check.recipe.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(summaryText(check), style = MaterialTheme.typography.bodyMedium)
                if (check.urgentCount > 0) {
                    Text(
                        "verbraucht ${check.urgentCount} bald Ablaufende${if (check.urgentCount == 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpiryStatus.SOON.color(),
                    )
                }
            }
            Text(if (check.missingCount + check.shortCount == 0) "✅" else "🛒", style = MaterialTheme.typography.titleLarge)
        }
    }
}

fun summaryText(check: RecipeCheck): String {
    if (check.missingCount + check.shortCount == 0) return "Alles da (${check.total} Zutaten)"
    val parts = mutableListOf("${check.haveCount} von ${check.total} da")
    if (check.shortCount > 0) parts += "${check.shortCount} zu wenig"
    when (check.missingCount) {
        0 -> Unit
        1 -> parts += "1 fehlt"
        else -> parts += "${check.missingCount} fehlen"
    }
    return parts.joinToString(", ")
}
