# Voice Notes for Recipes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user save a recipe from any mix of typed fields, a photo, and audio recordings (record / pause / resume / stop, multiple clips, playback). Nothing is mandatory except "at least one".

**Architecture:** Audio is stored as `.m4a` files; `RecipeEntity` gets `audioPaths: List<String>` (JSON via existing `Converters`). `AudioRecorder` (wraps `MediaRecorder`) lives in the creator `ViewModel` so it survives rotation. `AudioPlayer` (wraps `MediaPlayer`) and a shared `AudioClipList` row renderer are used by the creator and viewer. Validation moves to a pure `RecipeDraftRules`.

**Tech Stack:** Kotlin, Android Views + ViewBinding, Navigation, Hilt, Room 2.7 (DB v2 → v3), coroutines/StateFlow, JUnit4. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-11-23-voice-recording-design.md`

**Conventions:** package root `com.mobiapps.recipekeep` = `app/src/main/java/com/mobiapps/recipekeep` (written `$SRC` below). Run Gradle from the repo root. Append the repo's `Co-Authored-By` trailer to commits if one is configured.

---

## File map

| File | Action | Responsibility |
|---|---|---|
| `$SRC/util/RecipeDraftRules.kt` | create | pure save-validation + title fallback |
| `app/src/test/.../util/RecipeDraftRulesTest.kt` | create | unit tests for the above |
| `$SRC/data/local/entity/RecipeEntity.kt` | modify | `audioPaths` column |
| `$SRC/domain/model/Recipe.kt` | modify | `audioPaths` field |
| `$SRC/data/repository/RecipeRepositoryImpl.kt` | modify | map `audioPaths`; delete files on recipe delete |
| `$SRC/data/local/RecipeDatabase.kt` | modify | v3 + `MIGRATION_2_3` |
| `$SRC/di/DatabaseModule.kt` | modify | register migration |
| `app/src/androidTest/.../dao/RecipeDaoTest.kt` | modify | round-trip test |
| `$SRC/util/AudioRecorder.kt` | create | `MediaRecorder` wrapper + `RecState` |
| `$SRC/util/AudioPlayer.kt` | create | `MediaPlayer` wrapper |
| `app/src/main/res/layout/item_audio_clip.xml` | create | one clip row |
| `$SRC/ui/common/AudioClipList.kt` | create | renders rows into a container |
| `app/src/main/AndroidManifest.xml` | modify | `RECORD_AUDIO`, mic `uses-feature` |
| `$SRC/ui/creator/RecipeCreatorViewModel.kt` | rewrite | recorder, clips, cleanup |
| `app/src/main/res/layout/fragment_recipe_creator.xml` | modify | "Voice notes" card |
| `$SRC/ui/creator/RecipeCreatorFragment.kt` | modify | mic UI, relaxed validation |
| `app/src/main/res/layout/fragment_recipe_viewer.xml` | modify | voice section, stats row id |
| `$SRC/ui/viewer/RecipeViewerFragment.kt` | modify | render clips, hide empty stats |
| `app/src/main/res/layout/item_recipe.xml` + `$SRC/ui/dashboard/RecipeAdapter.kt` | modify | hide empty prep/servings |

---

### Task 0: Branch and baseline install (do this BEFORE any schema change)

This puts a DB v2 on the device so Task 9 can prove the upgrade keeps data.

- [ ] **Step 1: Branch**

```bash
git checkout -b feature/voice-notes
```

- [ ] **Step 2: Install the current (v2) build on an emulator/device**

```bash
./gradlew :app:installDebug
```
Expected: `BUILD SUCCESSFUL`, `Installed on 1 device`.

- [ ] **Step 3: In the app, create 2 recipes manually** (one with an ingredient and a photo). Leave the app installed. Do not uninstall it until Task 9.

---

### Task 1: `RecipeDraftRules` (pure logic, TDD)

**Files:**
- Create: `app/src/test/java/com/mobiapps/recipekeep/util/RecipeDraftRulesTest.kt`
- Create: `app/src/main/java/com/mobiapps/recipekeep/util/RecipeDraftRules.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.mobiapps.recipekeep.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeDraftRulesTest {

    @Test fun empty_draft_has_no_content() =
        assertFalse(RecipeDraftRules.hasContent(listOf("", "  ", "\n"), hasImage = false, hasAudio = false))

    @Test fun text_only_has_content() =
        assertTrue(RecipeDraftRules.hasContent(listOf("", "Salt"), hasImage = false, hasAudio = false))

    @Test fun photo_only_has_content() =
        assertTrue(RecipeDraftRules.hasContent(emptyList(), hasImage = true, hasAudio = false))

    @Test fun audio_only_has_content() =
        assertTrue(RecipeDraftRules.hasContent(emptyList(), hasImage = false, hasAudio = true))

    @Test fun typed_title_is_kept_and_trimmed() =
        assertEquals("Pasta", RecipeDraftRules.resolveTitle("  Pasta ", hasAudio = true, nowMillis = 0L))

    @Test fun blank_title_with_audio_falls_back_to_voice_recipe() =
        assertTrue(RecipeDraftRules.resolveTitle(" ", hasAudio = true, nowMillis = 0L).startsWith("Voice recipe · "))

    @Test fun blank_title_without_audio_falls_back_to_untitled() =
        assertTrue(RecipeDraftRules.resolveTitle("", hasAudio = false, nowMillis = 0L).startsWith("Untitled recipe · "))
}
```

- [ ] **Step 2: Run it, expect a compile failure**

```bash
./gradlew :app:testDebugUnitTest --tests "com.mobiapps.recipekeep.util.RecipeDraftRulesTest"
```
Expected: FAIL, `Unresolved reference: RecipeDraftRules`.

- [ ] **Step 3: Implement**

```kotlin
package com.mobiapps.recipekeep.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecipeDraftRules {

    /** A draft is saveable when it has any text, a photo, or at least one voice note. */
    fun hasContent(textFields: List<String>, hasImage: Boolean, hasAudio: Boolean): Boolean =
        hasImage || hasAudio || textFields.any { it.isNotBlank() }

    /** Typed title wins; otherwise "Voice recipe · 23 Nov" / "Untitled recipe · 23 Nov". */
    fun resolveTitle(title: String, hasAudio: Boolean, nowMillis: Long): String {
        if (title.isNotBlank()) return title.trim()
        val date = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(nowMillis))
        return (if (hasAudio) "Voice recipe" else "Untitled recipe") + " · $date"
    }
}
```

- [ ] **Step 4: Run, expect PASS (7 tests)**

```bash
./gradlew :app:testDebugUnitTest --tests "com.mobiapps.recipekeep.util.RecipeDraftRulesTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mobiapps/recipekeep/util/RecipeDraftRules.kt app/src/test/java/com/mobiapps/recipekeep/util/RecipeDraftRulesTest.kt
git commit -m "feat: add RecipeDraftRules for optional-field validation"
```

---

### Task 2: Data layer: `audioPaths`, DB v3, migration

**Files:**
- Modify: `$SRC/data/local/entity/RecipeEntity.kt`, `$SRC/domain/model/Recipe.kt`, `$SRC/data/repository/RecipeRepositoryImpl.kt`, `$SRC/data/local/RecipeDatabase.kt`, `$SRC/di/DatabaseModule.kt`
- Test: `app/src/androidTest/java/com/mobiapps/recipekeep/data/local/dao/RecipeDaoTest.kt`

- [ ] **Step 1: Add failing test** inside `RecipeDaoTest` (after the existing test). Add `import org.junit.Assert.assertTrue` if missing.

```kotlin
    @Test
    fun audioPaths_roundTrip_andDefaultsToEmpty() = runTest {
        val base = RecipeEntity(
            id = "a1", userId = null, title = "Voice", description = "",
            prepTimeMinutes = 0, servings = 0,
            instructions = emptyList(), tags = emptyList(),
            imagePath = null, difficulty = "Medium",
            createdAt = 1L, updatedAt = 1L
        )
        recipeDao.insertRecipe(base)
        recipeDao.insertRecipe(base.copy(id = "a2", audioPaths = listOf("/x/1.m4a", "/x/2.m4a")))

        assertTrue(recipeDao.getRecipeById("a1").first()!!.recipe.audioPaths.isEmpty())
        assertEquals(listOf("/x/1.m4a", "/x/2.m4a"), recipeDao.getRecipeById("a2").first()!!.recipe.audioPaths)
    }
