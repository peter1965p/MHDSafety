package com.paeffgen.mhdsafety.domain

import com.paeffgen.mhdsafety.data.FoodItem
import com.paeffgen.mhdsafety.data.Recipe
import java.time.LocalDate

/** Ergebnis für eine Zutatenzeile eines Rezepts. */
sealed interface IngredientMatch {
    val line: String

    /** Im Bestand, [items] sortiert nach Dringlichkeit (dringendstes zuerst). */
    data class Available(override val line: String, val items: List<FoodItem>) : IngredientMatch

    /** Salz, Pfeffer, Öl & Co. – hat man einfach da. */
    data class Pantry(override val line: String) : IngredientMatch

    data class Missing(override val line: String) : IngredientMatch
}

/** Wie gut passt ein Rezept zum Bestand? */
data class RecipeCheck(
    val recipe: Recipe,
    val matches: List<IngredientMatch>,
    /** Zutaten aus dem Bestand, die bald ablaufen (oder schon über dem Alarm sind). */
    val urgentCount: Int,
) {
    val total: Int get() = matches.size
    val haveCount: Int get() = matches.count { it !is IngredientMatch.Missing }
    val missingCount: Int get() = total - haveCount
    val fromStockCount: Int get() = matches.count { it is IngredientMatch.Available }
}

/**
 * Gleicht Zutatenzeilen ("200 g Schlagsahne") mit dem Bestand ("Weihenstephan Schlagsahne 30 %") ab.
 *
 * Ganz ohne KI: Menge, Einheit und Beiwörter weg, Wörter grob auf ihren Stamm kürzen,
 * ein paar Synonyme, und dann zusammengesetzte Wörter so vergleichen, wie Deutsch funktioniert:
 * - Bestand ist genauer als das Rezept: "Schlagsahne" endet auf "Sahne" -> passt.
 * - Rezept ist genauer als der Bestand: "Paprikaschoten" beginnt mit "Paprika" -> passt.
 */
object IngredientMatcher {

    fun check(recipe: Recipe, items: List<FoodItem>, bufferDays: Int, today: LocalDate): RecipeCheck {
        val usable = items.filter { usable(it, bufferDays, today) }
        val matches = recipe.ingredients.map { match(it, usable, bufferDays) }
        val urgent = matches.filterIsInstance<IngredientMatch.Available>()
            .flatMap { it.items }
            .distinctBy { it.id }
            .count { Expiry.status(it, bufferDays, today) != ExpiryStatus.OK }
        return RecipeCheck(recipe, matches, urgent)
    }

    /**
     * Reihenfolge der Vorschläge: zuerst, was man (fast) komplett da hat,
     * bei Gleichstand das, was mehr bald Ablaufendes verbraucht.
     */
    fun rank(checks: List<RecipeCheck>): List<RecipeCheck> = checks.sortedWith(
        compareBy<RecipeCheck> { it.missingCount }
            .thenByDescending { it.urgentCount }
            .thenByDescending { it.fromStockCount }
            .thenBy { it.recipe.name.lowercase() },
    )

    /** Abgelaufenes mit "Zu verbrauchen bis" zählt nicht mehr als vorhanden. */
    fun usable(item: FoodItem, bufferDays: Int, today: LocalDate): Boolean =
        !(item.isUseBy && Expiry.status(item, bufferDays, today) == ExpiryStatus.EXPIRED)

    fun match(line: String, items: List<FoodItem>, bufferDays: Int = Expiry.DEFAULT_BUFFER_DAYS): IngredientMatch {
        val words = keywords(line)
        if (words.isEmpty() || words.all(::isPantryWord)) return IngredientMatch.Pantry(line)

        val found = items.filter { item ->
            val itemWords = nameWords(item.name)
            words.any { w -> itemWords.any { wordsMatch(w, it) } }
        }
        return when {
            found.isNotEmpty() -> IngredientMatch.Available(line, found.sortedBy { Expiry.alarmDate(it, bufferDays) })
            else -> IngredientMatch.Missing(line)
        }
    }

