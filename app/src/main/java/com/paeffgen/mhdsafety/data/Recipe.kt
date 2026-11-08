package com.paeffgen.mhdsafety.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Ein gespeichertes Rezept aus dem Netz.
 * Gemerkt werden nur Link, Name und Zutaten – die Zubereitung liest man auf der Originalseite.
 */
@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val name: String,
    /** Zutatenzeilen wie auf der Seite, z. B. "200 g Schlagsahne". */
    val ingredients: List<String>,
    /** Portionen, wie die Seite sie angibt, z. B. "4 Portionen". */
    val servings: String? = null,
    val imageUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes")
    suspend fun getAll(): List<Recipe>

    @Upsert
    suspend fun upsert(recipe: Recipe): Long

    @Delete
    suspend fun delete(recipe: Recipe)

    @Delete
    suspend fun deleteAll(recipes: List<Recipe>)
}
