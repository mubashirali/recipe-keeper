package com.mobiapps.recipekeeper.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobiapps.recipekeeper.data.repository.RecipeRepository
import com.mobiapps.recipekeeper.domain.model.Recipe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    private val _recipes = MutableStateFlow<List<Recipe>>(emptyList())
    val recipes: StateFlow<List<Recipe>> = _recipes.asStateFlow()
    
    private val _searchQuery = MutableStateFlow<String>("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _selectedTag = MutableStateFlow<String>("")
    val selectedTag: StateFlow<String> = _selectedTag.asStateFlow()
    
    // All unique tags across all recipes
    val allTags: StateFlow<List<String>> = _recipes
        .map { recipes ->
            recipes.flatMap { it.tags }
                .distinct()
                .sorted()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Filtered recipes based on search query and selected tag
    val filteredRecipes: StateFlow<List<Recipe>> = combine(
        _recipes,
        _searchQuery,
        _selectedTag
    ) { recipes, query, tag ->
        recipes.filter { recipe ->
            val matchesSearch = query.isBlank() || recipe.title.lowercase().contains(query.lowercase())
            val matchesTag = tag.isBlank() || recipe.tags.contains(tag)
            matchesSearch && matchesTag
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        loadRecipes()
    }

    private fun loadRecipes() {
        viewModelScope.launch {
            recipeRepository.getAllRecipes().collect { recipes ->
                _recipes.value = recipes
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun setSelectedTag(tag: String) {
        _selectedTag.value = tag
    }

    fun deleteRecipe(recipe: Recipe) {
        viewModelScope.launch {
            recipeRepository.deleteRecipe(recipe)
        }
    }
}
