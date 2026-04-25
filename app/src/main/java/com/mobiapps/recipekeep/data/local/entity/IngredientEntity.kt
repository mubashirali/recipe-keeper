package com.mobiapps.recipekeep.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ingredients",
    foreignKeys = [ForeignKey(
        entity = RecipeEntity::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("recipeId")]
)
data class IngredientEntity(
    @PrimaryKey val id: String,
    val recipeId: String,
    val name: String,
    val quantity: String,    // e.g. "1.5"
    val unit: String,        // e.g. "cups"
    val dietaryTag: String   // e.g. "vegan"; empty string if none
)