    /** Suchbegriff für die Rezeptsuche aus einem Produktnamen: ohne Mengen, Prozente und Füllwörter. */
    fun searchTerm(name: String): String =
        name.split(Regex("\\s+"))
            .filter { w -> w.none(Char::isDigit) && '%' !in w }
            .filter { normalizeWord(it) !in stopWords }
            .joinToString(" ")
            .ifBlank { name }

    // --- intern, für Tests sichtbar ---

    /** Die Schlüsselwörter einer Zutatenzeile, z. B. "2 rote Paprikaschoten, gewürfelt" -> [paprikaschot]. */
    internal fun keywords(line: String): List<String> {
        val main = line
            .replace(Regex("\\([^)]*\\)"), " ") // "(ca. 400 g)"
            .substringBefore(',') // ", fein gehackt"
            .substringBefore(';')
        return words(main)
    }

    internal fun nameWords(name: String): List<String> = words(name)

    private fun words(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^\\p{L}]+"))
            .filter { it.length >= 2 && it !in stopWords }
            .map { synonyms[it] ?: it }
            .map(::stem)
            .distinct()

    private fun normalizeWord(word: String) = word.lowercase().trim { !it.isLetter() }

    /** Sehr grobe Grundform: "Zwiebeln" -> "zwiebel", "Tomaten"/"Tomate" -> "tomat". */
    internal fun stem(word: String): String {
        for (suffix in listOf("en", "er", "n", "e", "s")) {
            if (word.endsWith(suffix) && word.length - suffix.length >= 4) return word.dropLast(suffix.length)
        }
        return word
    }

    internal fun wordsMatch(recipeWord: String, itemWord: String): Boolean = when {
        recipeWord == itemWord -> true
        // Bestand genauer: "schlagsahn" endet auf "sahn"
        recipeWord.length >= 4 && itemWord.endsWith(recipeWord) -> true
        // Rezept genauer: "paprikaschot" beginnt mit "paprika"
        itemWord.length >= 4 && recipeWord.startsWith(itemWord) -> true
        else -> false
    }

    private fun isPantryWord(word: String) = word in pantry

    private val stopWords = setOf(
        // Mengen und Einheiten
        "g", "gr", "gramm", "kg", "kilo", "ml", "cl", "dl", "l", "liter", "el", "tl", "esslöffel", "teelöffel",
        "msp", "messerspitze", "prise", "prisen", "pck", "pkt", "päckchen", "packung", "packungen", "pack",
        "dose", "dosen", "becher", "glas", "gläser", "tasse", "tassen", "bund", "stück", "stk", "st",
        "scheibe", "scheiben", "zehe", "zehen", "handvoll", "würfel", "beutel", "flasche", "schuss", "spritzer",
        "tropfen", "blatt", "blätter", "stange", "stangen", "zweig", "zweige", "knolle", "kopf", "köpfe",
        "kleine", "kleiner", "kleines", "klein", "große", "großer", "großes", "groß", "mittelgroße", "mittelgroßer",
        "halbe", "halber", "ganze", "ganzer", "viertel", "etwas", "einige", "wenig", "viel", "reichlich", "ca", "evtl",
        // Füllwörter
        "und", "oder", "mit", "ohne", "von", "vom", "aus", "zum", "zur", "für", "nach", "belieben", "bedarf",
        "geschmack", "optional", "nb", "n", "b", "der", "die", "das", "den", "dem", "des", "ein", "eine", "einer",
        "eines", "einem", "einen", "je", "à", "a", "z", "bsp", "etwa", "gut", "sehr", "auch", "sowie", "nicht",
        // Zubereitung und Beschreibung
        "frisch", "frische", "frischer", "frisches", "fein", "feine", "grob", "grobe", "gehackt", "gehackte",
        "gewürfelt", "gewürfelte", "geschnitten", "geschnittene", "gerieben", "geriebene", "geriebener",
        "gemahlen", "gemahlener", "gemahlene", "getrocknet", "getrocknete", "getrockneter", "gekocht", "gekochte",
        "gegart", "geschält", "geschälte", "entkernt", "gepresst", "zerlassen", "weich", "weiche", "kalt",
        "kalte", "kaltes", "warm", "warme", "lauwarm", "tiefgekühlt", "tk", "rot", "rote", "roter", "rotes",
        "grün", "grüne", "grüner", "grünes", "gelb", "gelbe", "gelber", "weiß", "weiße", "weißer", "weißes",
        "schwarz", "schwarze", "schwarzer", "bunte", "bunter", "glatte", "krause", "mehlig", "mehligkochend",
        "festkochend", "vorwiegend", "reif", "reife", "reifer", "mager", "magerer", "light", "bio", "natur",
        "zimmerwarm", "zimmertemperatur", "streichfähig", "flüssig", "flüssige", "abgerieben", "abgetropft",
        "unbehandelt", "unbehandelte", "ungesalzen", "gesalzen", "gesalzene", "geräuchert", "frisch", "nach",
        "zum", "braten", "bestreuen", "garnieren", "anbraten", "einfetten", "würzen", "abschmecken",
        "dekorieren", "servieren", "kochen", "backen",
    )

