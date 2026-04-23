package com.mobiapps.recipekeeper.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val userId: String?,           // Null in Phase 0; set in Phase 1 after auth
    val title: String,             // Non-null; required
    val description: String,
    val prepTimeMinutes: Int,      // Non-null; required
    val servings: Int,             // Non-null; required
    val instructions: List<String>, // Stored as JSON via Converters.kt
    val tags: List<String>,        // Stored as JSON via Converters.kt
    val imagePath: String?,
    val difficulty: String,
    val createdAt: Long,           // epoch millis
    val updatedAt: Long
)
