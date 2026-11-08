package com.paeffgen.mhdsafety.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuantityTest {
    private fun q(amount: Double, unit: QuantityUnit) = Quantity(amount, unit)

    @Test
    fun weightsAndVolumes() {
        assertEquals(q(500.0, QuantityUnit.GRAM), QuantityParser.parseIngredient("500 g Hackfleisch"))
        assertEquals(q(500.0, QuantityUnit.GRAM), QuantityParser.parseIngredient("500g Hackfleisch"))
        assertEquals(q(1500.0, QuantityUnit.GRAM), QuantityParser.parseIngredient("1,5 kg Kartoffeln"))
        assertEquals(q(200.0, QuantityUnit.MILLILITER), QuantityParser.parseIngredient("200 ml Schlagsahne"))
        assertEquals(q(250.0, QuantityUnit.MILLILITER), QuantityParser.parseIngredient("0.25 l Milch"))
        assertEquals(q(60.0, QuantityUnit.MILLILITER), QuantityParser.parseIngredient("4 EL Schlagsahne"))
    }

    @Test
    fun fractionsAndRanges() {
        assertEquals(q(0.5, QuantityUnit.PIECE), QuantityParser.parseIngredient("½ Zwiebel"))
        assertEquals(q(1.5, QuantityUnit.PIECE), QuantityParser.parseIngredient("1 ½ Zwiebeln"))
        assertEquals(q(1.5, QuantityUnit.PIECE), QuantityParser.parseIngredient("1 1/2 Zwiebeln"))
        assertEquals(q(2.5, QuantityUnit.MILLILITER), QuantityParser.parseIngredient("1/2 TL Salz"))
        // Bei Spannen lieber die größere Zahl, dann reicht es sicher.
        assertEquals(q(3.0, QuantityUnit.PIECE), QuantityParser.parseIngredient("2-3 Zwiebeln"))
        assertEquals(q(3.0, QuantityUnit.PIECE), QuantityParser.parseIngredient("2 – 3 große Zwiebeln"))
    }

    @Test
    fun piecesAndVagueAmounts() {
        assertEquals(q(3.0, QuantityUnit.PIECE), QuantityParser.parseIngredient("3 Eier"))
        assertEquals(q(2.0, QuantityUnit.PIECE), QuantityParser.parseIngredient("2 Stk. Paprika"))
        assertNull(QuantityParser.parseIngredient("1 Dose Tomaten"))
        assertNull(QuantityParser.parseIngredient("1 Becher Schmand"))
        assertNull(QuantityParser.parseIngredient("etwas Wasser"))
        assertNull(QuantityParser.parseIngredient("n. B. Salz und Pfeffer"))
        assertNull(QuantityParser.parseIngredient("5"))
    }

    @Test
    fun openFoodFacts() {
        assertEquals(q(500.0, QuantityUnit.GRAM), QuantityParser.fromOpenFoodFacts("500", "g", "500 g"))
        assertEquals(q(1000.0, QuantityUnit.MILLILITER), QuantityParser.fromOpenFoodFacts("1", "l", null))
        assertEquals(q(500.0, QuantityUnit.GRAM), QuantityParser.fromOpenFoodFacts(null, null, "4 x 125 g"))
        assertEquals(q(10.0, QuantityUnit.PIECE), QuantityParser.fromOpenFoodFacts(null, null, "10 Stück"))
        assertEquals(q(200.0, QuantityUnit.GRAM), QuantityParser.fromOpenFoodFacts(null, null, "200g"))
        assertNull(QuantityParser.fromOpenFoodFacts(null, null, null))
        assertNull(QuantityParser.fromOpenFoodFacts(null, null, "Packung"))
    }

    @Test
    fun formatting() {
        assertEquals("500 g", q(500.0, QuantityUnit.GRAM).format())
        assertEquals("1,5 kg", q(1500.0, QuantityUnit.GRAM).format())
        assertEquals("1 l", q(1000.0, QuantityUnit.MILLILITER).format())
        assertEquals("6 Stück", q(6.0, QuantityUnit.PIECE).format())
        assertEquals("0,5 Stück", q(0.5, QuantityUnit.PIECE).format())
        assertEquals(1.5, Quantity.parseNumber("1,5")!!, 1e-9)
        assertNull(Quantity.parseNumber("abc"))
        assertNull(Quantity.parseNumber("0"))
    }

    @Test
    fun compareUnits() {
        assertEquals(200.0, q(200.0, QuantityUnit.MILLILITER).inUnit(QuantityUnit.GRAM)!!, 1e-9)
        assertNull(q(3.0, QuantityUnit.PIECE).inUnit(QuantityUnit.GRAM))
    }
}
