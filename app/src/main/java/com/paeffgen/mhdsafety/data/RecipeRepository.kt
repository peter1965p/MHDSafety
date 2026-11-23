package com.paeffgen.mhdsafety.data

import kotlinx.coroutines.flow.Flow

/** Rezepte auf dem Handy, abgeglichen wie [FoodRepository]. */
class RecipeRepository(
    private val local: RecipeDao,
    private val tombstones: TombstoneDao,
    private val account: CloudAccount,
    private val sync: () -> SyncManager,
) {
    val items: Flow<List<Recipe>> = local.observeAll()

    suspend fun save(recipe: Recipe) {
        val id = if (recipe.id == 0L) SyncCodec.newId() else recipe.id
        local.upsert(recipe.copy(id = id, updatedAt = FoodRepository.stamp(recipe.updatedAt), dirty = true))
        sync().requestSync()
    }

    suspend fun delete(recipe: Recipe) {
        local.delete(recipe)
        if (account.isSignedIn) {
            tombstones.upsert(Tombstone(Tombstone.RECIPES, recipe.id, FoodRepository.stamp(recipe.updatedAt)))
            sync().requestSync()
        }
    }
}
