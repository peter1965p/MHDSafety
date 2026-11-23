package com.paeffgen.mhdsafety.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert

/**
 * Merkzettel für eine Löschung, die noch zum Server muss.
 * Der Server hebt sie als Tombstone auf, damit andere Geräte den Eintrag ebenfalls entfernen.
 */
@Entity(tableName = "sync_tombstones", primaryKeys = ["kind", "id"])
data class Tombstone(
    val kind: String,
    val id: Long,
    val updatedAt: Long,
) {
    companion object {
        const val ITEMS = "items"
        const val RECIPES = "recipes"
    }
}

@Dao
interface TombstoneDao {
    @Upsert
    suspend fun upsert(tombstone: Tombstone)

    @Query("SELECT * FROM sync_tombstones WHERE kind = :kind")
    suspend fun get(kind: String): List<Tombstone>

    @Query("DELETE FROM sync_tombstones WHERE kind = :kind AND id = :id AND updatedAt = :updatedAt")
    suspend fun remove(kind: String, id: Long, updatedAt: Long)

    @Query("DELETE FROM sync_tombstones")
    suspend fun clear()
}
