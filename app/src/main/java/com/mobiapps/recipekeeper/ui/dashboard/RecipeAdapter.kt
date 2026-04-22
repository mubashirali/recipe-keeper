package com.mobiapps.recipekeeper.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mobiapps.recipekeeper.databinding.ItemRecipeBinding
import com.mobiapps.recipekeeper.domain.model.Recipe

class RecipeAdapter(
    private val onRecipeClick: (Recipe) -> Unit
) : ListAdapter<Recipe, RecipeAdapter.RecipeViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<Recipe>() {
        override fun areItemsTheSame(oldItem: Recipe, newItem: Recipe) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Recipe, newItem: Recipe) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val binding = ItemRecipeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecipeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecipeViewHolder(
        private val binding: ItemRecipeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: Recipe) {
            binding.tvRecipeTitle.text = recipe.title
            binding.tvRecipeDescription.text = recipe.description.ifBlank { "No description" }
            binding.tvPrepTime.text = "${recipe.prepTimeMinutes} min"
            binding.root.setOnClickListener { onRecipeClick(recipe) }
        }
    }
}
