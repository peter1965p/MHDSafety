package com.paeffgen.mhdsafety.domain

import com.paeffgen.mhdsafety.data.FoodItem
import com.paeffgen.mhdsafety.data.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CookingTest {
    private val today = LocalDate.of(2026, 11, 8)

    private fun item(id: Long, name: String, inDays: Long, quantity: Double? = null, unit: QuantityUnit? = null) =
        FoodItem(id = id, name = name, date = today.plusDays(inDays), quantity = quantity, unit = unit)

    private fun plan(items: List<FoodItem>, vararg ingredients: String): List<CookChange> {
        val recipe = Recipe(url = "u", name = "Test", ingredients = ingredients.toList())
        return Cooking.plan(IngredientMatcher.check(recipe, items, bufferDays = 0, today = today))
    }

    @Test
    fun partialUseKeepsRest() {
        val hack = item(1, "Hackfleisch", 3, 500.0, QuantityUnit.GRAM)
        val change = plan(listOf(hack), "300 g Hackfleisch").single()
        assertTrue(change.exact)
        assertFalse(change.usedUp)
        assertEquals(200.0, change.newQuantity!!, 1e-9)
        assertEquals(200.0, change.updatedItem.quantity!!, 1e-9)
    }

    @Test
    fun mostUrgentIsUsedFirst() {
        val old = item(1, "Hackfleisch", 1, 250.0, QuantityUnit.GRAM)
        val fresh = item(2, "Hackfleisch", 6, 500.0, QuantityUnit.GRAM)
        val changes = plan(listOf(fresh, old), "400 g Hackfleisch").associateBy { it.item.id }
        assertTrue(changes.getValue(1).usedUp)
        assertEquals(350.0, changes.getValue(2).newQuantity!!, 1e-9)
    }

    @Test
    fun tooLittleUsesEverything() {
        val sahne = item(1, "Sahne", 2, 200.0, QuantityUnit.MILLILITER)
        val change = plan(listOf(sahne), "300 ml Sahne").single()
        assertTrue(change.usedUp)
    }

    @Test
    fun gramAndMilliliterAreComparable() {
        val joghurt = item(1, "Joghurt", 4, 500.0, QuantityUnit.GRAM)
        assertEquals(350.0, plan(listOf(joghurt), "150 ml Joghurt").single().newQuantity!!, 1e-9)
    }

    @Test
    fun unknownAmountIsAGuess() {
        val tomaten = item(1, "Tomaten passiert", 30)
        val change = plan(listOf(tomaten), "1 Dose Tomaten").single()
        assertFalse(change.exact)
        assertTrue(change.usedUp)
        assertTrue(change.suggested)
    }

    @Test
    fun vagueLinesAreNotPreselected() {
        val knoblauch = item(1, "Knoblauch", 20)
        val sahne = item(2, "Sahne", 5)
        val changes = plan(listOf(knoblauch, sahne), "etwas Knoblauch (gehackt)", "evtl. Sahne")
        assertTrue(changes.none { it.suggested })
    }

    @Test
    fun sameItemIsNotUsedTwice() {
        val sahne = item(1, "Sahne", 3, 200.0, QuantityUnit.MILLILITER)
        val change = plan(listOf(sahne), "100 ml Sahne", "50 ml Sahne").single()
        assertEquals(50.0, change.newQuantity!!, 1e-9)
    }

    @Test
    fun pantryAndMissingAreUntouched() {
        val hack = item(1, "Hackfleisch", 3, 500.0, QuantityUnit.GRAM)
        val changes = plan(listOf(hack), "Salz und Pfeffer", "1 Zwiebel", "500 g Hackfleisch")
        assertEquals(listOf(1L), changes.map { it.item.id })
        assertTrue(changes.single().usedUp)
    }
}
