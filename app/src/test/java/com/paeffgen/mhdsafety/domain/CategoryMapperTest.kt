package com.paeffgen.mhdsafety.domain

import com.paeffgen.mhdsafety.data.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryMapperTest {
    @Test
    fun mostSpecificTagWins() {
        val tags = listOf("en:dairies", "en:fermented-foods", "en:cheeses", "en:hard-cheeses")
        assertEquals(Category.CHEESE, CategoryMapper.map(tags))
    }

    @Test
    fun fruitYogurtIsDairy() {
        assertEquals(Category.DAIRY, CategoryMapper.map(listOf("en:dairies", "en:yogurts", "en:fruit-yogurts")))
    }

    @Test
    fun genericPlantBasedTagIsIgnored() {
        assertEquals(
            Category.FRUIT_VEG,
            CategoryMapper.map(listOf("en:plant-based-foods-and-beverages", "en:plant-based-foods", "en:fruits-and-vegetables-based-foods", "en:fresh-vegetables")),
        )
        assertNull(CategoryMapper.map(listOf("en:plant-based-foods-and-beverages")))
    }

    @Test
    fun nonEnglishTags() {
        assertEquals(Category.SWEETS, CategoryMapper.map(listOf("nl:Chocolade", "nl:Koeken/Chocolade/Snoep")))
        assertEquals(Category.DAIRY, CategoryMapper.map(listOf("de:Milchprodukte")))
    }

    @Test
    fun frozenPizza() {
        assertEquals(Category.FROZEN, CategoryMapper.map(listOf("en:meals", "en:pizzas", "en:frozen-pizzas")))
    }
}
