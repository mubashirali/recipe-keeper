package com.mobiapps.recipekeeper.ui.viewer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.mobiapps.recipekeeper.databinding.FragmentRecipeViewerBinding

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
        // args.recipeId available here — used in Phase 1 to load recipe
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
