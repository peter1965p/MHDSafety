package com.paeffgen.mhdsafety.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Weiche: angemeldet -> gemeinsame Liste in der Cloud, sonst nur lokal auf dem Handy.
 */
class FoodRepository(
    private val local: FoodDao,
    private val cloud: CloudFoodStore,
    private val account: CloudAccount,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val items: Flow<List<FoodItem>> = account.email
        .map { it != null }
        .distinctUntilChanged()
        .flatMapLatest { signedIn -> if (signedIn) cloud.observeAll() else local.observeAll() }

    suspend fun getAll(): List<FoodItem> = if (account.isSignedIn) cloud.getAll() else local.getAll()

    suspend fun getById(id: Long): FoodItem? = if (account.isSignedIn) cloud.getById(id) else local.getById(id)

    suspend fun save(item: FoodItem) {
        if (account.isSignedIn) cloud.save(item) else local.upsert(item)
    }

    suspend fun delete(item: FoodItem) {
        if (account.isSignedIn) cloud.delete(item) else local.delete(item)
    }

    suspend fun deleteAll(items: List<FoodItem>) {
        if (account.isSignedIn) items.forEach(cloud::delete) else local.deleteAll(items)
    }

    /** Nach dem Anmelden: was bisher nur auf dem Handy stand, in die gemeinsame Liste schieben. */
    suspend fun moveLocalToCloud(): Int {
        val localItems = local.getAll()
        localItems.forEach { cloud.save(it.copy(id = CloudFoodStore.newId())) }
        local.deleteAll(localItems)
        return localItems.size
    }
}