```

- [ ] **Step 2: Run, expect compile failure** (`audioPaths` unknown). Needs a running emulator/device.

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mobiapps.recipekeep.data.local.dao.RecipeDaoTest
```

- [ ] **Step 3: Entity.** In `RecipeEntity.kt` add as the last constructor property (note the comma on `updatedAt`):

```kotlin
    val updatedAt: Long,
    val audioPaths: List<String> = emptyList() // Voice notes; JSON via Converters.kt
)
```

- [ ] **Step 4: Domain model.** In `Recipe.kt` add as the last property:

```kotlin
    val updatedAt: Long,
    val audioPaths: List<String> = emptyList()
)
```

- [ ] **Step 5: Mappers** in `RecipeRepositoryImpl.kt`. In `RecipeWithIngredients.toDomain()` add `audioPaths = recipe.audioPaths` after `updatedAt = recipe.updatedAt` (add the comma). In `Recipe.toEntity()` add `audioPaths = audioPaths` after `createdAt = createdAt, updatedAt = updatedAt` (add the comma).

- [ ] **Step 6: Database v3.** In `RecipeDatabase.kt` set `version = 3` and add inside the companion object:

```kotlin
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN audioPaths TEXT NOT NULL DEFAULT '[]'")
            }
        }
```

- [ ] **Step 7: Register it** in `DatabaseModule.kt`. **Without this, `fallbackToDestructiveMigration()` wipes every user's recipes.**

