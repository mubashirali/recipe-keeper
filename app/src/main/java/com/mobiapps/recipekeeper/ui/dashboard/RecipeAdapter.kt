package com.mobiapps.recipekeeper.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.chip.Chip
import com.mobiapps.recipekeeper.R
import com.mobiapps.recipekeeper.databinding.ItemRecipeBinding
import com.mobiapps.recipekeeper.domain.model.Recipe

class RecipeAdapter(
    private val onRecipeClick: (Recipe) -> Unit,
    private val onEditClick: (Recipe) -> Unit,
    private val onDeleteClick: (Recipe) -> Unit
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
            binding.tvServings.text = recipe.servings.toString()
            binding.tvDifficulty.text = recipe.difficulty

            // Load image
            binding.ivRecipeImage.load(recipe.imagePath) {
                crossfade(true)
                placeholder(R.drawable.ic_chef_hat)
                error(R.drawable.ic_chef_hat)
                if (recipe.imagePath == null) {
                    binding.ivRecipeImage.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    binding.ivRecipeImage.imageTintList = android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.parseColor("#4DFFFFFF")
                    )
                } else {
                    binding.ivRecipeImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    binding.ivRecipeImage.imageTintList = null
                }
            }

            // Setup tags
            binding.cgRecipeTags.removeAllViews()
            recipe.tags.take(3).forEach { tag ->
                val chip = Chip(binding.root.context).apply {
                    text = "#$tag"
                    isClickable = false
                    isCheckable = false
                    setChipBackgroundColorResource(R.color.md_theme_light_surfaceVariant)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelSmall)
                }
                binding.cgRecipeTags.addView(chip)
            }

            binding.root.setOnClickListener { onRecipeClick(recipe) }
        }
    }
}
