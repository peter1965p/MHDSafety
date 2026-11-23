package com.paeffgen.mhdsafety.data

import androidx.room.ColumnInfo
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
    /** Letzte Änderung in ms (Gerätezeit); beim Sync gewinnt der neueste Stand. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    /** Lokal geändert, noch nicht zum Server übertragen. */
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
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

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: Long): Recipe?

    @Query("SELECT * FROM recipes WHERE dirty = 1")
    suspend fun getDirty(): List<Recipe>

    /** Nur wenn sich seit dem Hochladen nichts mehr geändert hat. */
    @Query("UPDATE recipes SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: Long, updatedAt: Long)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recipes")
    suspend fun clear()
}
