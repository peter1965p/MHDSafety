package com.paeffgen.mhdsafety.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.paeffgen.mhdsafety.data.Recipe
import com.paeffgen.mhdsafety.domain.Expiry
import com.paeffgen.mhdsafety.domain.IngredientMatch
import com.paeffgen.mhdsafety.domain.Quantity
import java.time.LocalDate

/** Ein gespeichertes Rezept mit Abgleich gegen den Bestand. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(viewModel: RecipeViewModel, recipeId: Long, onBack: () -> Unit) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val recipe = recipes?.firstOrNull { it.id == recipeId }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    RecipeScaffold(
        title = recipe?.name ?: "Rezept",
        onBack = onBack,
        actions = {
            if (recipe != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Rezept löschen")
                }
            }
        },
    ) {
        if (recipe != null) RecipeContent(viewModel, recipe) else if (recipes != null) CenterText("Rezept nicht gefunden.")
    }

    if (confirmDelete && recipe != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Rezept löschen?") },
            text = { Text("„${recipe.name}“ wird aus euren Rezepten entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(recipe)
                    onBack()
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

/** Ein Rezept, das gerade aus dem Netz geholt wird (geteilt oder Link eingefügt). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeImportScreen(viewModel: RecipeViewModel, onSaved: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.import.collectAsStateWithLifecycle()
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()

    RecipeScaffold(
        title = (state as? ImportState.Loaded)?.recipe?.name ?: "Rezept laden",
        onBack = {
            viewModel.clearImport()
            onBack()
        },
    ) {
        when (val s = state) {
            ImportState.Idle -> CenterText("Kein Rezept zum Laden.")
            is ImportState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text("Lade ${s.url.removePrefix("https://").substringBefore('/')} …")
                }
            }
            is ImportState.Failed -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(s.message, style = MaterialTheme.typography.bodyLarge)
                s.url?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            is ImportState.Loaded -> {
                val existing = recipes?.firstOrNull { it.url == s.recipe.url }
                RecipeContent(
                    viewModel = viewModel,
                    recipe = s.recipe,
                    header = {
                        if (existing != null) {
                            Text(
                                "Dieses Rezept ist schon gespeichert.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Button(onClick = {
                                viewModel.saveImported()
                                onSaved()
                            }) { Text("Rezept speichern") }
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = { actions() },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

@Composable
private fun RecipeContent(viewModel: RecipeViewModel, recipe: Recipe, header: @Composable () -> Unit = {}) {
    val foods by viewModel.foods.collectAsStateWithLifecycle()
    val bufferDays by viewModel.bufferDays.collectAsStateWithLifecycle()
    val check = remember(recipe, foods, bufferDays) { viewModel.check(recipe, foods, bufferDays) }
    val uriHandler = LocalUriHandler.current
    val today = LocalDate.now()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(recipe.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    summaryText(check) + (recipe.servings?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(recipe.url.removePrefix("https://").substringBefore('/'), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    header()
                    OutlinedButton(onClick = { uriHandler.openUri(recipe.url) }) { Text("Zubereitung") }
                }
                Text("Zutaten", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
        items(check.matches) { match ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    when (match) {
                        is IngredientMatch.Available -> if (match.short) "⚠️" else "✅"
                        is IngredientMatch.Pantry -> "🧂"
                        is IngredientMatch.Missing -> "❌"
                    },
                    modifier = Modifier.width(32.dp),
                )
                Column {
                    Text(match.line, style = MaterialTheme.typography.bodyLarge)
                    when (match) {
                        is IngredientMatch.Available -> {
                            match.items.take(2).forEach { item ->
                                val status = Expiry.status(item, bufferDays, today)
                                Text(
                                    item.name + (item.amount?.let { " · ${it.format()}" } ?: "") +
                                        " · Alarm ${daysText(Expiry.daysUntilAlarm(item, bufferDays, today))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = status.color(),
                                )
                            }
                            val needed = match.needed
                            val have = match.have
                            if (needed != null && have != null) {
                                Text(
                                    if (match.short) {
                                        "zu wenig: ${have.format()} da, ${Quantity(needed.amount - have.amount, needed.unit).format()} fehlen"
                                    } else {
                                        "reicht: ${have.format()} da, ${needed.format()} gebraucht"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (match.short) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        is IngredientMatch.Pantry -> Text("Grundvorrat", style = MaterialTheme.typography.bodySmall)
                        is IngredientMatch.Missing -> Text(
                            "nicht im Bestand",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