```kotlin
            .addMigrations(RecipeDatabase.MIGRATION_1_2, RecipeDatabase.MIGRATION_2_3)
```

- [ ] **Step 8: Run DAO tests, expect PASS** (same command as Step 2, all tests in `RecipeDaoTest`).

- [ ] **Step 9: Commit**

```bash
git add -A app/src
git commit -m "feat: add audioPaths to recipes (DB v3 with migration)"
```

---

### Task 3: `AudioRecorder`

**Files:**
- Create: `$SRC/util/AudioRecorder.kt`

No JVM test: `MediaRecorder` is a system service. Verified on-device in Task 9.

- [ ] **Step 1: Create the file**

```kotlin
package com.mobiapps.recipekeep.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

enum class RecState { Idle, Recording, Paused }

/** One recording at a time. Each start()..stop() produces one .m4a clip. */
class AudioRecorder(context: Context) {

    private val appContext = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var accumulatedMs = 0L
    private var segmentStart = 0L

    private val _state = MutableStateFlow(RecState.Idle)
    val state: StateFlow<RecState> = _state.asStateFlow()

    /** Called when the max-duration cap stops the recording. The clip is already finalised. */
    var onAutoStop: ((File) -> Unit)? = null

    fun elapsedMs(): Long =
        accumulatedMs + if (_state.value == RecState.Recording) SystemClock.elapsedRealtime() - segmentStart else 0L

    fun start(): Result<Unit> = runCatching {
        check(_state.value == RecState.Idle) { "Already recording" }
        val out = File(appContext.getExternalFilesDir(null), "recipe_audio_${UUID.randomUUID()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(appContext) else MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(64_000) // ponytail: ~0.5 MB/min, fine for speech
            r.setMaxDuration(MAX_DURATION_MS)
            r.setOutputFile(out.absolutePath)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stop().onSuccess { onAutoStop?.invoke(it) }
                }
            }
            r.prepare()
            r.start()
        } catch (e: Exception) {
            runCatching { r.release() }
            out.delete()
            throw e
        }
        recorder = r
        file = out
        accumulatedMs = 0L
        segmentStart = SystemClock.elapsedRealtime()
        _state.value = RecState.Recording
    }

    fun pause(): Result<Unit> = runCatching {
        val r = checkNotNull(recorder)
        check(_state.value == RecState.Recording)
        r.pause()
        accumulatedMs += SystemClock.elapsedRealtime() - segmentStart
        _state.value = RecState.Paused
    }

    fun resume(): Result<Unit> = runCatching {
        val r = checkNotNull(recorder)
        check(_state.value == RecState.Paused)
        r.resume()
        segmentStart = SystemClock.elapsedRealtime()
        _state.value = RecState.Recording
    }

    /** Finalises the clip. On failure (e.g. stopped instantly) the partial file is deleted. */
    fun stop(): Result<File> {
        val r = recorder ?: return Result.failure(IllegalStateException("Not recording"))
        val f = checkNotNull(file)
        recorder = null
        file = null
        _state.value = RecState.Idle
        val result = runCatching { r.stop(); f }
        runCatching { r.release() }
        if (result.isFailure) f.delete()
        return result
    }

    /** Stops and throws the clip away. */
    fun cancel() {
        stop().onSuccess { it.delete() }
    }

    private companion object {
        const val MAX_DURATION_MS = 10 * 60 * 1000
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mobiapps/recipekeep/util/AudioRecorder.kt
git commit -m "feat: add AudioRecorder with pause/resume and 10 min cap"
```

