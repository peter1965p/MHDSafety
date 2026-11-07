package com.paeffgen.mhdsafety.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Gedächtnis für schon mal gescannte Produkte: beim nächsten Einkauf
 * kommen Name und Kategorie sofort und ohne Internet.
 */
@Entity(tableName = "products")
data class Product(
    @PrimaryKey val barcode: String,
    val name: String,
    val category: Category,
)

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE barcode = :barcode")
    suspend fun get(barcode: String): Product?

    @Upsert
    suspend fun upsert(product: Product)
}
