package com.paeffgen.mhdsafety.data

/**
 * Kategorien in der Reihenfolge eines typischen Supermarkt-Rundgangs.
 * Gespeichert wird der Name – Reihenfolge und Texte dürfen sich also ändern,
 * die Namen der Einträge aber nicht.
 */
enum class Category(val label: String, val emoji: String) {
    FRUIT_VEG("Obst & Gemüse", "🥦"),
    BAKERY("Brot & Backwaren", "🍞"),
    DAIRY("Milchprodukte & Eier", "🥛"),
    CHEESE("Käse", "🧀"),
    MEAT("Fleisch & Wurst", "🥩"),
    FISH("Fisch", "🐟"),
    READY_MEALS("Fertiggerichte", "🍱"),
    FROZEN("Tiefkühl", "🧊"),
    PANTRY("Nudeln, Reis & Vorrat", "🍝"),
    CANNED("Konserven", "🥫"),
    SAUCES("Saucen & Gewürze", "🧂"),
    SWEETS("Süßes & Snacks", "🍫"),
    DRINKS("Getränke", "🧃"),
    OTHER("Sonstiges", "📦");

    /** Bei diesen Kategorien steht meist "zu verbrauchen bis" auf der Packung. */
    val usuallyUseBy: Boolean get() = this == MEAT || this == FISH
}
