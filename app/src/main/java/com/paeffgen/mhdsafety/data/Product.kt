package com.paeffgen.mhdsafety.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.paeffgen.mhdsafety.domain.Quantity
import com.paeffgen.mhdsafety.domain.QuantityUnit

/**
 * Gedächtnis für schon mal gescannte Produkte: beim nächsten Einkauf
 * kommen Name und Kategorie sofort und ohne Internet.
 */
@Entity(tableName = "products")
data class Product(
    @PrimaryKey val barcode: String,
    val name: String,
    val category: Category,
    /** Packungsgröße (nicht die Restmenge). */
    val quantity: Double? = null,
    val unit: QuantityUnit? = null,
) {
    val packageSize: Quantity?
        get() = if (quantity != null && unit != null) Quantity(quantity, unit) else null
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE barcode = :barcode")
    suspend fun get(barcode: String): Product?

    @Upsert
    suspend fun upsert(product: Product)
}
