package com.paeffgen.mhdsafety.domain

import com.paeffgen.mhdsafety.data.Recipe
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Liest ein Rezept aus einer Webseite.
 *
 * Fast alle Rezeptseiten (Chefkoch, Lecker, EatSmarter …) haben für Suchmaschinen einen
 * unsichtbaren Block `<script type="application/ld+json">` nach schema.org/Recipe.
 * Darin stehen Name, Portionen, Bild und die Zutaten sauber als Liste.
 */
object RecipeParser {
    private val ldJsonScript = Regex(
        """<script[^>]*type\s*=\s*["']?application/ld\+json["']?[^>]*>(.*?)</script>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val urlInText = Regex("""https?://\S+""")

    /** Holt den ersten Link aus einem geteilten Text wie "Schau mal: https://…". */
    fun extractUrl(text: String): String? =
        urlInText.find(text)?.value?.trimEnd('.', ',', ')', '"', '\'', '>')

    fun parse(html: String, url: String): Recipe? {
        for (match in ldJsonScript.findAll(html)) {
            // Manche Seiten haben echte Zeilenumbrüche in Texten, das mag kein JSON-Parser.
            val json = match.groupValues[1].replace(Regex("[\\r\\n\\t]+"), " ").trim()
            val root = runCatching { JSONTokener(json).nextValue() }.getOrNull() ?: continue
            val node = findRecipe(root) ?: continue
            toRecipe(node, url)?.let { return it }
        }
        return null
    }

    private fun findRecipe(node: Any?): JSONObject? = when (node) {
        is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { findRecipe(node.opt(it)) }
        is JSONObject -> when {
            isRecipe(node) -> node
            else -> findRecipe(node.opt("@graph")) ?: findRecipe(node.opt("mainEntity"))
        }
        else -> null
    }

    private fun isRecipe(node: JSONObject): Boolean {
        fun isRecipeType(value: Any?) = value is String && value.substringAfterLast(':').substringAfterLast('/')
            .equals("Recipe", ignoreCase = true)
        return when (val type = node.opt("@type")) {
            is JSONArray -> (0 until type.length()).any { isRecipeType(type.opt(it)) }
            else -> isRecipeType(type)
        }
    }

    private fun toRecipe(node: JSONObject, url: String): Recipe? {
        var name = node.optString("name").cleanText().ifEmpty { return null }
        // Chefkoch hängt den Benutzernamen an: "Paprika-Hackfleisch-Pfanne von jofranzke"
        if ("chefkoch.de" in url) name = name.replace(Regex("\\s+von\\s+\\S+$"), "").ifEmpty { name }
        val ingredients = strings(node.opt("recipeIngredient") ?: node.opt("ingredients"))
            .map { it.cleanText() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (ingredients.isEmpty()) return null
        return Recipe(
            url = url,
            name = name,
            ingredients = ingredients,
            servings = strings(node.opt("recipeYield")).firstOrNull()?.cleanText()?.ifEmpty { null }
                ?.let { if (it.all(Char::isDigit)) "$it Portionen" else it },
            imageUrl = imageUrl(node.opt("image")),
        )
    }

    private fun strings(value: Any?): List<String> = when (value) {
        is JSONArray -> (0 until value.length()).flatMap { strings(value.opt(it)) }
        is String -> listOf(value)
        is Number -> listOf(value.toString())
        else -> emptyList()
    }

    private fun imageUrl(value: Any?): String? = when (value) {
        is String -> value.takeIf { it.startsWith("http") }
        is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { imageUrl(value.opt(it)) }
        is JSONObject -> imageUrl(value.opt("url"))
        else -> null
    }

    private val tag = Regex("<[^>]+>")
    private val numericEntity = Regex("&#(x?)([0-9a-fA-F]+);")
    private val namedEntities = mapOf(
        "&amp;" to "&", "&quot;" to "\"", "&apos;" to "'", "&lt;" to "<", "&gt;" to ">",
        "&nbsp;" to " ", "&frac12;" to "½", "&frac14;" to "¼", "&frac34;" to "¾",
        "&auml;" to "ä", "&ouml;" to "ö", "&uuml;" to "ü", "&Auml;" to "Ä", "&Ouml;" to "Ö",
        "&Uuml;" to "Ü", "&szlig;" to "ß",
    )

    /** HTML-Reste entfernen, Entities auflösen, Leerraum zusammenziehen. */
    internal fun String.cleanText(): String {
        var text = replace(tag, " ")
        text = numericEntity.replace(text) { m ->
            val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
            code?.let { String(Character.toChars(it)) } ?: m.value
        }
        namedEntities.forEach { (entity, char) -> text = text.replace(entity, char) }
        return text.replace(' ', ' ').replace(Regex("\\s+"), " ").trim()
    }
}
