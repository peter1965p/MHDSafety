package com.paeffgen.mhdsafety.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Welche Art von Datum auf der Packung steht. */
enum class DateType {
    /** "Mindestens haltbar bis" – danach meist noch genießbar, Puffer wird draufgerechnet. */
    BEST_BEFORE,

    /** "Zu verbrauchen bis" – danach nicht mehr essen, kein Puffer. */
    USE_BY,
}

@Entity(tableName = "food_items")
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: LocalDate,
    val dateType: DateType = DateType.BEST_BEFORE,
    val barcode: String? = null,
    @ColumnInfo(defaultValue = "OTHER") val category: Category = Category.OTHER,
)
