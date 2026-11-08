package com.paeffgen.mhdsafety.domain

import kotlin.math.abs
import kotlin.math.roundToLong

/** Grundeinheiten: alles wird in g, ml oder Stück gespeichert. */
enum class QuantityUnit(val label: String) {
    GRAM("g"),
    MILLILITER("ml"),
    PIECE("Stück"),
}

data class Quantity(val amount: Double, val unit: QuantityUnit) {

    /**
     * Menge in einer anderen Einheit, falls vergleichbar.
     * g und ml werden grob 1 : 1 gerechnet (passt für Milch, Sahne, Joghurt & Co.),
     * Stück lässt sich nur mit Stück vergleichen.
     */
    fun inUnit(target: QuantityUnit): Double? = when {
        unit == target -> amount
        unit != QuantityUnit.PIECE && target != QuantityUnit.PIECE -> amount
        else -> null
    }

    fun format(): String = when (unit) {
        QuantityUnit.GRAM -> if (amount >= 1000) "${formatNumber(amount / 1000)} kg" else "${formatNumber(amount)} g"
        QuantityUnit.MILLILITER -> if (amount >= 1000) "${formatNumber(amount / 1000)} l" else "${formatNumber(amount)} ml"
        QuantityUnit.PIECE -> "${formatNumber(amount)} Stück"
    }

    companion object {
        /** 500.0 -> "500", 1.5 -> "1,5", 0.333 -> "0,33" */
        fun formatNumber(value: Double): String {
            if (abs(value - value.roundToLong()) < 0.005) return value.roundToLong().toString()
            return "%.2f".format(java.util.Locale.GERMANY, value).trimEnd('0').trimEnd(',')
        }

        /** Eingabe aus einem Textfeld: "1,5" oder "1.5". */
        fun parseNumber(text: String): Double? =
            text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
    }
}

/**
 * Liest Mengen aus Zutatenzeilen ("500 g Hackfleisch", "1 ½ TL Salz", "2-3 Zwiebeln")
 * und aus den Packungsangaben von Open Food Facts ("4 x 125 g").
 */