    /** Grundvorrat (als Stamm, wie [stem] ihn liefert). */
    private val pantry = setOf(
        "salz", "meersalz", "pfeffer", "öl", "olivenöl", "rapsöl", "sonnenblumenöl", "speiseöl", "pflanzenöl",
        "bratöl", "wass", "wasser", "zuck", "zucker", "mehl", "weizenmehl", "essig", "balsamico", "backpulv",
        "natron", "paprikapulv", "currypulv", "curry", "zimt", "muskat", "muskatnuss", "oregano",
        "majoran", "kreuzkümmel", "kümmel", "chili", "chiliflock", "cayennepfeff", "lorbeerblatt",
        "lorbeerblätt", "brüh", "gemüsebrüh", "hühnerbrüh", "fleischbrüh", "rinderbrüh", "speisestärk",
        "stärk", "puderzuck", "vanillezuck", "senf", "sojasauc", "sojasoß", "worcestersauc", "honig",
        "fett", "pfefferkörn", "nelk", "piment", "kurkuma", "thymian", "rosmarin", "kräut", "gewürz",
        "salzwass", "hefe", "trockenhef", "kakao", "kakaopulv",
    ).map(::stem).toSet()

    /** Gleichbedeutende Wörter auf einen Namen bringen (vor dem Kürzen). */
    private val synonyms = mapOf(
        "ei" to "eier",
        "hack" to "hackfleisch",
        "gehacktes" to "hackfleisch",
        "rinderhack" to "rinderhackfleisch",
        "schweinehack" to "schweinehackfleisch",
        "rahm" to "sahne",
        "schlagrahm" to "schlagsahne",
        "obers" to "sahne",
        "schlagobers" to "schlagsahne",
        "erdäpfel" to "kartoffeln",
        "paradeiser" to "tomaten",
        "topfen" to "quark",
        "porree" to "lauch",
        "möhre" to "karotte",
        "möhren" to "karotten",
        "mohrrübe" to "karotte",
        "mohrrüben" to "karotten",
        "hendl" to "hähnchen",
        "huhn" to "hähnchen",
        "hühnchen" to "hähnchen",
        "hühnerbrust" to "hähnchenbrust",
        "faschiertes" to "hackfleisch",
        "semmel" to "brötchen",
        "semmeln" to "brötchen",
        "schrippe" to "brötchen",
        "schrippen" to "brötchen",
        "pilze" to "champignons",
        "zucchino" to "zucchini",
        "frühlingszwiebel" to "lauchzwiebel",
        "frühlingszwiebeln" to "lauchzwiebeln",
    )
}
