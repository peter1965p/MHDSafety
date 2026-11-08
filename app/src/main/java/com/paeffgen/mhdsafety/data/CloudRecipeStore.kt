package com.paeffgen.mhdsafety.data

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Gemeinsame Rezepte in Firestore, Sammlung `recipes`. Funktioniert wie [CloudFoodStore]. */
class CloudRecipeStore {
    private val recipes = FirebaseFirestore.getInstance().collection("recipes")

    fun observeAll(): Flow<List<Recipe>> = callbackFlow {
        val registration = recipes.addSnapshotListener { snapshot, error ->
            if (error != null) {
                FirebaseCrashlytics.getInstance().recordException(error)
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snapshot?.documents.orEmpty().mapNotNull(::toRecipe).sortedBy { it.name.lowercase() })
        }
        awaitClose { registration.remove() }
    }

    fun save(recipe: Recipe) {
        val withId = if (recipe.id == 0L) recipe.copy(id = CloudFoodStore.newId()) else recipe
        recipes.document(withId.id.toString()).set(toMap(withId))
    }

    fun delete(recipe: Recipe) {
        recipes.document(recipe.id.toString()).delete()
    }

    private companion object {
        fun toMap(recipe: Recipe): Map<String, Any?> = mapOf(
            "url" to recipe.url,
            "name" to recipe.name,
            "ingredients" to recipe.ingredients,
            "servings" to recipe.servings,
            "imageUrl" to recipe.imageUrl,
            "createdAt" to recipe.createdAt,
        )

        fun toRecipe(doc: DocumentSnapshot): Recipe? {
            if (!doc.exists()) return null
            return Recipe(
                id = doc.id.toLongOrNull() ?: return null,
                url = doc.getString("url") ?: return null,
                name = doc.getString("name") ?: return null,
                ingredients = (doc.get("ingredients") as? List<*>).orEmpty().filterIsInstance<String>(),
                servings = doc.getString("servings"),
                imageUrl = doc.getString("imageUrl"),
                createdAt = doc.getLong("createdAt") ?: 0L,
            )
        }
    }
}
