package com.paeffgen.mhdsafety.data

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import kotlin.random.Random

/**
 * Gemeinsame Liste in Firestore, Sammlung `items`.
 *
 * Firestore hält eine Kopie auf dem Handy: Lesen und Schreiben klappt auch offline,
 * abgeglichen wird, sobald wieder Netz da ist. Deshalb wird auf Schreibvorgänge
 * nicht gewartet (das würde offline hängen).
 */
class CloudFoodStore {
    private val items = FirebaseFirestore.getInstance().collection("items")

    fun observeAll(): Flow<List<FoodItem>> = callbackFlow {
        val registration = items.addSnapshotListener { snapshot, error ->
            if (error != null) {
                FirebaseCrashlytics.getInstance().recordException(error)
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snapshot?.documents.orEmpty().mapNotNull(::toItem))
        }
        awaitClose { registration.remove() }
    }

    suspend fun getAll(): List<FoodItem> = items.get().await().documents.mapNotNull(::toItem)

    suspend fun getById(id: Long): FoodItem? = toItem(items.document(id.toString()).get().await())

    /** Speichert; neue Einträge (id 0) bekommen eine zufällige id. */
    fun save(item: FoodItem) {
        val withId = if (item.id == 0L) item.copy(id = newId()) else item
        items.document(withId.id.toString()).set(toMap(withId))
    }

    fun delete(item: FoodItem) {
        items.document(item.id.toString()).delete()
    }

    companion object {
        /** Zufällig statt hochgezählt, damit sich zwei Handys nicht in die Quere kommen. */
        fun newId(): Long = Random.nextLong(1, Long.MAX_VALUE)

        private fun toMap(item: FoodItem): Map<String, Any?> = mapOf(
            "name" to item.name,
            "date" to item.date.toEpochDay(),
            "dateType" to item.dateType.name,
            "barcode" to item.barcode,
            "category" to item.category.name,
            "ownUseBy" to item.ownUseBy?.toEpochDay(),
        )

        private fun toItem(doc: DocumentSnapshot): FoodItem? {
            if (!doc.exists()) return null
            return FoodItem(
                id = doc.id.toLongOrNull() ?: return null,
                name = doc.getString("name") ?: return null,
                date = doc.getLong("date")?.let(LocalDate::ofEpochDay) ?: return null,
                dateType = doc.getString("dateType")
                    ?.let { runCatching { DateType.valueOf(it) }.getOrNull() } ?: DateType.BEST_BEFORE,
                barcode = doc.getString("barcode"),
                category = doc.getString("category")
                    ?.let { runCatching { Category.valueOf(it) }.getOrNull() } ?: Category.OTHER,
                ownUseBy = doc.getLong("ownUseBy")?.let(LocalDate::ofEpochDay),
            )
        }
    }
}
