package com.mobiapps.recipekeeper.ui.creator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import com.mobiapps.recipekeeper.R
import com.mobiapps.recipekeeper.databinding.FragmentRecipeCreatorBinding
import com.mobiapps.recipekeeper.databinding.ItemIngredientRowBinding
import com.mobiapps.recipekeeper.domain.model.Recipe
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecipeCreatorFragment : Fragment() {

    private var _binding: FragmentRecipeCreatorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecipeCreatorViewModel by viewModels()
    private val args: RecipeCreatorFragmentArgs by navArgs()

    private val ingredientRows = mutableListOf<ItemIngredientRowBinding>()
    
    // Common culinary units for the dropdown
    private val units = arrayOf("g", "kg", "ml", "l", "tsp", "tbsp", "cup", "oz", "lb", "pcs", "to taste")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipeCreatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.btnAddIngredient.setOnClickListener {
            addIngredientRow()
        }

        // Check if we're in edit mode
        args.recipeId?.let { recipeId ->
            viewModel.loadRecipe(recipeId)
        } ?: run {
            // Add one initial row if empty to prompt input for NEW recipe
            if (ingredientRows.isEmpty()) {
                addIngredientRow()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recipeToEdit.collect { recipe ->
                    recipe?.let { populateFields(it) }
                }
            }
        }

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
                    saveRecipe()
                    true
                }
                else -> true
            }
        }
    }

    private fun populateFields(recipe: Recipe) {
        binding.toolbar.title = "Edit Recipe"
        binding.etTitle.setText(recipe.title)
        binding.etDescription.setText(recipe.description)
        binding.etPrepTime.setText(recipe.prepTimeMinutes.toString())
        binding.etServings.setText(recipe.servings.toString())
        binding.etInstructions.setText(recipe.instructions.joinToString("\n"))
        binding.etTags.setText(recipe.tags.joinToString(", "))

        // Clear existing rows and add from recipe
        binding.containerIngredients.removeAllViews()
        ingredientRows.clear()
        recipe.ingredients.forEach { ingredient ->
            addIngredientRow(ingredient.quantity, ingredient.unit, ingredient.name)
        }
        
        if (ingredientRows.isEmpty()) {
            addIngredientRow()
        }
    }

    private fun addIngredientRow(quantity: String = "", unit: String = "", name: String = "") {
        val rowBinding = ItemIngredientRowBinding.inflate(layoutInflater, binding.containerIngredients, true)
        ingredientRows.add(rowBinding)
        
        // Setup the unit dropdown (Exposed Dropdown Menu)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, units)
        rowBinding.etIngredientUnit.setAdapter(adapter)

        rowBinding.etIngredientQuantity.setText(quantity)
        rowBinding.etIngredientUnit.setText(unit, false)
        rowBinding.etIngredientName.setText(name)

        rowBinding.btnRemoveIngredient.setOnClickListener {
            binding.containerIngredients.removeView(rowBinding.root)
            ingredientRows.remove(rowBinding)
        }
    }

    private fun saveRecipe() {
        val title = binding.etTitle.text?.toString() ?: ""

        if (title.isBlank()) {
            binding.tilTitle.error = "Title is required"
            return
        }
        binding.tilTitle.error = null

        val description = binding.etDescription.text?.toString()
        val prepTime = binding.etPrepTime.text?.toString()?.toIntOrNull() ?: 0
        val servings = binding.etServings.text?.toString()?.toIntOrNull() ?: 1

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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
