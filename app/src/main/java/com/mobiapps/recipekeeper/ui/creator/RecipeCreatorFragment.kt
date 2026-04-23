package com.mobiapps.recipekeeper.ui.creator

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import com.mobiapps.recipekeeper.R
import com.mobiapps.recipekeeper.databinding.FragmentRecipeCreatorBinding
import com.mobiapps.recipekeeper.databinding.ItemIngredientRowBinding
import com.mobiapps.recipekeeper.domain.model.Recipe
import com.mobiapps.recipekeeper.util.ImageUtils
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class RecipeCreatorFragment : Fragment() {

    private var _binding: FragmentRecipeCreatorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecipeCreatorViewModel by viewModels()
    private val args: RecipeCreatorFragmentArgs by navArgs()

    private val ingredientRows = mutableListOf<ItemIngredientRowBinding>()
    
    private var currentPhotoPath: String? = null
    private var selectedImagePath: String? = null

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            currentPhotoPath?.let { path ->
                val compressedPath = ImageUtils.compressAndSaveImage(requireContext(), path)
                selectedImagePath = compressedPath
                binding.ivRecipeImage.load(selectedImagePath) {
                    crossfade(true)
                    binding.ivRecipeImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    binding.ivRecipeImage.imageTintList = null
                }
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val compressedPath = ImageUtils.compressAndSaveImage(requireContext(), it)
            selectedImagePath = compressedPath
            binding.ivRecipeImage.load(selectedImagePath) {
                crossfade(true)
                binding.ivRecipeImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                binding.ivRecipeImage.imageTintList = null
            }
        }
    }

    // Common culinary units for the dropdown
    private val units = arrayOf("g", "kg", "ml", "l", "tsp", "tbsp", "cup", "oz", "lb", "pcs", "to taste")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipeCreatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.btnTakePhoto.setOnClickListener {
            showImageSourceDialog()
        }

        binding.btnAddIngredient.setOnClickListener {
            addIngredientRow()
        }

        // Check if we're in edit mode
        args.recipeId?.let { recipeId ->
            viewModel.loadRecipe(recipeId)
        } ?: run {
            // Add one initial row if empty to prompt input for NEW recipe
            if (ingredientRows.isEmpty()) {
                addIngredientRow()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recipeToEdit.collect { recipe ->
                    recipe?.let { populateFields(it) }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveState.collect { state ->
                    when (state) {
                        is SaveState.Saving -> {
                            binding.toolbar.menu.findItem(R.id.action_save)?.isEnabled = false
                        }
                        is SaveState.Success -> {
                            viewModel.resetSaveState()
                            findNavController().navigateUp()
                        }
                        is SaveState.Error -> {
                            viewModel.resetSaveState()
                            binding.toolbar.menu.findItem(R.id.action_save)?.isEnabled = true
                            Snackbar.make(binding.root, "Failed to save: ${state.message}", Snackbar.LENGTH_LONG).show()
                        }
                        is SaveState.Idle -> {
                            binding.toolbar.menu.findItem(R.id.action_save)?.isEnabled = true
                        }
                    }
                }
            }
        }

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save -> {
                    saveRecipe()
                    true
                }
                else -> true
            }
        }
    }

    private fun populateFields(recipe: Recipe) {
        binding.toolbar.title = "Edit Recipe"
        binding.etTitle.setText(recipe.title)
        binding.etDescription.setText(recipe.description)
        binding.etPrepTime.setText(recipe.prepTimeMinutes.toString())
        binding.etServings.setText(recipe.servings.toString())
        binding.etInstructions.setText(recipe.instructions.joinToString("\n"))
        binding.etTags.setText(recipe.tags.joinToString(", "))

        recipe.imagePath?.let {
            selectedImagePath = it
            binding.ivRecipeImage.load(it) {
                crossfade(true)
                binding.ivRecipeImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                binding.ivRecipeImage.imageTintList = null
            }
        }

        when (recipe.difficulty) {
            "Easy" -> binding.cgDifficulty.check(R.id.chip_easy)
            "Hard" -> binding.cgDifficulty.check(R.id.chip_hard)
            else -> binding.cgDifficulty.check(R.id.chip_medium)
        }

        // Clear existing rows and add from recipe
        binding.containerIngredients.removeAllViews()
        ingredientRows.clear()
        recipe.ingredients.forEach { ingredient ->
            addIngredientRow(ingredient.quantity, ingredient.unit, ingredient.name)
        }
        
        if (ingredientRows.isEmpty()) {
            addIngredientRow()
        }
    }

    private fun addIngredientRow(quantity: String = "", unit: String = "", name: String = "") {
        val rowBinding = ItemIngredientRowBinding.inflate(layoutInflater, binding.containerIngredients, true)
        ingredientRows.add(rowBinding)
        
        // Setup the unit dropdown (Exposed Dropdown Menu)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, units)
        rowBinding.etIngredientUnit.setAdapter(adapter)

        rowBinding.etIngredientQuantity.setText(quantity)
        rowBinding.etIngredientUnit.setText(unit, false)
        rowBinding.etIngredientName.setText(name)

        rowBinding.btnRemoveIngredient.setOnClickListener {
            binding.containerIngredients.removeView(rowBinding.root)
            ingredientRows.remove(rowBinding)
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Recipe Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchCamera()
                    1 -> pickImageLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun launchCamera() {
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: Exception) {
            null
        }
        photoFile?.also {
            val photoURI: Uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                it
            )
            takePhotoLauncher.launch(photoURI)
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("RECIPE_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun saveRecipe() {
        val title = binding.etTitle.text?.toString() ?: ""

        if (title.isBlank()) {
            binding.tilTitle.error = "Title is required"
            binding.etTitle.requestFocus()
            return
        }
        binding.tilTitle.error = null

        val prepTimeText = binding.etPrepTime.text?.toString() ?: ""
        if (prepTimeText.isBlank()) {
            binding.tilPrepTime.error = "Required"
            binding.etPrepTime.requestFocus()
            return
        }
        binding.tilPrepTime.error = null

        val servingsText = binding.etServings.text?.toString() ?: ""
        if (servingsText.isBlank()) {
            binding.tilServings.error = "Required"
            binding.etServings.requestFocus()
            return
        }
        binding.tilServings.error = null

        val description = binding.etDescription.text?.toString()
        val prepTime = prepTimeText.toIntOrNull() ?: 0
        val servings = servingsText.toIntOrNull() ?: 1

        val ingredients = mutableListOf<Pair<String, Pair<String, String>>>()
        for (rowBinding in ingredientRows) {
            val quantity = rowBinding.etIngredientQuantity.text?.toString() ?: ""
            val unit = rowBinding.etIngredientUnit.text?.toString() ?: ""
            val name = rowBinding.etIngredientName.text?.toString() ?: ""
            if (name.isNotEmpty()) {
                ingredients.add(Pair(quantity, Pair(unit, name)))
            }
        }

        val instructionsText = binding.etInstructions.text?.toString() ?: ""
        val instructions = instructionsText.split('\n').filter { it.isNotBlank() }

        val tagsText = binding.etTags.text?.toString() ?: ""
        val tags = tagsText.split(',').map { it.trim() }.filter { it.isNotBlank() }

        val difficulty = when (binding.cgDifficulty.checkedChipId) {
            R.id.chip_easy -> "Easy"
            R.id.chip_hard -> "Hard"
            else -> "Medium"
        }

        viewModel.saveRecipe(
            title = title,
            description = if (description.isNullOrEmpty()) null else description,
            prepTimeMinutes = prepTime,
            servings = servings,
            ingredients = ingredients,
            instructions = instructions,
            tags = tags,
            imagePath = selectedImagePath,
            difficulty = difficulty
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
