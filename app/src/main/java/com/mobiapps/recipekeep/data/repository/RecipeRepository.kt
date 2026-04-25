package com.mobiapps.recipekeep.data.repository

import com.mobiapps.recipekeep.domain.model.Recipe
import kotlinx.coroutines.flow.Flow

interface RecipeRepository {
    fun getAllRecipes(): Flow<List<Recipe>>
    fun getRecipeById(id: String): Flow<Recipe?>
    suspend fun saveRecipe(recipe: Recipe)
    suspend fun deleteRecipe(recipe: Recipe)
}
