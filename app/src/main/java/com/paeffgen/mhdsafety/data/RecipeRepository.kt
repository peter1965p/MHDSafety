package com.paeffgen.mhdsafety.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Weiche wie bei [FoodRepository]: angemeldet -> gemeinsame Rezepte in der Cloud, sonst lokal. */
class RecipeRepository(
    private val local: RecipeDao,
    private val cloud: CloudRecipeStore,
    private val account: CloudAccount,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val items: Flow<List<Recipe>> = account.email
        .map { it != null }
        .distinctUntilChanged()
        .flatMapLatest { signedIn -> if (signedIn) cloud.observeAll() else local.observeAll() }

    suspend fun save(recipe: Recipe) {
        if (account.isSignedIn) cloud.save(recipe) else local.upsert(recipe)
    }

    suspend fun delete(recipe: Recipe) {
        if (account.isSignedIn) cloud.delete(recipe) else local.delete(recipe)
    }

    /** Nach dem Anmelden: Rezepte vom Handy in die gemeinsame Sammlung schieben. */
    suspend fun moveLocalToCloud(): Int {
        val localRecipes = local.getAll()
        localRecipes.forEach { cloud.save(it.copy(id = CloudFoodStore.newId())) }
        local.deleteAll(localRecipes)
        return localRecipes.size
    }
}