---

### Task 4: `AudioPlayer`, clip row layout, `AudioClipList`

**Files:**
- Create: `$SRC/util/AudioPlayer.kt`, `app/src/main/res/layout/item_audio_clip.xml`, `$SRC/ui/common/AudioClipList.kt`

- [ ] **Step 1: `AudioPlayer.kt`**

```kotlin
package com.mobiapps.recipekeep.util

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Plays one clip at a time. toggle() = play / pause; a different path switches clip. */
class AudioPlayer {

    private var player: MediaPlayer? = null
    private var loadedPath: String? = null

    private val _playing = MutableStateFlow<String?>(null)
    /** Path that is audibly playing right now, or null (paused / stopped). */
    val playing: StateFlow<String?> = _playing.asStateFlow()

    /** @return false when the clip could not be played (missing / unreadable file). */
    fun toggle(path: String): Boolean {
        val current = player
        if (current != null && loadedPath == path) {
            return runCatching {
                if (current.isPlaying) { current.pause(); _playing.value = null }
                else { current.start(); _playing.value = path }
            }.isSuccess
        }
        stop()
        if (!File(path).exists()) return false
        val mp = MediaPlayer()
        return try {
            mp.setDataSource(path)
            mp.setOnCompletionListener { stop() }
            mp.prepare()
            mp.start()
            player = mp
            loadedPath = path
            _playing.value = path
            true
        } catch (e: Exception) {
            runCatching { mp.release() }
            false
        }
    }

    fun stop() {
        player?.let { runCatching { it.release() } }
        player = null
        loadedPath = null
        _playing.value = null
    }
}
```

- [ ] **Step 2: `item_audio_clip.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingVertical="4dp">

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_play"
        style="@style/Widget.Material3.Button.IconButton.Filled.Tonal"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:contentDescription="Play or pause voice note"
        app:icon="@android:drawable/ic_media_play" />

    <TextView
        android:id="@+id/tv_clip_name"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="12dp"
        android:layout_weight="1"
        android:textAppearance="?attr/textAppearanceBodyLarge" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_delete_clip"
        style="@style/Widget.Material3.Button.IconButton"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:contentDescription="Delete voice note"
        android:visibility="gone"
        app:icon="@android:drawable/ic_delete" />
</LinearLayout>
```

- [ ] **Step 3: `AudioClipList.kt`**

```kotlin
package com.mobiapps.recipekeep.ui.common

import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.mobiapps.recipekeep.databinding.ItemAudioClipBinding
import com.mobiapps.recipekeep.util.AudioPlayer

/** Renders one row per clip into [container]. Pass [onDelete] to show delete buttons (creator only). */
class AudioClipList(
    private val container: LinearLayout,
    private val player: AudioPlayer,
    private val onDelete: ((String) -> Unit)? = null,
    private val onMissing: () -> Unit
) {
    fun render(paths: List<String>, playing: String?) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        paths.forEachIndexed { index, path ->
            val row = ItemAudioClipBinding.inflate(inflater, container, true)
            row.tvClipName.text = "Voice note ${index + 1}"
            row.btnPlay.setIconResource(
                if (path == playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            row.btnPlay.setOnClickListener { if (!player.toggle(path)) onMissing() }
            row.btnDeleteClip.isVisible = onDelete != null
            row.btnDeleteClip.setOnClickListener { onDelete?.invoke(path) }
        }
    }
}
```

- [ ] **Step 4: Compile** (generates `ItemAudioClipBinding`)

