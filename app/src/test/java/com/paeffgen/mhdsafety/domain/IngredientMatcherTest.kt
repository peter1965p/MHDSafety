package com.paeffgen.mhdsafety.domain

import com.paeffgen.mhdsafety.data.DateType
import com.paeffgen.mhdsafety.data.FoodItem
import com.paeffgen.mhdsafety.data.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IngredientMatcherTest {
    private val today = LocalDate.of(2026, 11, 8)
    private var nextId = 1L

    private fun item(name: String, inDays: Long = 10, type: DateType = DateType.BEST_BEFORE) =
        FoodItem(id = nextId++, name = name, date = today.plusDays(inDays), dateType = type)

    private fun matchOf(line: String, vararg items: FoodItem) = IngredientMatcher.match(line, items.toList())

    @Test
    fun stockIsMoreSpecificThanRecipe() {
        val sahne = item("Weihenstephan Schlagsahne 30 %")
        val match = matchOf("200 ml Sahne", sahne)
        assertTrue(match is IngredientMatch.Available)
        assertEquals(listOf(sahne), (match as IngredientMatch.Available).items)
    }

    @Test
    fun recipeIsMoreSpecificThanStock() {
        assertTrue(matchOf("2 rote Paprikaschoten, gewürfelt", item("Paprika")) is IngredientMatch.Available)
        assertTrue(matchOf("400 g Hähnchenbrustfilet", item("Hähnchenbrust")) is IngredientMatch.Available)
    }

    @Test
    fun pluralAndSingular() {
        assertTrue(matchOf("2 Zwiebeln", item("Zwiebel")) is IngredientMatch.Available)
        assertTrue(matchOf("1 Tomate", item("Tomaten passiert")) is IngredientMatch.Available)
        assertTrue(matchOf("3 Eier", item("Eier Freiland")) is IngredientMatch.Available)
        assertTrue(matchOf("1 Ei", item("Bio Eier")) is IngredientMatch.Available)
    }

    @Test
    fun synonyms() {
        assertTrue(matchOf("500 g Gehacktes", item("Rinderhackfleisch")) is IngredientMatch.Available)
        assertTrue(matchOf("2 Möhren", item("Karotten")) is IngredientMatch.Available)
    }

    @Test
    fun pantry() {
        assertTrue(matchOf("Salz und Pfeffer") is IngredientMatch.Pantry)
        assertTrue(matchOf("2 EL Olivenöl") is IngredientMatch.Pantry)
        assertTrue(matchOf("1 TL Paprikapulver, edelsüß") is IngredientMatch.Pantry)
        // Grundvorrat gewinnt auch, wenn etwas Ähnliches im Bestand ist.
        assertTrue(matchOf("1 Prise Salz", item("Salzstangen")) is IngredientMatch.Pantry)
    }

    @Test
    fun missing() {
        assertTrue(matchOf("1 Zwiebel", item("Schlagsahne")) is IngredientMatch.Missing)
        // "Butter" im Rezept ist kein Butterkäse.
        assertTrue(matchOf("50 g Butter", item("Butterkäse")) is IngredientMatch.Missing)
    }

    @Test
    fun quantityInParenthesesIsIgnored() {
        assertEquals(listOf("tomat"), IngredientMatcher.keywords("1 Dose Tomaten (ca. 400 g)"))
    }

    @Test
    fun chefkochStyleLines() {
        assertEquals(listOf("paprikaschot"), IngredientMatcher.keywords("500 g Paprikaschote(n) (klein geschnitten)"))
        assertTrue(matchOf("1 Zwiebel(n) (gewürfelt)", item("Zwiebeln")) is IngredientMatch.Available)
        assertTrue(matchOf("n. B. Salz und Pfeffer") is IngredientMatch.Pantry)
        assertTrue(matchOf("etwas Wasser") is IngredientMatch.Pantry)
        assertTrue(matchOf("evtl. Sahne", item("Kochsahne")) is IngredientMatch.Available)
    }

    @Test
    fun expiredUseByDoesNotCount() {
        val hack = item("Hackfleisch", inDays = -1, type = DateType.USE_BY)
        val recipe = Recipe(url = "u", name = "Bolognese", ingredients = listOf("500 g Hackfleisch"))
        val check = IngredientMatcher.check(recipe, listOf(hack), bufferDays = 3, today = today)
        assertEquals(1, check.missingCount)
    }

    @Test
    fun rankingPrefersCompleteThenUrgent() {
        val sahne = item("Sahne", inDays = 1)
        val nudeln = item("Spaghetti", inDays = 200)
        val zwiebel = item("Zwiebeln", inDays = 20)
        val foods = listOf(sahne, nudeln, zwiebel)
        fun check(name: String, vararg ingredients: String) =
            IngredientMatcher.check(Recipe(url = name, name = name, ingredients = ingredients.toList()), foods, 0, today)

        val incomplete = check("Lasagne", "Sahne", "Lasagneplatten")
        val completeNotUrgent = check("Zwiebelnudeln", "Spaghetti", "Zwiebeln", "Salz")
        val completeUrgent = check("Sahnenudeln", "Spaghetti", "200 ml Sahne")

        val ranked = IngredientMatcher.rank(listOf(incomplete, completeNotUrgent, completeUrgent))
        assertEquals(listOf("Sahnenudeln", "Zwiebelnudeln", "Lasagne"), ranked.map { it.recipe.name })
        assertEquals(1, completeUrgent.urgentCount)
    }

    @Test
    fun searchTermDropsQuantities() {
        assertEquals("Weihenstephan Schlagsahne", IngredientMatcher.searchTerm("Weihenstephan Schlagsahne 30 %"))
        assertEquals("Hackfleisch gemischt", IngredientMatcher.searchTerm("Hackfleisch gemischt 500g"))
    }
}
