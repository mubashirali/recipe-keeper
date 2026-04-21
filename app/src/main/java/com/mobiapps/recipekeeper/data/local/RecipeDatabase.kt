package com.mobiapps.recipekeeper.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mobiapps.recipekeeper.data.local.converter.Converters
import com.mobiapps.recipekeeper.data.local.dao.IngredientDao
import com.mobiapps.recipekeeper.data.local.dao.RecipeDao
import com.mobiapps.recipekeeper.data.local.entity.IngredientEntity
import com.mobiapps.recipekeeper.data.local.entity.RecipeEntity

@Database(
    entities = [RecipeEntity::class, IngredientEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RecipeDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun ingredientDao(): IngredientDao
}
