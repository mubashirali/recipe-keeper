package com.mobiapps.recipekeeper.ui.creator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobiapps.recipekeeper.data.repository.RecipeRepository
import com.mobiapps.recipekeeper.domain.model.Ingredient
import com.mobiapps.recipekeeper.domain.model.Recipe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    object Success : SaveState()
    data class Error(val message: String) : SaveState()
}

@HiltViewModel
class RecipeCreatorViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _recipeToEdit = MutableStateFlow<Recipe?>(null)
    val recipeToEdit: StateFlow<Recipe?> = _recipeToEdit.asStateFlow()

    fun loadRecipe(recipeId: String) {
        viewModelScope.launch {
            val recipe = recipeRepository.getRecipeById(recipeId).firstOrNull()
            _recipeToEdit.value = recipe
        }
    }

    fun saveRecipe(
        title: String,
        description: String?,
        prepTimeMinutes: Int,
        servings: Int,
        ingredients: List<Pair<String, Pair<String, String>>>,
        instructions: List<String>,
        tags: List<String>,
        imagePath: String? = null,
        difficulty: String = "Medium"
    ) {
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            try {
                val existingRecipe = _recipeToEdit.value
                val recipeId = existingRecipe?.id ?: UUID.randomUUID().toString()
                
                val recipe = Recipe(
                    id = recipeId,
                    userId = existingRecipe?.userId,
                    title = title,
                    description = if (description == null) "" else description,
                    prepTimeMinutes = prepTimeMinutes,
                    servings = servings,
                    ingredients = ingredients.map { (quantity, unitName) ->
                        Ingredient(
                            id = UUID.randomUUID().toString(),
                            recipeId = recipeId,
                            quantity = quantity,
                            unit = unitName.first,
                            name = unitName.second,
                            dietaryTag = ""
                        )
                    },
                    instructions = instructions,
                    tags = tags,
                    imagePath = imagePath ?: existingRecipe?.imagePath,
                    difficulty = difficulty,
                    createdAt = existingRecipe?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                recipeRepository.saveRecipe(recipe)
                _saveState.value = SaveState.Success
            } catch (e: Exception) {
                e.printStackTrace()
                _saveState.value = SaveState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }
}
