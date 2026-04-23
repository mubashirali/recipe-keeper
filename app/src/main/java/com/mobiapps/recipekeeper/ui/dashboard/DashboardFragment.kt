package com.mobiapps.recipekeeper.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mobiapps.recipekeeper.R
import com.mobiapps.recipekeeper.databinding.DialogConfirmDeleteBinding
import com.mobiapps.recipekeeper.databinding.FragmentDashboardBinding
import com.mobiapps.recipekeeper.domain.model.Recipe
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
        adapter = RecipeAdapter(
            onRecipeClick = { recipe ->
                val action = DashboardFragmentDirections.actionDashboardToViewer(recipe.id)
                findNavController().navigate(action)
            },
            onEditClick = { recipe ->
                val action = DashboardFragmentDirections.actionDashboardToCreator(recipe.id)
                findNavController().navigate(action)
            },
            onDeleteClick = { recipe ->
                showDeleteConfirmationDialog(recipe)
            }
        )
        binding.recyclerRecipes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRecipes.adapter = adapter

        // Observe filtered recipes
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filteredRecipes.collect { recipes ->
                    adapter.submitList(recipes)
                    binding.emptyStateContainer.visibility = if (recipes.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerRecipes.visibility = if (recipes.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        // Observe all tags
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allTags.collect { tags ->
                    setupTagChips(tags)
                }
            }
        }

        // Setup search
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
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
        val context = ContextThemeWrapper(requireContext(), R.style.Widget_RecipeKeeper_TagChip)
        binding.tagChipGroup.removeAllViews()

        val allChip = Chip(context).apply {
            text = "All Recipes"
            isCheckable = true
            isChecked = viewModel.selectedTag.value.isEmpty()
            id = View.generateViewId()
            setOnClickListener {
                viewModel.setSelectedTag("")
            }
        }
        binding.tagChipGroup.addView(allChip)

        tags.forEach { tag ->
            val chip = Chip(context).apply {
                text = tag
                isCheckable = true
                isChecked = viewModel.selectedTag.value == tag
                id = View.generateViewId()
                setOnClickListener {
                    viewModel.setSelectedTag(tag)
                }
            }
            binding.tagChipGroup.addView(chip)
        }
    }

    private fun showDeleteConfirmationDialog(recipe: Recipe) {
        val dialogBinding = DialogConfirmDeleteBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tvDialogMessage.text = getString(R.string.delete_recipe_confirmation_message, recipe.title)
        
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnDelete.setOnClickListener {
            viewModel.deleteRecipe(recipe)
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
