package com.mobiapps.recipekeep.ui.creator

import android.content.Context
import androidx.annotation.StringRes
import com.mobiapps.recipekeep.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobiapps.recipekeep.data.repository.RecipeRepository
import com.mobiapps.recipekeep.domain.model.Ingredient
import com.mobiapps.recipekeep.domain.model.Recipe
import com.mobiapps.recipekeep.util.AudioRecorder
import com.mobiapps.recipekeep.util.RecState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
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
    private val recipeRepository: RecipeRepository,
    @ApplicationContext context: Context
) : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _recipeToEdit = MutableStateFlow<Recipe?>(null)
    val recipeToEdit: StateFlow<Recipe?> = _recipeToEdit.asStateFlow()

    // --- Voice notes ---
    private val appContext = context.applicationContext
    private fun str(@StringRes id: Int) = appContext.getString(id)
    private val recorder = AudioRecorder(context)
    val recState: StateFlow<RecState> = recorder.state
    fun elapsedMs(): Long = recorder.elapsedMs()

    private val _clips = MutableStateFlow<List<String>>(emptyList())
    val clips: StateFlow<List<String>> = _clips.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val newClips = mutableSetOf<String>()          // recorded this session, not yet saved
    private val removedSavedClips = mutableSetOf<String>() // saved clips the user deleted; files go after save
    private var saved = false
    private var saving = false
    private var clipsSeeded = false

    init {
        // Max-duration cap or a MediaRecorder error stopped the take by itself.
        recorder.onSystemStop = { result ->
            result
                .onSuccess { addClip(it); _messages.tryEmit(str(R.string.rec_stopped_saved)) }
                .onFailure { _messages.tryEmit(str(R.string.rec_stopped_failed)) }
        }
    }

    fun loadRecipe(recipeId: String) {
        viewModelScope.launch {
            val recipe = recipeRepository.getRecipeById(recipeId).firstOrNull()
            // Only seed clips on first load, so rotation doesn't drop clips recorded this session.
            if (!clipsSeeded) {
                clipsSeeded = true
                val savedClips = recipe?.audioPaths.orEmpty()
                _clips.update { savedClips + it.filter { p -> p in newClips } }
            }
            _recipeToEdit.value = recipe
        }
    }

    fun startRecording() {
        recorder.start().onFailure { _messages.tryEmit(str(R.string.rec_start_failed)) }
    }

    fun pauseRecording() {
        recorder.pause().onFailure { recorder.cancel(); _messages.tryEmit(str(R.string.rec_failed)) }
    }

    fun resumeRecording() {
        recorder.resume().onFailure { recorder.cancel(); _messages.tryEmit(str(R.string.rec_failed)) }
    }

    fun stopRecording() {
        if (recorder.state.value == RecState.Idle) return
        recorder.stop()
            .onSuccess { addClip(it) }
            .onFailure { _messages.tryEmit(str(R.string.rec_too_short)) }
    }

    /** True if clips were added or removed since the screen opened (drives the discard prompt). */
    fun hasClipChanges(): Boolean = newClips.isNotEmpty() || removedSavedClips.isNotEmpty()

    fun removeClip(path: String) {
        if (path !in _clips.value) return
        _clips.update { it - path }
        if (newClips.remove(path)) File(path).delete() else removedSavedClips += path
    }

    private fun addClip(file: File) {
        newClips += file.absolutePath
        _clips.update { it + file.absolutePath }
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
        if (saving) return
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            saved = false
            saving = true
            val clipsSnap = _clips.value
            val toDelete = removedSavedClips.toSet()
            try {
                val existingRecipe = _recipeToEdit.value
                val recipeId = existingRecipe?.id ?: UUID.randomUUID().toString()

                val recipe = Recipe(
                    id = recipeId,
                    userId = existingRecipe?.userId,
                    title = title,
                    description = description ?: "",
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
                    updatedAt = System.currentTimeMillis(),
                    audioPaths = clipsSnap
                )
                recipeRepository.saveRecipe(recipe)
                toDelete.forEach { File(it).delete() }
                removedSavedClips -= toDelete
                newClips.removeAll(clipsSnap.toSet())
                saved = true
                saving = false
                _saveState.value = SaveState.Success
            } catch (e: Exception) {
                saving = false
                e.printStackTrace()
                _saveState.value = SaveState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

    override fun onCleared() {
        recorder.cancel() // in-progress recording is discarded
        if (!saved && !saving) newClips.forEach { File(it).delete() }
    }
}
