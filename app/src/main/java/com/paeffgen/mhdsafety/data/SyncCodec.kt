package com.paeffgen.mhdsafety.data

import com.paeffgen.mhdsafety.domain.QuantityUnit
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.random.Random

/** Übersetzung zwischen Room-Einträgen und dem JSON der MHD-API, plus die Konfliktregel. */
object SyncCodec {
    /** Zufällige ids, damit sich zwei Geräte auf demselben Konto nicht in die Quere kommen. */
    fun newId(): Long = Random.nextLong(1, Long.MAX_VALUE)

    /**
     * Soll ein Stand vom Server den lokalen ersetzen? Neuester gewinnt.
     * Lokal noch nicht hochgeladene, neuere Änderungen (oder Löschungen) bleiben stehen.
     */
    fun remoteWins(localUpdatedAt: Long?, localDirty: Boolean, tombstoneUpdatedAt: Long?, remoteUpdatedAt: Long): Boolean {
        if (tombstoneUpdatedAt != null && tombstoneUpdatedAt > remoteUpdatedAt) return false
        if (localUpdatedAt != null && localDirty && localUpdatedAt > remoteUpdatedAt) return false
        return true
    }

    fun foodToJson(item: FoodItem): JSONObject = JSONObject()
        .put("id", item.id)
        .put("name", item.name)
        .put("date", item.date.toEpochDay())
        .put("dateType", item.dateType.name)
        .put("barcode", item.barcode ?: JSONObject.NULL)
        .put("category", item.category.name)
        .put("ownUseBy", item.ownUseBy?.toEpochDay() ?: JSONObject.NULL)
        .put("quantity", item.quantity ?: JSONObject.NULL)
        .put("unit", item.unit?.name ?: JSONObject.NULL)
        .put("updatedAt", item.updatedAt)
        .put("deleted", false)

    fun foodFromJson(o: JSONObject): FoodItem? {
        val date = o.optLongOrNull("date") ?: return null
        return FoodItem(
            id = o.getLong("id"),
            name = o.optString("name"),
            date = LocalDate.ofEpochDay(date),
            dateType = o.optStringOrNull("dateType")
                ?.let { runCatching { DateType.valueOf(it) }.getOrNull() } ?: DateType.BEST_BEFORE,
            barcode = o.optStringOrNull("barcode"),
            category = o.optStringOrNull("category")
                ?.let { runCatching { Category.valueOf(it) }.getOrNull() } ?: Category.OTHER,
            ownUseBy = o.optLongOrNull("ownUseBy")?.let(LocalDate::ofEpochDay),
            quantity = if (o.isNull("quantity") || !o.has("quantity")) null else o.getDouble("quantity"),
            unit = o.optStringOrNull("unit")?.let { runCatching { QuantityUnit.valueOf(it) }.getOrNull() },
            updatedAt = o.getLong("updatedAt"),
            dirty = false,
        )
    }

    fun recipeToJson(recipe: Recipe): JSONObject = JSONObject()
        .put("id", recipe.id)
        .put("url", recipe.url)
        .put("name", recipe.name)
        .put("ingredients", JSONArray(recipe.ingredients))
        .put("servings", recipe.servings ?: JSONObject.NULL)
        .put("imageUrl", recipe.imageUrl ?: JSONObject.NULL)
        .put("createdAt", recipe.createdAt)
        .put("updatedAt", recipe.updatedAt)
        .put("deleted", false)

    fun recipeFromJson(o: JSONObject): Recipe {
        val zutaten = o.optJSONArray("ingredients")
        return Recipe(
            id = o.getLong("id"),
            url = o.optString("url"),
            name = o.optString("name"),
            ingredients = (0 until (zutaten?.length() ?: 0)).map { zutaten!!.getString(it) },
            servings = o.optStringOrNull("servings"),
            imageUrl = o.optStringOrNull("imageUrl"),
            createdAt = o.optLongOrNull("createdAt") ?: 0L,
            updatedAt = o.getLong("updatedAt"),
            dirty = false,
        )
    }

    fun tombstoneToJson(t: Tombstone): JSONObject =
        JSONObject().put("id", t.id).put("updatedAt", t.updatedAt).put("deleted", true)

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key)
}
