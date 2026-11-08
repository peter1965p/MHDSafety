package com.paeffgen.mhdsafety.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paeffgen.mhdsafety.MhdSafetyApp
import com.paeffgen.mhdsafety.data.FetchResult
import com.paeffgen.mhdsafety.data.FoodItem
import com.paeffgen.mhdsafety.data.Recipe
import com.paeffgen.mhdsafety.domain.CookChange
import com.paeffgen.mhdsafety.domain.Expiry
import com.paeffgen.mhdsafety.domain.IngredientMatcher
import com.paeffgen.mhdsafety.domain.RecipeCheck
import com.paeffgen.mhdsafety.domain.RecipeParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.time.LocalDate

sealed interface ImportState {
    data object Idle : ImportState
    data class Loading(val url: String) : ImportState
    data class Loaded(val recipe: Recipe) : ImportState
    data class Failed(val message: String, val url: String?) : ImportState
}

class RecipeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MhdSafetyApp

    val recipes: StateFlow<List<Recipe>?> = app.recipes.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val foods: StateFlow<List<FoodItem>> = app.foods.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bufferDays: StateFlow<Int> = app.settings.bufferDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Expiry.DEFAULT_BUFFER_DAYS)

    /** Gespeicherte Rezepte, das Passendste zum Bestand zuerst. */
    val ranked: StateFlow<List<RecipeCheck>?> = combine(app.recipes.items, app.foods.items, app.settings.bufferDays) { recipes, foods, buffer ->
        val today = LocalDate.now()
        IngredientMatcher.rank(recipes.map { IngredientMatcher.check(it, foods, buffer, today) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _import = MutableStateFlow<ImportState>(ImportState.Idle)
    val import: StateFlow<ImportState> = _import.asStateFlow()

    /** @param text ein Link oder ein geteilter Text, in dem irgendwo ein Link steckt */
    fun startImport(text: String) {
        val url = RecipeParser.extractUrl(text)
        if (url == null) {
            _import.value = ImportState.Failed("In „${text.take(80)}“ steckt kein Link.", null)
            return
        }
        _import.value = ImportState.Loading(url)
        viewModelScope.launch {
            _import.value = when (val result = app.recipeFetcher.fetch(url)) {
                is FetchResult.Found -> ImportState.Loaded(result.recipe)
                FetchResult.NoRecipe -> ImportState.Failed(
                    "Auf dieser Seite wurde kein Rezept gefunden. Klappt mit den meisten Rezeptseiten " +
                        "wie Chefkoch, Lecker oder EatSmarter – aber nicht mit Suchergebnissen oder Videos.",
                    url,
                )
                is FetchResult.Failed -> ImportState.Failed(result.message, url)
            }
        }
    }

    fun saveImported() {
        val recipe = (_import.value as? ImportState.Loaded)?.recipe ?: return
        viewModelScope.launch { app.recipes.save(recipe) }
        _import.value = ImportState.Idle
    }

    fun clearImport() {
        _import.value = ImportState.Idle
    }

    fun delete(recipe: Recipe) {
        viewModelScope.launch { app.recipes.delete(recipe) }
    }

    /**
     * Wendet die ausgewählten Änderungen nach dem Kochen an.
     * @return die Einträge vorher, für "Rückgängig"
     */
    fun applyCooking(changes: List<CookChange>): List<FoodItem> {
        viewModelScope.launch {
            changes.forEach { change ->
                if (change.usedUp) app.foods.delete(change.item) else app.foods.save(change.updatedItem)
            }
        }
        return changes.map { it.item }
    }

    /** Stellt Einträge so wieder her, wie sie vor dem Kochen waren. */
    fun undoCooking(originals: List<FoodItem>) {
        viewModelScope.launch { originals.forEach { app.foods.save(it) } }
    }

    fun check(recipe: Recipe, foods: List<FoodItem>, bufferDays: Int): RecipeCheck =
        IngredientMatcher.check(recipe, foods, bufferDays, LocalDate.now())

    companion object {
        /** Google-Suche nach Rezepten mit diesen Zutaten. */
        fun searchUrl(terms: List<String>): String =
            "https://www.google.com/search?q=" + URLEncoder.encode("Rezept " + terms.joinToString(" "), "UTF-8")
    }
}
