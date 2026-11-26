package com.mobiapps.recipekeep.util

import android.content.Context
import com.mobiapps.recipekeep.R

/**
 * Difficulty is stored in the database as the English keys "Easy" / "Medium" / "Hard"
 * (existing data and the creator chips rely on them), so only the label shown to the user is localized.
 */
fun difficultyLabel(context: Context, stored: String): String = context.getString(
    when (stored) {
        "Easy" -> R.string.difficulty_easy
        "Hard" -> R.string.difficulty_hard
        else -> R.string.difficulty_medium
    }
)
