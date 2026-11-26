package com.mobiapps.recipekeep.ui.viewer

import android.graphics.Paint
import android.os.Bundle
import java.util.Locale
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.mobiapps.recipekeep.R
import com.mobiapps.recipekeep.databinding.FragmentRecipeViewerBinding
import com.mobiapps.recipekeep.domain.model.Recipe
import com.mobiapps.recipekeep.ui.common.AudioClipList
import com.mobiapps.recipekeep.util.AudioPlayer
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

    private val player = AudioPlayer()
    private var clipList: AudioClipList? = null
    private var currentClips: List<String> = emptyList()

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
        clipList = AudioClipList(
            container = binding.containerClips,
            player = player,
            onMissing = { Snackbar.make(binding.root, R.string.recording_not_found, Snackbar.LENGTH_SHORT).show() }
        )
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                player.playing.collect { clipList?.render(currentClips, it) }
            }
        }
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

        MobileAds.initialize(requireContext()) {
            if (_binding != null) binding.adView.loadAd(AdRequest.Builder().build())
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.adView?.resume()
    }

    override fun onPause() {
        _binding?.adView?.pause()
        super.onPause()
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
        binding.tvIngredientCount.text = getString(R.string.checked_count, checkedCount, totalCount)
    }

    private fun displayRecipe(recipe: Recipe) {
        binding.tvTitle.text = recipe.title
        binding.tvDescription.text = if (recipe.description.isNotEmpty()) recipe.description else getString(R.string.no_description_provided)
        val hasPrep = recipe.prepTimeMinutes > 0
        val hasServings = recipe.servings > 0
        binding.rowStats.isVisible = hasPrep || hasServings
        binding.tvPrepTime.text = if (hasPrep) getString(R.string.minutes_short, recipe.prepTimeMinutes) else "—"
        binding.tvServings.text = if (hasServings) currentServings.toString() else "—"
        binding.btnIncreaseServings.isVisible = hasServings
        binding.btnDecreaseServings.isVisible = hasServings

        currentClips = recipe.audioPaths
        binding.sectionVoice.isVisible = currentClips.isNotEmpty()
        clipList?.render(currentClips, player.playing.value)

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
                text = getString(R.string.no_instructions)
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

    override fun onStop() {
        super.onStop()
        player.stop()
    }

    override fun onDestroyView() {
        player.stop()
        clipList = null
        _binding?.adView?.destroy()
        super.onDestroyView()
        _binding = null
    }
}
