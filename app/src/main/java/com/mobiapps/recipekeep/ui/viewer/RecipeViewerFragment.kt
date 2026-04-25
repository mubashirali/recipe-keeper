package com.mobiapps.recipekeep.ui.viewer

import android.graphics.Paint
import android.os.Bundle
import java.util.Locale
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
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
import com.mobiapps.recipekeep.R
import com.mobiapps.recipekeep.databinding.FragmentRecipeViewerBinding
import com.mobiapps.recipekeep.domain.model.Recipe
import com.mobiapps.recipekeep.util.UnitConverter
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecipeViewerFragment : Fragment() {

    private var _binding: FragmentRecipeViewerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecipeViewerViewModel by viewModels()
    private val args: RecipeViewerFragmentArgs by navArgs()

    private var currentServings: Int = 1
    private var baseServings: Int = 1

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
                    recipe?.let { 
                        baseServings = it.servings
                        currentServings = it.servings
                        displayRecipe(it) 
                    }
                }
            }
        }

        binding.btnIncreaseServings.setOnClickListener {
            currentServings++
            updateServingsAndIngredients()
        }

        binding.btnDecreaseServings.setOnClickListener {
            if (currentServings > 1) {
                currentServings--
                updateServingsAndIngredients()
            }
        }
    }

    private fun updateServingsAndIngredients() {
        binding.tvServings.text = currentServings.toString()
        val recipe = viewModel.recipe.value ?: return
        
        binding.containerIngredients.removeAllViews()
        recipe.ingredients.forEach { ingredient ->
            val adjustedQuantity = UnitConverter.scaleQuantity(ingredient.quantity, baseServings, currentServings)
            addIngredientItem(adjustedQuantity, ingredient.unit, ingredient.name)
        }
        updateIngredientCount()
    }

    private fun addIngredientItem(quantity: String, unit: String, name: String) {
        val checkBox = CheckBox(requireContext()).apply {
            text = "$quantity $unit $name"
            setPadding(8, 12, 8, 12)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    alpha = 0.5f
                } else {
                    paintFlags = paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    alpha = 1.0f
                }
                updateIngredientCount()
            }
        }
        binding.containerIngredients.addView(checkBox)
    }

    private fun updateIngredientCount() {
        var checkedCount = 0
        val totalCount = binding.containerIngredients.childCount
        for (i in 0 until totalCount) {
            val child = binding.containerIngredients.getChildAt(i)
            if (child is CheckBox && child.isChecked) {
                checkedCount++
            }
        }
        binding.tvIngredientCount.text = "$checkedCount of $totalCount checked"
    }

    private fun displayRecipe(recipe: Recipe) {
        binding.tvTitle.text = recipe.title
        binding.tvDescription.text = if (recipe.description.isNotEmpty()) recipe.description else "No description provided"
        binding.tvPrepTime.text = "${recipe.prepTimeMinutes} mins"
        binding.tvServings.text = currentServings.toString()

        // Clear existing views and add ingredient items
        binding.containerIngredients.removeAllViews()
        recipe.ingredients.forEach { ingredient ->
            addIngredientItem(ingredient.quantity, ingredient.unit, ingredient.name)
        }
        updateIngredientCount()

        // Display instructions
        binding.containerInstructions.removeAllViews()
        if (recipe.instructions.isNotEmpty()) {
            binding.progressCooking.visibility = View.VISIBLE
            binding.progressCooking.max = recipe.instructions.size
            binding.progressCooking.progress = 0
            
            recipe.instructions.forEachIndexed { index, step ->
                val checkBox = CheckBox(requireContext()).apply {
                    text = "${index + 1}. $step"
                    setPadding(8, 12, 8, 12)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                            alpha = 0.5f
                        } else {
                            paintFlags = paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                            alpha = 1.0f
                        }
                        updateCookingProgress()
                    }
                }
                binding.containerInstructions.addView(checkBox)
            }
        } else {
            binding.progressCooking.visibility = View.GONE
            val textView = TextView(requireContext()).apply {
                text = "No instructions provided"
                setPadding(16, 16, 16, 16)
            }
            binding.containerInstructions.addView(textView)
        }

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

    private fun updateCookingProgress() {
        var completedSteps = 0
        for (i in 0 until binding.containerInstructions.childCount) {
            val child = binding.containerInstructions.getChildAt(i)
            if (child is CheckBox && child.isChecked) {
                completedSteps++
            }
        }
        binding.progressCooking.setProgressCompat(completedSteps, true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
