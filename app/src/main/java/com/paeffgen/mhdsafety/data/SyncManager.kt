package com.paeffgen.mhdsafety.data

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.DateFormat
import java.util.Date

/**
 * Gleicht den Bestand und die Rezepte auf dem Handy mit dem Konto auf dem Server ab.
 *
 * Alles wird zuerst in Room gespeichert (die App funktioniert offline). Geänderte Einträge sind
 * als "dirty" markiert, Löschungen liegen als [Tombstone] bereit. Beim Abgleich gehen diese zum
 * Server, zurück kommt alles, was sich seit dem letzten Stand ([AuthStore.cursor]) geändert hat –
 * auch von anderen Geräten desselben Kontos. Konflikte: neuester Stand gewinnt.
 */
class SyncManager(
    private val db: AppDatabase,
    private val account: CloudAccount,
    private val store: AuthStore,
    private val scope: CoroutineScope,
) {
    private val foodDao = db.foodDao()
    private val recipeDao = db.recipeDao()
    private val tombstones = db.tombstoneDao()
    private val mutex = Mutex()
    private var pending: Job? = null

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    /** Läuft, solange jemand angemeldet ist: gleich nach der Anmeldung und dann jede Minute. */
    fun start() {
        scope.launch {
            account.email.collectLatest { email ->
                while (email != null) {
                    syncNow()
                    delay(60_000)
                }
            }
        }
    }

    /** Nach einer Änderung kurz warten (mehrere Änderungen auf einmal) und dann abgleichen. */
    fun requestSync() {
        if (!account.isSignedIn) return
        pending?.cancel()
        pending = scope.launch {
            delay(1_500)
            syncNow()
        }
    }

    /**
     * Nach einer Anmeldung: Gehören die Daten auf dem Handy noch keinem Konto, werden sie
     * hochgeladen (mit neuen, zufälligen ids). Gehören sie einem anderen Konto, werden sie verworfen.
     * @return Anzahl der Einträge und Rezepte, die hochgeladen werden
     */
    suspend fun adoptLocalData(accountId: Long): Int = mutex.withLock {
        val owner = store.dataOwner
        if (owner == accountId) return 0
        if (owner != null) {
            clearLocalLocked()
            store.dataOwner = accountId
            return 0
        }
        val now = System.currentTimeMillis()
        var count = 0
        db.withTransaction {
            foodDao.getAll().forEach {
                foodDao.delete(it)
                foodDao.upsert(it.copy(id = SyncCodec.newId(), updatedAt = now, dirty = true))
                count++
            }
            recipeDao.getAll().forEach {
                recipeDao.delete(it)
                recipeDao.upsert(it.copy(id = SyncCodec.newId(), updatedAt = now, dirty = true))
                count++
            }
            tombstones.clear()
        }
        store.setCursor(Tombstone.ITEMS, 0)
        store.setCursor(Tombstone.RECIPES, 0)
        store.dataOwner = accountId
        count
    }

    /** Beim Abmelden: Die Daten gehören dem Konto und verschwinden vom Handy (auf dem Server bleiben sie). */
    suspend fun clearLocal() = mutex.withLock { clearLocalLocked() }

    private suspend fun clearLocalLocked() {
        db.withTransaction {
            foodDao.clear()
            recipeDao.clear()
            tombstones.clear()
        }
        store.clearSyncState()
    }

    suspend fun syncNow(): Boolean {
        if (!account.isSignedIn || store.dataOwner == null) return false
        return mutex.withLock {
            try {
                syncKind(Tombstone.ITEMS)
                syncKind(Tombstone.RECIPES)
                _status.value = "Abgeglichen um ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())}"
                true
            } catch (e: SessionExpired) {
                _status.value = e.message
                false
            } catch (e: IOException) {
                _status.value = "Nicht abgeglichen: ${e.message ?: "Server nicht erreichbar"}"
                false
            }
        }
    }

    private suspend fun syncKind(kind: String) {
        val dirtyFoods = if (kind == Tombstone.ITEMS) foodDao.getDirty() else emptyList()
        val dirtyRecipes = if (kind == Tombstone.RECIPES) recipeDao.getDirty() else emptyList()
        val tombs = tombstones.get(kind)
        val outgoing = dirtyFoods.map(SyncCodec::foodToJson) + dirtyRecipes.map(SyncCodec::recipeToJson) +
            tombs.map(SyncCodec::tombstoneToJson)

        var chunks = outgoing.chunked(500).ifEmpty { listOf(emptyList()) }
        var index = 0
        while (index < chunks.size) {
            val body = JSONObject().put("since", store.cursor(kind)).put("changes", JSONArray(chunks[index]))
            val r = account.authorized("POST", "/sync/$kind", body)
            if (!r.ok || r.body == null) throw IOException(r.message())
            applyIncoming(kind, r.body.getJSONArray("changes"))
            store.setCursor(kind, r.body.getLong("cursor"))
            index++
            // Server hat mehr als eine Seite: weiterholen, ohne erneut zu senden.
            if (index == chunks.size && r.body.optBoolean("more")) chunks = chunks + listOf(emptyList())
        }

        // Hochgeladenes als erledigt markieren – nur, wenn es seitdem nicht erneut geändert wurde.
        dirtyFoods.forEach { foodDao.markClean(it.id, it.updatedAt) }
        dirtyRecipes.forEach { recipeDao.markClean(it.id, it.updatedAt) }
        tombs.forEach { tombstones.remove(kind, it.id, it.updatedAt) }
    }

    private suspend fun applyIncoming(kind: String, changes: JSONArray) {
        if (changes.length() == 0) return
        val tombsById = tombstones.get(kind).associateBy { it.id }
        db.withTransaction {
            for (i in 0 until changes.length()) {
                val c = changes.getJSONObject(i)
                val id = c.getLong("id")
                val remoteAt = c.getLong("updatedAt")
                val deleted = c.optBoolean("deleted")
                if (kind == Tombstone.ITEMS) {
                    val local = foodDao.getById(id)
                    if (!SyncCodec.remoteWins(local?.updatedAt, local?.dirty == true, tombsById[id]?.updatedAt, remoteAt)) continue
                    if (deleted) foodDao.deleteById(id) else SyncCodec.foodFromJson(c)?.let { foodDao.upsert(it) }
                } else {
                    val local = recipeDao.getById(id)
                    if (!SyncCodec.remoteWins(local?.updatedAt, local?.dirty == true, tombsById[id]?.updatedAt, remoteAt)) continue
                    if (deleted) recipeDao.deleteById(id) else recipeDao.upsert(SyncCodec.recipeFromJson(c))
                }
            }
        }
    }
}
