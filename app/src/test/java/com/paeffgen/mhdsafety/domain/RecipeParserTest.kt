package com.paeffgen.mhdsafety.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeParserTest {
    private val url = "https://www.example.de/rezept/123"

    @Test
    fun plainRecipe() {
        val html = """
            <html><head>
            <script type="application/ld+json">{"@context":"https://schema.org","@type":"WebSite","name":"Kochseite"}</script>
            <script type="application/ld+json">
            {"@context":"https://schema.org","@type":"Recipe","name":"Paprika-Sahne-Hack-Pfanne",
             "recipeYield":"4 Portionen","image":["https://img.example.de/1.jpg"],
             "recipeIngredient":["500 g Hackfleisch","200 ml Schlagsahne","2  Paprikaschoten, rot","Salz &amp; Pfeffer"]}
            </script></head><body></body></html>
        """.trimIndent()
        val recipe = RecipeParser.parse(html, url)!!
        assertEquals("Paprika-Sahne-Hack-Pfanne", recipe.name)
        assertEquals("4 Portionen", recipe.servings)
        assertEquals("https://img.example.de/1.jpg", recipe.imageUrl)
        assertEquals(listOf("500 g Hackfleisch", "200 ml Schlagsahne", "2 Paprikaschoten, rot", "Salz & Pfeffer"), recipe.ingredients)
        assertEquals(url, recipe.url)
    }

    @Test
    fun recipeInsideGraphWithTypeArray() {
        val html = """
            <script type='application/ld+json'>{"@graph":[{"@type":"Organization","name":"X"},
            {"@type":["Recipe","NewsArticle"],"name":"Kartoffelsuppe","recipeYield":4,
             "image":{"@type":"ImageObject","url":"https://img/2.jpg"},
             "recipeIngredient":["1 kg Kartoffeln","1 Zwiebel"]}]}</script>
        """.trimIndent()
        val recipe = RecipeParser.parse(html, url)!!
        assertEquals("Kartoffelsuppe", recipe.name)
        assertEquals("4 Portionen", recipe.servings)
        assertEquals("https://img/2.jpg", recipe.imageUrl)
        assertEquals(listOf("1 kg Kartoffeln", "1 Zwiebel"), recipe.ingredients)
    }

    @Test
    fun rawLineBreaksInJsonAreTolerated() {
        val html = "<script type=\"application/ld+json\">{\"@type\":\"Recipe\",\"name\":\"Kuchen\n mit Streuseln\"," +
            "\"recipeIngredient\":[\"250 g Mehl\"]}</script>"
        assertEquals("Kuchen mit Streuseln", RecipeParser.parse(html, url)!!.name)
    }

    @Test
    fun numericEntitiesAreDecoded() {
        val html = "<script type=\"application/ld+json\">{\"@type\":\"Recipe\",\"name\":\"K&#228;sesp&#xE4;tzle\"," +
            "\"recipeIngredient\":[\"&frac12; Bund Petersilie\"]}</script>"
        val recipe = RecipeParser.parse(html, url)!!
        assertEquals("Käsespätzle", recipe.name)
        assertEquals(listOf("½ Bund Petersilie"), recipe.ingredients)
    }

    @Test
    fun chefkochAuthorIsRemoved() {
        val html = "<script type=\"application/ld+json\">{\"@type\":\"Recipe\",\"name\":\"Paprika-Hackfleisch-Pfanne von jofranzke\"," +
            "\"recipeIngredient\":[\"500 g Hackfleisch\"]}</script>"
        assertEquals("Paprika-Hackfleisch-Pfanne", RecipeParser.parse(html, "https://www.chefkoch.de/rezepte/1/x.html")!!.name)
        assertEquals("Paprika-Hackfleisch-Pfanne von jofranzke", RecipeParser.parse(html, url)!!.name)
    }

    @Test
    fun noRecipeOnPage() {
        assertNull(RecipeParser.parse("<html><body>Hallo</body></html>", url))
        assertNull(
            RecipeParser.parse("<script type=\"application/ld+json\">{\"@type\":\"Article\",\"name\":\"News\"}</script>", url),
        )
    }

    @Test
    fun brokenJsonIsSkipped() {
        val html = "<script type=\"application/ld+json\">{kaputt</script>" +
            "<script type=\"application/ld+json\">{\"@type\":\"Recipe\",\"name\":\"Gut\",\"recipeIngredient\":[\"1 Ei\"]}</script>"
        assertEquals("Gut", RecipeParser.parse(html, url)!!.name)
    }

    @Test
    fun extractUrlFromSharedText() {
        assertEquals(
            "https://www.chefkoch.de/rezepte/123/Gulasch.html",
            RecipeParser.extractUrl("Schau dir das an: https://www.chefkoch.de/rezepte/123/Gulasch.html."),
        )
        assertNull(RecipeParser.extractUrl("Kein Link hier"))
    }
}
