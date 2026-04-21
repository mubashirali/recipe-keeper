package com.mobiapps.recipekeeper.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mobiapps.recipekeeper.data.local.RecipeDatabase
import com.mobiapps.recipekeeper.data.local.entity.IngredientEntity
import com.mobiapps.recipekeeper.data.local.entity.RecipeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IngredientDaoTest {

    private lateinit var db: RecipeDatabase
    private lateinit var recipeDao: RecipeDao
    private lateinit var ingredientDao: IngredientDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RecipeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        recipeDao = db.recipeDao()
        ingredientDao = db.ingredientDao()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun deleteRecipe_cascadeDeletesItsIngredients() = runTest {
        val recipe = RecipeEntity(
            id = "r1", userId = null, title = "Pasta", description = "",
            prepTimeMinutes = 20, servings = 2,
            instructions = emptyList(), tags = emptyList(),
            createdAt = 1000L, updatedAt = 1000L
        )
        recipeDao.insertRecipe(recipe)
        ingredientDao.insertIngredients(listOf(
            IngredientEntity(id = "i1", recipeId = "r1", name = "Flour",
                quantity = "1", unit = "cup", dietaryTag = "")
        ))

        recipeDao.deleteRecipe(recipe)

        val result = recipeDao.getRecipeById("r1").first()
        assertNull("Ingredient should be cascade-deleted with its recipe", result)
    }
}
