package com.paeffgen.mhdsafety.data

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromEpochDay(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    /** Zutatenlisten: eine Zeile pro Zutat. */
    @TypeConverter
    fun fromLines(value: String): List<String> = value.split('\n').filter { it.isNotBlank() }

    @TypeConverter
    fun toLines(lines: List<String>): String = lines.joinToString("\n") { it.replace('\n', ' ') }
}
