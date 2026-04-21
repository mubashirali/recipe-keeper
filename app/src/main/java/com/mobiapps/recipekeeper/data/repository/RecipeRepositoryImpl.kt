package com.mobiapps.recipekeeper.data.repository

import com.mobiapps.recipekeeper.data.local.dao.IngredientDao
import com.mobiapps.recipekeeper.data.local.dao.RecipeDao
import com.mobiapps.recipekeeper.data.local.entity.IngredientEntity
import com.mobiapps.recipekeeper.data.local.entity.RecipeEntity
import com.mobiapps.recipekeeper.data.local.entity.RecipeWithIngredients
import com.mobiapps.recipekeeper.domain.model.Ingredient
import com.mobiapps.recipekeeper.domain.model.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RecipeRepositoryImpl @Inject constructor(
    private val recipeDao: RecipeDao,
    private val ingredientDao: IngredientDao
) : RecipeRepository {

    override fun getAllRecipes(): Flow<List<Recipe>> =
        recipeDao.getAllRecipesWithIngredients().map { it.map(RecipeWithIngredients::toDomain) }

    override fun getRecipeById(id: String): Flow<Recipe?> =
        recipeDao.getRecipeById(id).map { it?.toDomain() }

    override suspend fun saveRecipe(recipe: Recipe) {
        ingredientDao.deleteIngredientsForRecipe(recipe.id)
        recipeDao.insertRecipe(recipe.toEntity())
        ingredientDao.insertIngredients(recipe.ingredients.map(Ingredient::toEntity))
    }

    override suspend fun deleteRecipe(recipe: Recipe) {
        recipeDao.deleteRecipe(recipe.toEntity())
    }
}

// --- Mapping extensions (private to this file) ---

private fun RecipeWithIngredients.toDomain() = Recipe(
    id = recipe.id,
    userId = recipe.userId,
    title = recipe.title,
    description = recipe.description,
    prepTimeMinutes = recipe.prepTimeMinutes,
    servings = recipe.servings,
    instructions = recipe.instructions,  // List<String>; Room invokes Converters.kt
    tags = recipe.tags,                   // List<String>; Room invokes Converters.kt
    ingredients = ingredients.map(IngredientEntity::toDomain),
    createdAt = recipe.createdAt,
    updatedAt = recipe.updatedAt
)

private fun IngredientEntity.toDomain() = Ingredient(
    id = id, recipeId = recipeId, name = name,
    quantity = quantity, unit = unit, dietaryTag = dietaryTag
)

private fun Recipe.toEntity() = RecipeEntity(
    id = id, userId = userId, title = title, description = description,
    prepTimeMinutes = prepTimeMinutes, servings = servings,
    instructions = instructions,  // List<String>; Room invokes Converters.kt
    tags = tags,                   // List<String>; Room invokes Converters.kt
    createdAt = createdAt, updatedAt = updatedAt
)

private fun Ingredient.toEntity() = IngredientEntity(
    id = id, recipeId = recipeId, name = name,
    quantity = quantity, unit = unit, dietaryTag = dietaryTag
)