```bash
./gradlew :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`. If `Widget.Material3.Button.IconButton.Filled.Tonal` is unresolved, use `Widget.Material3.Button.TonalButton.Icon`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mobiapps/recipekeep/util/AudioPlayer.kt app/src/main/java/com/mobiapps/recipekeep/ui/common app/src/main/res/layout/item_audio_clip.xml
git commit -m "feat: add AudioPlayer and shared clip row list"
```

---

### Task 5: Manifest + creator `ViewModel`

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Rewrite: `$SRC/ui/creator/RecipeCreatorViewModel.kt`

- [ ] **Step 1: Manifest.** After the `AD_ID` permission line add:

```xml
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-feature android:name="android.hardware.microphone" android:required="false" />
```

- [ ] **Step 2: Replace `RecipeCreatorViewModel.kt`** entirely:

```kotlin
package com.mobiapps.recipekeep.ui.creator

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobiapps.recipekeep.data.repository.RecipeRepository
import com.mobiapps.recipekeep.domain.model.Ingredient
import com.mobiapps.recipekeep.domain.model.Recipe
import com.mobiapps.recipekeep.util.AudioRecorder
import com.mobiapps.recipekeep.util.RecState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    object Success : SaveState()
    data class Error(val message: String) : SaveState()
}

@HiltViewModel
class RecipeCreatorViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
    @ApplicationContext context: Context
) : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _recipeToEdit = MutableStateFlow<Recipe?>(null)
    val recipeToEdit: StateFlow<Recipe?> = _recipeToEdit.asStateFlow()

    // --- Voice notes ---
    private val recorder = AudioRecorder(context)
    val recState: StateFlow<RecState> = recorder.state
    fun elapsedMs(): Long = recorder.elapsedMs()

    private val _clips = MutableStateFlow<List<String>>(emptyList())
    val clips: StateFlow<List<String>> = _clips.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val newClips = mutableSetOf<String>()          // recorded this session, not yet saved
    private val removedSavedClips = mutableSetOf<String>() // saved clips the user deleted; files go after save
    private var saved = false

    init {
        recorder.onAutoStop = { file ->
            addClip(file)
            _messages.tryEmit("Reached the 10 minute limit. Clip saved.")
        }
    }

    fun loadRecipe(recipeId: String) {
        viewModelScope.launch {
            val recipe = recipeRepository.getRecipeById(recipeId).firstOrNull()
            // Only seed clips on first load, so rotation doesn't drop clips recorded this session.
            if (_recipeToEdit.value == null) _clips.value = recipe?.audioPaths.orEmpty()
            _recipeToEdit.value = recipe
        }
    }

    fun startRecording() {
        recorder.start().onFailure { _messages.tryEmit("Couldn't start recording. Is the microphone in use?") }
    }

    fun pauseRecording() {
        recorder.pause().onFailure { recorder.cancel(); _messages.tryEmit("Recording failed") }
    }

    fun resumeRecording() {
        recorder.resume().onFailure { recorder.cancel(); _messages.tryEmit("Recording failed") }
    }

    fun stopRecording() {
        recorder.stop()
            .onSuccess { addClip(it) }
            .onFailure { _messages.tryEmit("Recording was too short or failed") }
    }

    fun removeClip(path: String) {
        _clips.update { it - path }
        if (newClips.remove(path)) File(path).delete() else removedSavedClips += path
    }

    private fun addClip(file: File) {
        newClips += file.absolutePath
        _clips.update { it + file.absolutePath }
    }

    fun saveRecipe(
        title: String,
        description: String?,
        prepTimeMinutes: Int,
        servings: Int,
        ingredients: List<Pair<String, Pair<String, String>>>,
        instructions: List<String>,
        tags: List<String>,
        imagePath: String? = null,
        difficulty: String = "Medium"
    ) {
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            try {
                val existingRecipe = _recipeToEdit.value
                val recipeId = existingRecipe?.id ?: UUID.randomUUID().toString()

                val recipe = Recipe(
                    id = recipeId,
                    userId = existingRecipe?.userId,
                    title = title,
                    description = description ?: "",
                    prepTimeMinutes = prepTimeMinutes,
                    servings = servings,
                    ingredients = ingredients.map { (quantity, unitName) ->
                        Ingredient(
                            id = UUID.randomUUID().toString(),
                            recipeId = recipeId,
                            quantity = quantity,
                            unit = unitName.first,
                            name = unitName.second,
                            dietaryTag = ""
                        )
                    },
                    instructions = instructions,
                    tags = tags,
                    imagePath = imagePath ?: existingRecipe?.imagePath,
                    difficulty = difficulty,
                    createdAt = existingRecipe?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    audioPaths = _clips.value
                )
                recipeRepository.saveRecipe(recipe)
                removedSavedClips.forEach { File(it).delete() }
                removedSavedClips.clear()
                newClips.clear()
                saved = true
                _saveState.value = SaveState.Success
            } catch (e: Exception) {
                e.printStackTrace()
                _saveState.value = SaveState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

    override fun onCleared() {
        recorder.cancel() // in-progress recording is discarded
        if (!saved) newClips.forEach { File(it).delete() }
    }
}
```

- [ ] **Step 3: Compile**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: `BUILD SUCCESSFUL`. (Fragment still compiles: `saveRecipe` signature is unchanged.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main
git commit -m "feat: creator ViewModel owns recorder and voice clips"
```

---

### Task 6: Creator UI: voice card + relaxed validation

**Files:**
- Modify: `app/src/main/res/layout/fragment_recipe_creator.xml`
- Modify: `$SRC/ui/creator/RecipeCreatorFragment.kt`

- [ ] **Step 1: Layout.** Insert the card right after the photo card. In `fragment_recipe_creator.xml`, replace

```xml
                        app:icon="@android:drawable/ic_menu_camera" />
                </FrameLayout>
            </com.google.android.material.card.MaterialCardView>
```
with that same text followed by:

```xml

            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="20dp"
                style="@style/Widget.RecipeKeeper.CardView"
                app:strokeWidth="1dp"
                app:strokeColor="?attr/colorOutlineVariant">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Voice notes"
                        android:textAppearance="?attr/textAppearanceTitleMedium"
                        android:textColor="?attr/colorPrimary"
                        android:textStyle="bold" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="4dp"
                        android:text="Optional. Say the recipe in your own language."
                        android:textAppearance="?attr/textAppearanceBodyMedium"
                        android:textColor="?attr/colorOnSurfaceVariant" />

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="12dp"
                        android:gravity="center_vertical"
                        android:orientation="horizontal">

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btn_record"
                            style="@style/Widget.Material3.Button.TonalButton"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Record"
                            app:icon="@android:drawable/ic_btn_speak_now" />

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btn_pause_resume"
                            style="@style/Widget.Material3.Button.TonalButton"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Pause"
                            android:visibility="gone" />

                        <com.google.android.material.button.MaterialButton
                            android:id="@+id/btn_stop"
                            style="@style/Widget.Material3.Button.TextButton"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_marginStart="8dp"
                            android:text="Stop"
                            android:visibility="gone" />

                        <Chronometer
                            android:id="@+id/chronometer"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:layout_marginStart="12dp"
                            android:textAppearance="?attr/textAppearanceTitleMedium"
                            android:visibility="gone" />
                    </LinearLayout>

                    <LinearLayout
                        android:id="@+id/container_clips"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:orientation="vertical" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 2: Hints.** Remove any "required" wording from field hints:

```bash
grep -n -i "hint" app/src/main/res/layout/fragment_recipe_creator.xml
```
If a hint says "Title *" / "required", change it to the plain label (e.g. `Title`).

- [ ] **Step 3: Fragment imports.** Add to `RecipeCreatorFragment.kt`:

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.mobiapps.recipekeep.ui.common.AudioClipList
import com.mobiapps.recipekeep.util.AudioPlayer
import com.mobiapps.recipekeep.util.RecState
import com.mobiapps.recipekeep.util.RecipeDraftRules
import kotlinx.coroutines.flow.combine
```

- [ ] **Step 4: Fragment members.** Below `private var selectedImagePath: String? = null` add:

```kotlin
    private val player = AudioPlayer()

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.startRecording()
            else Snackbar.make(binding.root, "Microphone permission is needed to record", Snackbar.LENGTH_LONG).show()
        }
```

- [ ] **Step 5: Wire the UI.** In `onViewCreated`, right after the `btnAddIngredient` click listener, add:

```kotlin
        val clipList = AudioClipList(
            container = binding.containerClips,
            player = player,
            onDelete = { viewModel.removeClip(it) },
            onMissing = { Snackbar.make(binding.root, "Recording not found", Snackbar.LENGTH_SHORT).show() }
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
                        binding.btnPauseResume.text = if (state == RecState.Paused) "Resume" else "Pause"
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
```

- [ ] **Step 6: Stop recording when the app is backgrounded (not on rotation), and release the player.** Replace the existing `onDestroyView` with:

```kotlin
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
```

- [ ] **Step 7: Edit mode shows blank for unset numbers.** In `populateFields` replace the two `setText` lines:

```kotlin
        binding.etPrepTime.setText(recipe.prepTimeMinutes.takeIf { it > 0 }?.toString().orEmpty())
        binding.etServings.setText(recipe.servings.takeIf { it > 0 }?.toString().orEmpty())
```

- [ ] **Step 8: Replace `saveRecipe()`** (the whole private function, currently ~lines 239-302) with:

```kotlin
    private fun saveRecipe() {
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
            Snackbar.make(binding.root, "Add something first: text, a photo, or a voice note", Snackbar.LENGTH_LONG).show()
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
            title = RecipeDraftRules.resolveTitle(title, hasAudio, System.currentTimeMillis()),
            description = description.ifEmpty { null },
            prepTimeMinutes = prepTime,
            servings = servings,
            ingredients = ingredients,
            instructions = instructions,
            tags = tags,
            imagePath = selectedImagePath,
            difficulty = difficulty
        )
    }
```

- [ ] **Step 9: Build and run all unit tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
git add app/src/main
git commit -m "feat: voice notes card in recipe creator, all fields optional"
```

---

### Task 7: Viewer + dashboard

**Files:**
- Modify: `app/src/main/res/layout/fragment_recipe_viewer.xml`, `$SRC/ui/viewer/RecipeViewerFragment.kt`
- Modify: `app/src/main/res/layout/item_recipe.xml`, `$SRC/ui/dashboard/RecipeAdapter.kt`

- [ ] **Step 1: Viewer layout, stats row id.** In `fragment_recipe_viewer.xml` replace

```xml
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:baselineAligned="false">
```
with

```xml
                    <LinearLayout
                        android:id="@+id/row_stats"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:baselineAligned="false">
```

- [ ] **Step 2: Viewer layout, voice section.** Insert immediately before the `Instructions` title TextView (the one with `android:text="Instructions"`; insert before its opening `<TextView`):

```xml
            <LinearLayout
                android:id="@+id/section_voice"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="24dp"
                android:orientation="vertical"
                android:visibility="gone">

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="12dp"
                    android:text="Voice notes"
                    android:textAppearance="?attr/textAppearanceTitleLarge"
                    android:textStyle="bold" />

                <com.google.android.material.card.MaterialCardView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    app:cardCornerRadius="12dp"
                    app:cardElevation="1dp">

                    <LinearLayout
                        android:id="@+id/container_clips"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:padding="12dp" />
                </com.google.android.material.card.MaterialCardView>
            </LinearLayout>

```

- [ ] **Step 3: Viewer fragment.** Add imports:

```kotlin
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import com.mobiapps.recipekeep.ui.common.AudioClipList
import com.mobiapps.recipekeep.util.AudioPlayer
```
(skip any already present). Add members to the class:

```kotlin
    private val player = AudioPlayer()
    private var clipList: AudioClipList? = null
    private var currentClips: List<String> = emptyList()
```
In `onViewCreated`, after `viewModel.loadRecipe(args.recipeId)` add:

```kotlin
        clipList = AudioClipList(
            container = binding.containerClips,
            player = player,
            onMissing = { Snackbar.make(binding.root, "Recording not found", Snackbar.LENGTH_SHORT).show() }
        )
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                player.playing.collect { clipList?.render(currentClips, it) }
            }
        }
