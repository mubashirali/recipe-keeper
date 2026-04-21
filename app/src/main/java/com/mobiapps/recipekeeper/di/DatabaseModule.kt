package com.mobiapps.recipekeeper.di

import android.content.Context
import androidx.room.Room
import com.mobiapps.recipekeeper.data.local.RecipeDatabase
import com.mobiapps.recipekeeper.data.local.dao.IngredientDao
import com.mobiapps.recipekeeper.data.local.dao.RecipeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideRecipeDatabase(@ApplicationContext context: Context): RecipeDatabase =
        Room.databaseBuilder(context, RecipeDatabase::class.java, "recipe_keeper_db").build()

    @Provides
    fun provideRecipeDao(db: RecipeDatabase): RecipeDao = db.recipeDao()

    @Provides
    fun provideIngredientDao(db: RecipeDatabase): IngredientDao = db.ingredientDao()
}
