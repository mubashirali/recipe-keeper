package com.mobiapps.recipekeeper.ui.creator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.mobiapps.recipekeeper.databinding.FragmentRecipeCreatorBinding
import com.mobiapps.recipekeeper.databinding.ItemIngredientRowBinding

class RecipeCreatorFragment : Fragment() {

    private var _binding: FragmentRecipeCreatorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecipeCreatorViewModel by viewModels()

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

        // Add ingredient row (scaffold — rows are view-only in Phase 0)
        binding.btnAddIngredient.setOnClickListener {
            val rowBinding = ItemIngredientRowBinding.inflate(layoutInflater, binding.containerIngredients, true)
            rowBinding.btnRemoveIngredient.setOnClickListener {
                rowBinding.btnRemoveIngredient.setOnClickListener(null)
                binding.containerIngredients.removeView(rowBinding.root)
            }
        }

        // Save action — wired up in Phase 1
        binding.toolbar.setOnMenuItemClickListener { true }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