```
Add lifecycle cleanup (merge into the existing `onDestroyView` if one exists):

```kotlin
    override fun onStop() {
        super.onStop()
        player.stop()
    }
```
In `onDestroyView` add `player.stop()` and `clipList = null` before `_binding = null`.

In `displayRecipe`, replace the two lines `binding.tvPrepTime.text = ...` and `binding.tvServings.text = ...` with:

```kotlin
        val hasPrep = recipe.prepTimeMinutes > 0
        val hasServings = recipe.servings > 0
        binding.rowStats.isVisible = hasPrep || hasServings
        binding.tvPrepTime.text = if (hasPrep) "${recipe.prepTimeMinutes} mins" else "—"
        binding.tvServings.text = if (hasServings) currentServings.toString() else "—"
        binding.btnIncreaseServings.isVisible = hasServings
        binding.btnDecreaseServings.isVisible = hasServings

        currentClips = recipe.audioPaths
        binding.sectionVoice.isVisible = currentClips.isNotEmpty()
        clipList?.render(currentClips, player.playing.value)
```

- [ ] **Step 4: Dashboard row id.** In `item_recipe.xml` replace

```xml
                android:layout_marginTop="12dp"
                android:gravity="center_vertical">

                <ImageView
                    android:layout_width="16dp"
                    android:layout_height="16dp"
                    android:src="@android:drawable/ic_menu_recent_history"
