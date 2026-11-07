package com.paeffgen.mhdsafety.domain

import com.paeffgen.mhdsafety.data.Category

/** Ordnet Open-Food-Facts-Kategorien (z. B. "en:yogurts") unseren Supermarkt-Kategorien zu. */
object CategoryMapper {
    // Englische Tags sind der Normalfall, manchmal kommen aber deutsche/niederländische/französische.
    // Reihenfolge zählt: "Tiefkühlpizza" ist Tiefkühl, "Fruchtjoghurt" ist Milch, nicht Obst.
    private val rules: List<Pair<Category, List<String>>> = listOf(
        Category.FROZEN to listOf("frozen", "tiefkühl", "surgel", "diepvries"),
        Category.CANNED to listOf("canned", "konserve", "conserve"),
        Category.CHEESE to listOf("cheese", "käse", "kaese", "fromage", "kaas"),
        Category.FISH to listOf("fish", "seafood", "tuna", "salmon", "fisch", "poisson", "vis"),
        Category.MEAT to listOf("meat", "sausage", "ham", "poultr", "charcuterie", "salami", "bacon", "fleisch", "wurst", "schinken", "viande", "vlees"),
        Category.DAIRY to listOf("dair", "milk", "yogurt", "yoghurt", "egg", "butter", "cream", "quark", "milch", "joghurt", "sahne", "lait", "zuivel"),
        Category.DRINKS to listOf("beverage", "drink", "juice", "water", "soda", "coffee", "tea", "getränk", "saft", "boisson", "drank"),
        Category.SWEETS to listOf("snack", "sweet", "chocolate", "candies", "confection", "biscuit", "crisps", "chocola", "schokolade", "süß", "suess", "snoep", "keks"),
        Category.BAKERY to listOf("bread", "pastr", "cake", "baker", "brot", "backwaren", "pain", "brood"),
        Category.READY_MEALS to listOf("meal", "pizza", "sandwich"),
        Category.SAUCES to listOf("sauce", "condiment", "spice", "dressing", "ketchup", "mustard"),
        Category.PANTRY to listOf("pasta", "rice", "cereal", "flour", "legume", "noodle", "oil", "nudel", "reis", "mehl", "müsli"),
        Category.FRUIT_VEG to listOf("fruit", "vegetable", "salad", "obst", "gemüse", "fruits", "groente"),
    )

    // Tauchen bei fast jedem Produkt auf und sagen nichts aus.
    private val ignored = setOf("en:plant-based-foods-and-beverages", "en:plant-based-foods", "en:beverages-and-beverages-preparations")

    /** Tags sind von allgemein nach speziell sortiert, deshalb von hinten prüfen. */
    fun map(tags: List<String>): Category? {
        for (tag in tags.asReversed()) {
            if (tag in ignored) continue
            val t = tag.lowercase()
            rules.firstOrNull { (_, words) -> words.any { it in t } }?.let { return it.first }
        }
        return null
    }
}
