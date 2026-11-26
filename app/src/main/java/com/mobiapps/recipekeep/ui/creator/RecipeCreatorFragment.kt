package com.mobiapps.recipekeep.ui.creator

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
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
import com.mobiapps.recipekeep.R
import com.mobiapps.recipekeep.databinding.FragmentRecipeCreatorBinding
import com.mobiapps.recipekeep.databinding.ItemIngredientRowBinding
import com.mobiapps.recipekeep.domain.model.Recipe
import com.mobiapps.recipekeep.ui.common.AudioClipList
import com.mobiapps.recipekeep.util.AudioPlayer
import com.mobiapps.recipekeep.util.ImageUtils
import com.mobiapps.recipekeep.util.RecState
import com.mobiapps.recipekeep.util.RecipeDraftRules
import kotlinx.coroutines.flow.combine
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
    private var baselineSnapshot: String? = null // form state at open; null until known (edit mode loads async)

    private val player = AudioPlayer()

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.startRecording()
            else Snackbar.make(binding.root, R.string.mic_permission_needed, Snackbar.LENGTH_LONG).show()
        }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            currentPhotoPath?.let { path ->
                val compressedPath = ImageUtils.compressAndSaveImage(requireContext(), path)
                selectedImagePath = compressedPath
                binding.ivRecipeImage.load(selectedImagePath) {
                    crossfade(true)
                    binding.ivRecipeImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    binding.ivRecipeImage.imageTintList = null
                binding.ivRecipeImage.setPadding(0, 0, 0, 0) // placeholder icon padding would letterbox the photo
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
                binding.ivRecipeImage.setPadding(0, 0, 0, 0) // placeholder icon padding would letterbox the photo
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

        binding.toolbar.setNavigationOnClickListener { onBackRequested() }
        // Covers the system back button / gesture too.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBackRequested()
        })

        binding.btnTakePhoto.setOnClickListener {
            showImageSourceDialog()
        }

        binding.btnAddIngredient.setOnClickListener {
            addIngredientRow()
        }

        val clipList = AudioClipList(
            container = binding.containerClips,
            player = player,
            onDelete = { player.stopIf(it); viewModel.removeClip(it) },
            canPlay = { viewModel.recState.value != RecState.Recording },
            onMissing = { Snackbar.make(binding.root, R.string.recording_not_found, Snackbar.LENGTH_SHORT).show() }
        )

        binding.btnRecord.setOnClickListener {
            val granted = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) viewModel.startRecording() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        binding.btnPauseResume.setOnClickListener {
            if (viewModel.recState.value == RecState.Recording) viewModel.pauseRecording() else viewModel.resumeRecording()
        }
        binding.btnStop.setOnClickListener { viewModel.stopRecording() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.recState.collect { state ->
                        val active = state != RecState.Idle
                        binding.btnRecord.isVisible = !active
                        binding.btnPauseResume.isVisible = active
                        binding.btnStop.isVisible = active
                        binding.chronometer.isVisible = active
                        binding.btnPauseResume.text = getString(if (state == RecState.Paused) R.string.resume else R.string.pause)
                        binding.chronometer.base = SystemClock.elapsedRealtime() - viewModel.elapsedMs()
                        if (state == RecState.Recording) binding.chronometer.start() else binding.chronometer.stop()
                        if (state == RecState.Recording) player.stop()
                    }
                }
                launch {
                    combine(viewModel.clips, player.playing) { clips, playing -> clips to playing }
                        .collect { (clips, playing) -> clipList.render(clips, playing) }
                }
                launch {
                    viewModel.messages.collect { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() }
                }
            }
        }

        // Check if we're in edit mode
        args.recipeId?.let { recipeId ->
            viewModel.loadRecipe(recipeId)
        } ?: run {
            // Add one initial row if empty to prompt input for NEW recipe
            if (ingredientRows.isEmpty()) {
                addIngredientRow()
            }
            // Empty form = nothing to lose. Taken before Android restores typed text after rotation.
            baselineSnapshot = formSnapshot()
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
                        is SaveState.Saving -> saveButton?.isEnabled = false
                        is SaveState.Success -> {
                            viewModel.resetSaveState()
                            findNavController().navigateUp()
                        }
                        is SaveState.Error -> {
                            viewModel.resetSaveState()
                            saveButton?.isEnabled = true
                            Snackbar.make(binding.root, getString(R.string.save_failed, state.message), Snackbar.LENGTH_LONG).show()
                        }
                        is SaveState.Idle -> saveButton?.isEnabled = true
                    }
                }
            }
        }

        // The Save item uses a MaterialButton action layout, so it needs its own click listener.
        saveButton?.setOnClickListener { saveRecipe() }
    }

    private val saveButton: View?
        get() = _binding?.toolbar?.menu?.findItem(R.id.action_save)?.actionView?.findViewById(R.id.btn_save)

    private fun populateFields(recipe: Recipe) {
        binding.toolbar.title = getString(R.string.edit_recipe)
        binding.etTitle.setText(recipe.title)
        binding.etDescription.setText(recipe.description)
        binding.etPrepTime.setText(recipe.prepTimeMinutes.takeIf { it > 0 }?.toString().orEmpty())
        binding.etServings.setText(recipe.servings.takeIf { it > 0 }?.toString().orEmpty())
        binding.etInstructions.setText(recipe.instructions.joinToString("\n"))
        binding.etTags.setText(recipe.tags.joinToString(", "))

        recipe.imagePath?.let {
            selectedImagePath = it
            binding.ivRecipeImage.load(it) {
                crossfade(true)
                binding.ivRecipeImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                binding.ivRecipeImage.imageTintList = null
                binding.ivRecipeImage.setPadding(0, 0, 0, 0) // placeholder icon padding would letterbox the photo
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
        baselineSnapshot = formSnapshot() // what "unchanged" means in edit mode
    }

    /** Everything the user can change on the form, as one comparable string. */
    private fun formSnapshot(): String = listOf(
        binding.etTitle.text, binding.etDescription.text, binding.etPrepTime.text,
        binding.etServings.text, binding.etInstructions.text, binding.etTags.text,
        binding.cgDifficulty.checkedChipId, selectedImagePath,
        ingredientRows.joinToString("|") {
            "${it.etIngredientQuantity.text}/${it.etIngredientUnit.text}/${it.etIngredientName.text}"
        }
    ).joinToString("\u0001")

    // Voice clips live in the ViewModel, so they are tracked there and survive rotation.
    private fun hasUnsavedChanges(): Boolean =
        viewModel.recState.value != RecState.Idle ||
            viewModel.hasClipChanges() ||
            (baselineSnapshot?.let { it != formSnapshot() } ?: false)

    private fun onBackRequested() {
        if (!hasUnsavedChanges()) {
            findNavController().navigateUp()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.discard_title)
            .setMessage(R.string.discard_message)
            .setNegativeButton(R.string.keep_editing, null)
            .setPositiveButton(R.string.discard) { _, _ -> findNavController().navigateUp() }
            .show()
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
        val options = arrayOf(getString(R.string.take_photo), getString(R.string.choose_gallery))
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_recipe_photo)
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
        if (viewModel.recState.value != RecState.Idle) viewModel.stopRecording() // finalise in-progress take (synchronous)
        val title = binding.etTitle.text?.toString().orEmpty()
        val description = binding.etDescription.text?.toString().orEmpty()
        val prepTime = binding.etPrepTime.text?.toString()?.toIntOrNull() ?: 0   // 0 = not set
        val servings = binding.etServings.text?.toString()?.toIntOrNull() ?: 0   // 0 = not set
        val instructionsText = binding.etInstructions.text?.toString().orEmpty()
        val tagsText = binding.etTags.text?.toString().orEmpty()

        val ingredients = mutableListOf<Pair<String, Pair<String, String>>>()
        for (rowBinding in ingredientRows) {
            val quantity = rowBinding.etIngredientQuantity.text?.toString() ?: ""
            val unit = rowBinding.etIngredientUnit.text?.toString() ?: ""
            val name = rowBinding.etIngredientName.text?.toString() ?: ""
            if (name.isNotEmpty()) {
                ingredients.add(Pair(quantity, Pair(unit, name)))
            }
        }

        val hasAudio = viewModel.clips.value.isNotEmpty()
        val hasImage = selectedImagePath != null || viewModel.recipeToEdit.value?.imagePath != null
        val hasContent = RecipeDraftRules.hasContent(
            textFields = listOf(title, description, instructionsText, tagsText) + ingredients.map { it.second.second },
            hasImage = hasImage,
            hasAudio = hasAudio
        )
        if (!hasContent) {
            Snackbar.make(binding.root, getString(R.string.add_something_first), Snackbar.LENGTH_LONG).show()
            return
        }

        val instructions = instructionsText.split('\n').filter { it.isNotBlank() }
        val tags = tagsText.split(',').map { it.trim() }.filter { it.isNotBlank() }
        val difficulty = when (binding.cgDifficulty.checkedChipId) {
            R.id.chip_easy -> "Easy"
            R.id.chip_hard -> "Hard"
            else -> "Medium"
        }

        viewModel.saveRecipe(
            title = RecipeDraftRules.resolveTitle(
                title, hasAudio, System.currentTimeMillis(),
                voiceTitle = getString(R.string.voice_recipe_title),
                untitledTitle = getString(R.string.untitled_recipe_title)
            ),
            description = description.ifBlank { null },
            prepTimeMinutes = prepTime,
            servings = servings,
            ingredients = ingredients,
            instructions = instructions,
            tags = tags,
            imagePath = selectedImagePath,
            difficulty = difficulty
        )
    }

    override fun onStop() {
        super.onStop()
        player.stop()
        if (!requireActivity().isChangingConfigurations && viewModel.recState.value != RecState.Idle) {
            viewModel.stopRecording() // keep what was recorded so far
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player.stop()
        _binding = null
    }
}