object QuantityParser {
    private const val FRACTIONS = "½¼¾⅓⅔⅛"
    private val fractionValues = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1 / 3.0, '⅔' to 2 / 3.0, '⅛' to 0.125)

    private const val NUMBER = """\d+\s+\d+\s*/\s*\d+|\d+\s*/\s*\d+|\d+\s*[$FRACTIONS]|\d+(?:[.,]\d+)?|[$FRACTIONS]"""
    private val leadingAmount = Regex("""^\s*($NUMBER)(?:\s*(?:-|–|bis)\s*($NUMBER))?\s*""")
    private val multiPack = Regex("""^\s*(\d+)\s*[x×]\s*(.+)$""", RegexOption.IGNORE_CASE)

    /** Einheit -> Faktor zur Grundeinheit. */
    private val units: Map<String, Pair<Double, QuantityUnit>> = mapOf(
        "g" to (1.0 to QuantityUnit.GRAM), "gr" to (1.0 to QuantityUnit.GRAM), "gramm" to (1.0 to QuantityUnit.GRAM),
        "kg" to (1000.0 to QuantityUnit.GRAM), "kilo" to (1000.0 to QuantityUnit.GRAM),
        "kilogramm" to (1000.0 to QuantityUnit.GRAM),
        "ml" to (1.0 to QuantityUnit.MILLILITER), "cl" to (10.0 to QuantityUnit.MILLILITER),
        "dl" to (100.0 to QuantityUnit.MILLILITER), "l" to (1000.0 to QuantityUnit.MILLILITER),
        "liter" to (1000.0 to QuantityUnit.MILLILITER), "ltr" to (1000.0 to QuantityUnit.MILLILITER),
        "el" to (15.0 to QuantityUnit.MILLILITER), "esslöffel" to (15.0 to QuantityUnit.MILLILITER),
        "tl" to (5.0 to QuantityUnit.MILLILITER), "teelöffel" to (5.0 to QuantityUnit.MILLILITER),
        "stück" to (1.0 to QuantityUnit.PIECE), "stk" to (1.0 to QuantityUnit.PIECE), "st" to (1.0 to QuantityUnit.PIECE),
    )

    /** Gebinde ohne feste Menge: "1 Dose Tomaten" sagt nichts über Gramm. */
    private val vagueUnits = setOf(
        "dose", "dosen", "becher", "pck", "pkt", "päckchen", "packung", "packungen", "pack", "glas", "gläser",
        "bund", "prise", "prisen", "msp", "messerspitze", "zehe", "zehen", "scheibe", "scheiben", "tasse", "tassen",
        "handvoll", "zweig", "zweige", "stange", "stangen", "blatt", "blätter", "kopf", "knolle", "würfel",
        "beutel", "flasche", "flaschen", "schuss", "spritzer", "tropfen", "tube", "netz", "schale", "portion",
        "portionen", "rolle", "rollen", "tafel", "tafeln", "riegel", "dash", "cup", "cups", "tbsp", "tsp", "oz", "lb",
    )

    /** Menge am Anfang einer Zutatenzeile, oder null, wenn keine oder nur "1 Dose". */
    fun parseIngredient(line: String): Quantity? {
        val match = leadingAmount.find(line) ?: return null
        val low = number(match.groupValues[1]) ?: return null
        // Bei "2-3 Zwiebeln" lieber mit 3 rechnen, dann reicht es sicher.
        val amount = match.groupValues[2].takeIf { it.isNotEmpty() }?.let(::number) ?: low
        if (amount <= 0) return null
        val rest = line.substring(match.range.last + 1)
        val unitWord = rest.trim().split(Regex("\\s+")).firstOrNull()
            ?.lowercase()?.trimEnd('.', ',')
            .orEmpty()
        units[unitWord]?.let { (factor, unit) -> return Quantity(amount * factor, unit) }
        if (unitWord in vagueUnits || unitWord.isEmpty()) return null
        // "2 Zwiebeln", "3 große Eier": Stückzahl
        return Quantity(amount, QuantityUnit.PIECE)
    }

    /**
     * Packungsgröße von Open Food Facts: zuerst die aufbereiteten Felder
     * `product_quantity` + `product_quantity_unit`, sonst der Packungstext `quantity`.
     */
    fun fromOpenFoodFacts(productQuantity: String?, productUnit: String?, quantityText: String?): Quantity? {
        val amount = productQuantity?.let(Quantity::parseNumber)
        val unit = units[productUnit?.trim()?.lowercase()]
        if (amount != null && unit != null && unit.second != QuantityUnit.PIECE) {
            return Quantity(amount * unit.first, unit.second)
        }
        return quantityText?.let(::parsePackage)
    }

    /** "500 g", "1 l", "4 x 125 g", "10 Stück", "6 Eier". */
    fun parsePackage(text: String): Quantity? {
        multiPack.find(text)?.let { m ->
            val count = m.groupValues[1].toInt()
            val single = parseIngredient(m.groupValues[2]) ?: return null
            return single.copy(amount = single.amount * count)
        }
        return parseIngredient(text)
    }

    private fun number(text: String): Double? {
        val t = text.replace(Regex("\\s+"), " ").trim()
        // "1 1/2"
        Regex("""^(\d+) (\d+)\s*/\s*(\d+)$""").find(t)?.let { m ->
            val (whole, num, den) = m.destructured
            return whole.toDouble() + fraction(num, den)
        }
        // "1/2"
        Regex("""^(\d+)\s*/\s*(\d+)$""").find(t)?.let { m ->
            val (num, den) = m.destructured
            return fraction(num, den)
        }
        // "1½", "1 ½", "½"
        if (t.isNotEmpty() && t.last() in fractionValues) {
            val whole = t.dropLast(1).trim().ifEmpty { "0" }.toDoubleOrNull() ?: return null
            return whole + fractionValues.getValue(t.last())
        }
        return t.replace(',', '.').toDoubleOrNull()
    }

    private fun fraction(num: String, den: String): Double {
        val d = den.toDouble()
        return if (d == 0.0) 0.0 else num.toDouble() / d
    }
}
