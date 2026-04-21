package com.mobiapps.recipekeeper.domain.model

data class Ingredient(
    val id: String,
    val recipeId: String,
    val name: String,
    val quantity: String,
    val unit: String,
    val dietaryTag: String
)
