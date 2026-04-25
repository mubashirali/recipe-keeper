package com.mobiapps.recipekeep.domain.model

data class Recipe(
    val id: String,
    val userId: String?,
    val title: String,
    val description: String,
    val prepTimeMinutes: Int,
    val servings: Int,
    val instructions: List<String>,
    val tags: List<String>,
    val ingredients: List<Ingredient>,
    val imagePath: String? = null,
    val difficulty: String = "Medium", // Easy, Medium, Hard
    val createdAt: Long,
    val updatedAt: Long
)
