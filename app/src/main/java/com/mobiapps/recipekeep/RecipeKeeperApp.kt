package com.mobiapps.recipekeep

import android.app.Application
import com.mobiapps.recipekeep.data.repository.RecipeRepository
import com.mobiapps.recipekeep.util.AudioFiles
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RecipeKeeperApp : Application() {

    @Inject lateinit var recipeRepository: RecipeRepository

    override fun onCreate() {
        super.onCreate()
        // Remove clips left behind when the process died after recording but before saving.
        // 24h grace period protects a clip that was just recorded and is not saved yet.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val referenced = recipeRepository.getAllRecipes().first()
                    .flatMap { it.audioPaths }.toSet()
                AudioFiles.sweepOrphans(
                    AudioFiles.dir(this@RecipeKeeperApp),
                    referenced,
                    System.currentTimeMillis(),
                    24L * 60 * 60 * 1000,
                )
            }
        }
    }
}