```
(the `<LinearLayout` opening tag above it is unchanged) by adding an id to that LinearLayout: put `android:id="@+id/row_stats"` as the first attribute of the `<LinearLayout` that contains `ic_menu_recent_history`.

- [ ] **Step 5: Dashboard adapter.** In `RecipeAdapter.kt` add `import androidx.core.view.isVisible` and replace the two lines around 43-44:

```kotlin
            binding.rowStats.isVisible = recipe.prepTimeMinutes > 0 || recipe.servings > 0
            binding.tvPrepTime.text = if (recipe.prepTimeMinutes > 0) "${recipe.prepTimeMinutes} min" else "—"
            binding.tvServings.text = if (recipe.servings > 0) recipe.servings.toString() else "—"
```

- [ ] **Step 6: Build**

```bash
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main
git commit -m "feat: show voice notes in viewer, hide empty prep/servings"
```

---

### Task 8: Delete audio files with the recipe

**Files:**
- Modify: `$SRC/data/repository/RecipeRepositoryImpl.kt`

- [ ] **Step 1: Edit `deleteRecipe`** and add `import java.io.File`:

```kotlin
    override suspend fun deleteRecipe(recipe: Recipe) {
        recipeDao.deleteRecipe(recipe.toEntity())
        recipe.audioPaths.forEach { File(it).delete() }
    }
