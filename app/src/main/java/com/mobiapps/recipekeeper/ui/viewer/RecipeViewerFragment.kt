package com.mobiapps.recipekeeper.ui.viewer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.chip.Chip
import com.mobiapps.recipekeeper.R
import com.mobiapps.recipekeeper.databinding.FragmentRecipeViewerBinding
import com.mobiapps.recipekeeper.domain.model.Recipe
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecipeViewerFragment : Fragment() {

    private var _binding: FragmentRecipeViewerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecipeViewerViewModel by viewModels()
    private val args: RecipeViewerFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipeViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.inflateMenu(R.menu.menu_viewer)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    val action = RecipeViewerFragmentDirections.actionRecipeViewerFragmentToRecipeCreatorFragment(args.recipeId)
                    findNavController().navigate(action)
                    true
                }
                else -> false
            }
        }

        // Load recipe when args are available
        viewModel.loadRecipe(args.recipeId)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recipe.collect { recipe ->
                    recipe?.let { displayRecipe(it) }
                }
            }
        }
    }

    private fun displayRecipe(recipe: Recipe) {
        binding.tvTitle.text = recipe.title
        binding.tvDescription.text = if (recipe.description.isNotEmpty()) recipe.description else "No description provided"
        binding.tvPrepTime.text = "${recipe.prepTimeMinutes} mins"
        binding.tvServings.text = if (recipe.servings == 1) "1 person" else "${recipe.servings} people"

        // Clear existing views and add ingredient items
        binding.containerIngredients.removeAllViews()
        recipe.ingredients.forEach { ingredient ->
            val textView = TextView(requireContext()).apply {
                text = "${ingredient.quantity} ${ingredient.unit} ${ingredient.name}"
                setPadding(0, 8, 0, 8)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            }
            binding.containerIngredients.addView(textView)
        }

        // Display instructions
        val instructionsText = recipe.instructions.joinToString(separator = "\n\n") {
            "${recipe.instructions.indexOf(it) + 1}. $it"
        }
        binding.tvInstructions.text = if (recipe.instructions.isNotEmpty()) instructionsText else "No instructions provided"

        // Display tags
        binding.chipGroupTags.removeAllViews()
        recipe.tags.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = tag
                isCheckable = false
                isClickable = false
            }
            binding.chipGroupTags.addView(chip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
