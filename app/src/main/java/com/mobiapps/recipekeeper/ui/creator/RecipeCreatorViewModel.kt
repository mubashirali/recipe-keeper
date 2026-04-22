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

    fun saveRecipe(
        title: String,
        description: String?,
        prepTimeMinutes: Int,
        servings: Int,
        ingredients: List<Pair<String, Pair<String, String>>>,
        instructions: List<String>,
        tags: List<String>
    ) {
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            try {
                val recipeId = UUID.randomUUID().toString()
                val recipe = Recipe(
                    id = recipeId,
                    userId = null,
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
                    createdAt = System.currentTimeMillis(),
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
