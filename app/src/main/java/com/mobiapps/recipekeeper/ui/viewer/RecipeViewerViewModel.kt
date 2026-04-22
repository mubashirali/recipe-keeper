package com.mobiapps.recipekeeper.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobiapps.recipekeeper.data.repository.RecipeRepository
import com.mobiapps.recipekeeper.domain.model.Recipe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecipeViewerViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    private val _recipe = MutableStateFlow<Recipe?>(null)
    val recipe: StateFlow<Recipe?> = _recipe.asStateFlow()

    fun loadRecipe(recipeId: String) {
        viewModelScope.launch {
            recipeRepository.getRecipeById(recipeId).collect { recipe ->
                _recipe.value = recipe
            }
        }
    }
}
