package com.mobiapps.recipekeeper.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.mobiapps.recipekeeper.R
import com.mobiapps.recipekeeper.databinding.FragmentDashboardBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var adapter: RecipeAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup RecyclerView
        adapter = RecipeAdapter { recipe ->
            // Navigate to recipe viewer when item is clicked
            val action = DashboardFragmentDirections.actionDashboardToViewer(recipe.id)
            findNavController().navigate(action)
        }
        binding.recyclerRecipes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRecipes.adapter = adapter

        // Observe filtered recipes from ViewModel
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filteredRecipes.collect { recipes ->
                    adapter.submitList(recipes)
                    binding.emptyStateContainer.visibility = if (recipes.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerRecipes.visibility = if (recipes.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        // Observe all tags for chip display
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allTags.collect { tags ->
                    setupTagChips(tags)
                }
            }
        }

        // Setup search functionality
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.setSearchQuery(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText ?: "")
                return true
            }
        })

        // Setup FAB
        binding.fabAddRecipe.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_creator)
        }
    }

    private fun setupTagChips(tags: List<String>) {
        // Clear existing chips
        binding.tagChipGroup.removeAllViews()

        // Add "All" chip (selected by default)
        val allChip = Chip(requireContext()).apply {
            text = "All"
            isChecked = true
            setChipBackgroundColorResource(R.color.chip_background)
            setOnClickListener {
                // Clear selection from other chips
                binding.tagChipGroup.clearCheck()
                isChecked = true
                viewModel.setSelectedTag("")
            }
        }
        binding.tagChipGroup.addView(allChip)

        // Add chips for each tag
        tags.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = "#$tag"
                setChipBackgroundColorResource(R.color.chip_background)
                setOnClickListener {
                    // Clear selection from other chips
                    binding.tagChipGroup.clearCheck()
                    isChecked = true
                    viewModel.setSelectedTag(tag)
                }
            }
            binding.tagChipGroup.addView(chip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
