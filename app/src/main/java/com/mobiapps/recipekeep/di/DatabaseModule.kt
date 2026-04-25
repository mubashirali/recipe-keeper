package com.mobiapps.recipekeep.di

import android.content.Context
import androidx.room.Room
import com.mobiapps.recipekeep.data.local.RecipeDatabase
import com.mobiapps.recipekeep.data.local.dao.IngredientDao
import com.mobiapps.recipekeep.data.local.dao.RecipeDao
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
        Room.databaseBuilder(context, RecipeDatabase::class.java, "recipe_keeper_db")
            .addMigrations(RecipeDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideRecipeDao(db: RecipeDatabase): RecipeDao = db.recipeDao()

    @Provides
    fun provideIngredientDao(db: RecipeDatabase): IngredientDao = db.ingredientDao()
}
