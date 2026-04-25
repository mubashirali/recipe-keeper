package com.mobiapps.recipekeep.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.material.chip.Chip
import com.mobiapps.recipekeep.R
import com.mobiapps.recipekeep.databinding.ItemAdBinding
import com.mobiapps.recipekeep.databinding.ItemRecipeBinding
import com.mobiapps.recipekeep.domain.model.Recipe

sealed class DashboardItem {
    data class RecipeItem(val recipe: Recipe) : DashboardItem()
    data class AdItem(val nativeAd: NativeAd) : DashboardItem()
}

class RecipeAdapter(
    private val onRecipeClick: (Recipe) -> Unit,
    private val onEditClick: (Recipe) -> Unit,
    private val onDeleteClick: (Recipe) -> Unit
) : ListAdapter<DashboardItem, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val TYPE_RECIPE = 0
        private const val TYPE_AD = 1

        private object DiffCallback : DiffUtil.ItemCallback<DashboardItem>() {
            override fun areItemsTheSame(oldItem: DashboardItem, newItem: DashboardItem): Boolean {
                return when {
                    oldItem is DashboardItem.RecipeItem && newItem is DashboardItem.RecipeItem ->
                        oldItem.recipe.id == newItem.recipe.id
                    oldItem is DashboardItem.AdItem && newItem is DashboardItem.AdItem ->
                        oldItem.nativeAd == newItem.nativeAd
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: DashboardItem, newItem: DashboardItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is DashboardItem.RecipeItem -> TYPE_RECIPE
            is DashboardItem.AdItem -> TYPE_AD
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_RECIPE -> {
                val binding = ItemRecipeBinding.inflate(inflater, parent, false)
                RecipeViewHolder(binding)
            }
            TYPE_AD -> {
                val binding = ItemAdBinding.inflate(inflater, parent, false)
                AdViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is RecipeViewHolder -> holder.bind((item as DashboardItem.RecipeItem).recipe)
            is AdViewHolder -> holder.bind((item as DashboardItem.AdItem).nativeAd)
        }
    }

    inner class AdViewHolder(private val binding: ItemAdBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(nativeAd: NativeAd) {
            binding.root.setNativeAd(nativeAd)
            
            binding.adHeadline.text = nativeAd.headline
            binding.adBody.text = nativeAd.body
            binding.adCallToAction.text = nativeAd.callToAction
            
            nativeAd.icon?.let {
                binding.adAppIcon.setImageDrawable(it.drawable)
                binding.adAppIcon.visibility = View.VISIBLE
            } ?: run {
                binding.adAppIcon.visibility = View.GONE
            }

            nativeAd.starRating?.let {
                binding.adStars.rating = it.toFloat()
                binding.adStars.visibility = View.VISIBLE
            } ?: run {
                binding.adStars.visibility = View.GONE
            }

            nativeAd.advertiser?.let {
                binding.adAdvertiser.text = it
                binding.adAdvertiser.visibility = View.VISIBLE
            } ?: run {
                binding.adAdvertiser.visibility = View.GONE
            }

            binding.root.headlineView = binding.adHeadline
            binding.root.bodyView = binding.adBody
            binding.root.callToActionView = binding.adCallToAction
            binding.root.iconView = binding.adAppIcon
            binding.root.starRatingView = binding.adStars
            binding.root.advertiserView = binding.adAdvertiser
            binding.root.mediaView = binding.adMedia
        }
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
            binding.btnDelete.setOnClickListener { onDeleteClick(recipe) }
        }
    }
}
