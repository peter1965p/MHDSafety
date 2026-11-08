package com.paeffgen.mhdsafety.domain

import com.paeffgen.mhdsafety.data.FoodItem

/**
 * Eine geplante Änderung am Bestand nach dem Kochen.
 *
 * @param newQuantity was danach noch da ist (in der Einheit des Eintrags), null = ohne Menge
 * @param usedUp Eintrag ist aufgebraucht und wird entfernt
 * @param exact aus Mengen berechnet; sonst nur eine Vermutung ("1 Dose Tomaten", Eintrag ohne Menge)
 * @param suggested soll im Dialog vorausgewählt sein
 */
data class CookChange(
    val item: FoodItem,
    val newQuantity: Double?,
    val usedUp: Boolean,
    val exact: Boolean,
    val suggested: Boolean,
) {
    /** Der Eintrag, wie er nach dem Kochen gespeichert wird (nur wenn nicht aufgebraucht). */
    val updatedItem: FoodItem get() = item.copy(quantity = newQuantity)
}

/** Plant, was "Gekocht" mit dem Bestand macht – ohne etwas zu ändern. */
object Cooking {
    /** Unter dieser Restmenge gilt etwas als aufgebraucht (Rundungsreste). */
    private const val EPSILON = 0.001

    /** Zeilen wie "etwas Knoblauch" oder "evtl. Sahne": dann nicht von selbst als aufgebraucht vorschlagen. */
    private val vagueStart = Regex("""^\s*(etwas|evtl\.?|eventuell|n\.\s*b\.?|nach belieben|optional|ggf\.?|wenig|einige)(?!\p{L})""", RegexOption.IGNORE_CASE)

    fun plan(check: RecipeCheck): List<CookChange> {
        // Aktuelle Restmengen, damit zwei Zutatenzeilen denselben Eintrag nicht doppelt verbrauchen.
        val remaining = mutableMapOf<Long, Double?>()
        val changes = linkedMapOf<Long, CookChange>()

        for (match in check.matches.filterIsInstance<IngredientMatch.Available>()) {
            val needed = match.needed
            if (needed != null && match.have != null) {
                var rest = needed.amount
                for (item in match.items) {
                    if (rest <= EPSILON) break
                    val itemUnit = item.unit ?: continue
                    val current = remaining.getOrPut(item.id) { item.quantity } ?: continue
                    // In der Einheit des Rezepts rechnen (g und ml gelten als 1 : 1).
                    val currentInNeeded = Quantity(current, itemUnit).inUnit(needed.unit) ?: continue
                    val take = minOf(currentInNeeded, rest)
                    rest -= take
                    val left = (currentInNeeded - take).coerceAtLeast(0.0)
                    remaining[item.id] = left
                    changes[item.id] = CookChange(
                        item = item,
                        newQuantity = left,
                        usedUp = left <= EPSILON,
                        exact = true,
                        suggested = true,
                    )
                }
            } else {
                // Menge unbekannt: das Dringendste vorschlagen, der Rest bleibt.
                val item = match.items.first()
                if (item.id !in changes) {
                    changes[item.id] = CookChange(
                        item = item,
                        newQuantity = null,
                        usedUp = true,
                        exact = false,
                        suggested = !vagueStart.containsMatchIn(match.line),
                    )
                }
            }
        }
        return changes.values.toList()
    }
}
