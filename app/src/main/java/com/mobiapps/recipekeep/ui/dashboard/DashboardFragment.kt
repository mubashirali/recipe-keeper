package com.mobiapps.recipekeep.ui.dashboard

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
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mobiapps.recipekeep.R
import com.mobiapps.recipekeep.databinding.DialogConfirmDeleteBinding
import com.mobiapps.recipekeep.databinding.FragmentDashboardBinding
import com.mobiapps.recipekeep.domain.model.Recipe
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var adapter: RecipeAdapter
    private var nativeAd: NativeAd? = null

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
        binding.recyclerRecipes.layoutManager = GridLayoutManager(requireContext(), 2).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (adapter.getItemViewType(position) == 1) 2 else 1
                }
            }
        }
        binding.recyclerRecipes.adapter = adapter

        loadNativeAd()

        // Observe filtered recipes
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filteredRecipes.collect { recipes ->
                    updateListWithAds(recipes)
                    binding.emptyStateContainer.visibility = if (recipes.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerRecipes.visibility = if (recipes.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        // Observe all tags
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allTags.collect { tags ->
                    updateTagChips(tags)
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

    private fun loadNativeAd() {
        val adLoader = AdLoader.Builder(requireContext(), "ca-app-pub-4179968443458774/3875615101")
            .forNativeAd { ad : NativeAd ->
                nativeAd = ad
                if (!isDetached) {
                    updateListWithAds(viewModel.filteredRecipes.value)
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    // Handle failure
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun updateListWithAds(recipes: List<Recipe>) {
        val items = mutableListOf<DashboardItem>()
        recipes.forEachIndexed { index, recipe ->
            items.add(DashboardItem.RecipeItem(recipe))
            // Insert ad after every 5 recipes if ad is loaded
            nativeAd?.let { ad ->
                if ((index + 1) % 5 == 0) {
                    items.add(DashboardItem.AdItem(ad))
                }
            }
        }
        adapter.submitList(items)
    }

    private fun updateTagChips(tags: List<String>) {
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
        nativeAd?.destroy()
        _binding = null
    }
}