```

- [ ] **Step 2: Build**

```bash
./gradlew :app:assembleDebug
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/mobiapps/recipekeep/data/repository/RecipeRepositoryImpl.kt
git commit -m "fix: delete voice note files when a recipe is deleted"
```

---

### Task 9: Verification (release gate)

- [ ] **Step 1: Automated**

```bash
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest
```
Expected: all pass. If `DashboardFragmentTest` fails, check it before blaming this change, and report what you find.

- [ ] **Step 2: Migration (blocker).** On the SAME emulator from Task 0, with the v2 recipes still installed:

```bash
./gradlew :app:installDebug
```
Open the app. **Both Task 0 recipes must still be there**, photo included. If the list is empty, `MIGRATION_2_3` is not registered correctly: stop and fix.

- [ ] **Step 3: Manual device pass** (record the result of each):
  - Record → Pause → Resume → Stop. A clip row appears. Play, pause, and resume playback work.
  - Record a second clip. Two rows. Delete one. One row.
  - Rotate during recording. Timer and state are kept, recording continues.
  - Press Home during recording. On return a clip exists and the state is Idle.
  - Deny the mic permission. A snackbar shows; typing and photo still work.
  - Save with audio only → title is "Voice recipe · <date>", viewer shows the Voice notes section, no stats row.
  - Save with photo only. Save with nothing. The "Add something first" snackbar shows.
  - Edit an audio recipe, add a clip, save. Open it. Both clips play.
  - Leave the creator without saving after recording. In Android Studio's Device Explorer or `adb shell ls /sdcard/Android/data/com.mobiapps.recipekeep/files/` the new `.m4a` is gone.
  - Delete a recipe that has audio. Its `.m4a` files are gone.
  - Speak in a non-English language. Playback is correct (audio is language-agnostic).

- [ ] **Step 4: Push-ready check**

```bash
git status
git log --oneline main..HEAD
```
Expected: clean tree, 8 feature commits. Open a PR when the user asks.

---

## Out of scope (phase 2, separate spec)

Speech-to-text and auto-filling fields from the recording (English-only parsing). Reuse the stored `.m4a` clips as input.
