package com.paeffgen.mhdsafety.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paeffgen.mhdsafety.R
import com.paeffgen.mhdsafety.data.Category
import com.paeffgen.mhdsafety.data.DateType
import com.paeffgen.mhdsafety.data.FoodItem
import com.paeffgen.mhdsafety.domain.Expiry
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FoodListScreen(
    viewModel: FoodViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val bufferDays by viewModel.bufferDays.collectAsStateWithLifecycle()
    val groupByCategory by viewModel.groupByCategory.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()

    fun consume(item: FoodItem) {
        viewModel.delete(item)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "„${item.name}“ entfernt",
                actionLabel = "Rückgängig",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.save(item)
        }
    }

    Box(Modifier.fillMaxSize()) {
    // Tux im Hintergrund, abgedunkelt, damit die Liste gut lesbar bleibt.
    Image(
        painter = painterResource(R.drawable.bg_tux),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(-0.3f, 0f),
        modifier = Modifier.fillMaxSize(),
    )
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)))

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("MHD Safety") },
                actions = {
                    IconButton(onClick = { viewModel.setGroupByCategory(!groupByCategory) }) {
                        if (groupByCategory) {
                            Icon(Icons.Default.DateRange, contentDescription = "Nach Datum sortieren")
                        } else {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Nach Kategorie gruppieren")
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Hinzufügen") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val list = items
        when {
            list == null -> Unit // lädt noch
            list.isEmpty() -> EmptyState(Modifier.padding(padding))
            else -> {
                // Innerhalb jeder Gruppe steht das Dringendste oben.
                val sorted = list.sortedBy { Expiry.alarmDate(it, bufferDays) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val row: @Composable (FoodItem) -> Unit = { item ->
                        FoodRow(
                            item = item,
                            bufferDays = bufferDays,
                            today = today,
                            showCategory = !groupByCategory,
                            onClick = { onEdit(item.id) },
                            onConsumed = { consume(item) },
                        )
                    }
                    if (groupByCategory) {
                        val groups = sorted.groupBy { it.category }
                        Category.entries.filter { it in groups }.forEach { category ->
                            val inCategory = groups.getValue(category)
                            stickyHeader(key = "header_${category.name}") {
                                CategoryHeader(category, inCategory.size)
                            }
                            items(inCategory, key = { it.id }) { row(it) }
                        }
                    } else {
                        items(sorted, key = { it.id }) { row(it) }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun CategoryHeader(category: Category, count: Int) {
    Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Text(
            "${category.emoji}  ${category.label}  ($count)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun FoodRow(
    item: FoodItem,
    bufferDays: Int,
    today: LocalDate,
    showCategory: Boolean,
    onClick: () -> Unit,
    onConsumed: () -> Unit,
) {
    val status = Expiry.status(item, bufferDays, today)
    val alarm = Expiry.alarmDate(item, bufferDays)
    val days = Expiry.daysUntilAlarm(item, bufferDays, today)
    val label = when (item.dateType) {
        DateType.BEST_BEFORE -> "MHD"
        DateType.USE_BY -> "Verbrauchen bis"
    }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .background(status.color(), CircleShape),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (if (showCategory) "${item.category.emoji} " else "") + item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "$label ${item.date.formatDe()}" +
                        (item.ownUseBy?.let { " · selbst bis ${it.formatDe()}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Alarm ${daysText(days)}" +
                        if (alarm != item.date) " (${alarm.formatDe()})" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = status.color(),
                )
            }
            IconButton(onClick = onConsumed) {
                Icon(Icons.Default.Check, contentDescription = "Verbraucht")
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Noch nichts eingetragen", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tippe auf „Hinzufügen“, um das erste Lebensmittel zu erfassen.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
