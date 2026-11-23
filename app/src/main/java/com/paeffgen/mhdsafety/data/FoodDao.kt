package com.paeffgen.mhdsafety.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_items ORDER BY date ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items")
    suspend fun getAll(): List<FoodItem>

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun getById(id: Long): FoodItem?

    @Upsert
    suspend fun upsert(item: FoodItem): Long

    @Delete
    suspend fun delete(item: FoodItem)

    @Delete
    suspend fun deleteAll(items: List<FoodItem>)

    @Query("SELECT * FROM food_items WHERE dirty = 1")
    suspend fun getDirty(): List<FoodItem>

    /** Nur wenn sich seit dem Hochladen nichts mehr geändert hat. */
    @Query("UPDATE food_items SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: Long, updatedAt: Long)

    @Query("DELETE FROM food_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM food_items")
    suspend fun clear()
}
