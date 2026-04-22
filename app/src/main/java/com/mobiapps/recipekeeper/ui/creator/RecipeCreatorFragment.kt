package com.mobiapps.recipekeeper.ui.creator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import com.mobiapps.recipekeeper.R
import com.mobiapps.recipekeeper.databinding.FragmentRecipeCreatorBinding
import com.mobiapps.recipekeeper.databinding.ItemIngredientRowBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecipeCreatorFragment : Fragment() {

    private var _binding: FragmentRecipeCreatorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecipeCreatorViewModel by viewModels()

    // Bug 1 fix: track row bindings so we can read fields directly without casting view tree
    private val ingredientRows = mutableListOf<ItemIngredientRowBinding>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipeCreatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Back navigation
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        // Bug 1 fix: append binding to ingredientRows when adding a row
        binding.btnAddIngredient.setOnClickListener {
            val rowBinding = ItemIngredientRowBinding.inflate(layoutInflater, binding.containerIngredients, true)
            ingredientRows.add(rowBinding)
            rowBinding.btnRemoveIngredient.setOnClickListener {
                rowBinding.btnRemoveIngredient.setOnClickListener(null)
                binding.containerIngredients.removeView(rowBinding.root)
                // Bug 1 fix: keep list in sync when a row is removed
                ingredientRows.remove(rowBinding)
            }
        }

        // Bug 2 fix: observe saveState and react to each transition
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveState.collect { state ->
                    when (state) {
                        is SaveState.Saving -> {
                            binding.toolbar.menu.findItem(R.id.action_save)?.isEnabled = false
                        }
                        is SaveState.Success -> {
                            viewModel.resetSaveState()
                            findNavController().navigateUp()
                        }
                        is SaveState.Error -> {
                            viewModel.resetSaveState()
                            binding.toolbar.menu.findItem(R.id.action_save)?.isEnabled = true
                            Snackbar.make(binding.root, "Failed to save: ${state.message}", Snackbar.LENGTH_LONG).show()
                        }
                        is SaveState.Idle -> {
                            binding.toolbar.menu.findItem(R.id.action_save)?.isEnabled = true
                        }
                    }
                }
            }
        }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save -> {
                    val title = binding.etTitle.text?.toString() ?: ""

                    // Bug 3 fix: validate title before proceeding
                    if (title.isBlank()) {
                        binding.tilTitle.error = "Title is required"
                        return@setOnMenuItemClickListener true
                    }
                    binding.tilTitle.error = null

                    val description = binding.etDescription.text?.toString()
                    val prepTime = binding.etPrepTime.text?.toString()?.toIntOrNull() ?: 0
                    val servings = binding.etServings.text?.toString()?.toIntOrNull() ?: 1

                    // Bug 1 fix: iterate stored bindings instead of casting child views
                    val ingredients = mutableListOf<Pair<String, Pair<String, String>>>()
                    for (rowBinding in ingredientRows) {
                        val quantity = rowBinding.etIngredientQuantity.text?.toString() ?: ""
                        val unit = rowBinding.etIngredientUnit.text?.toString() ?: ""
                        val name = rowBinding.etIngredientName.text?.toString() ?: ""
                        if (name.isNotEmpty()) {
                            ingredients.add(Pair(quantity, Pair(unit, name)))
                        }
                    }

                    val instructionsText = binding.etInstructions.text?.toString() ?: ""
                    val instructions = instructionsText.split('\n').filter { it.isNotBlank() }

                    val tagsText = binding.etTags.text?.toString() ?: ""
                    val tags = tagsText.split(',').map { it.trim() }.filter { it.isNotBlank() }

                    viewModel.saveRecipe(
                        title = title,
                        description = if (description.isNullOrEmpty()) null else description,
                        prepTimeMinutes = prepTime,
                        servings = servings,
                        ingredients = ingredients,
                        instructions = instructions,
                        tags = tags
                    )
                    true
                }
                else -> true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
