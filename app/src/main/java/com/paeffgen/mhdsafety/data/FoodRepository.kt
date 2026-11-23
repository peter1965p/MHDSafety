package com.paeffgen.mhdsafety.data

import kotlinx.coroutines.flow.Flow

/**
 * Bestand auf dem Handy (Room). Mit Anmeldung gleicht [SyncManager] ihn im Hintergrund mit dem Konto ab;
 * ohne Anmeldung bleibt alles nur auf diesem Gerät. Die App funktioniert so auch offline.
 */
class FoodRepository(
    private val local: FoodDao,
    private val tombstones: TombstoneDao,
    private val account: CloudAccount,
    private val sync: () -> SyncManager,
) {
    val items: Flow<List<FoodItem>> = local.observeAll()

    suspend fun getAll(): List<FoodItem> = local.getAll()

    suspend fun getById(id: Long): FoodItem? = local.getById(id)

    /** Speichert; neue Einträge (id 0) bekommen eine zufällige id, damit Geräte sich nicht in die Quere kommen. */
    suspend fun save(item: FoodItem) {
        val id = if (item.id == 0L) SyncCodec.newId() else item.id
        local.upsert(item.copy(id = id, updatedAt = stamp(item.updatedAt), dirty = true))
        sync().requestSync()
    }

    suspend fun delete(item: FoodItem) = deleteAll(listOf(item))

    suspend fun deleteAll(items: List<FoodItem>) {
        if (items.isEmpty()) return
        local.deleteAll(items)
        if (account.isSignedIn) {
            items.forEach { tombstones.upsert(Tombstone(Tombstone.ITEMS, it.id, stamp(it.updatedAt))) }
            sync().requestSync()
        }
    }

    companion object {
        /** Jetzt, aber immer später als der bisherige Stand (falls die Uhr des Handys nachgeht). */
        fun stamp(previous: Long): Long = maxOf(System.currentTimeMillis(), previous + 1)
    }
}
