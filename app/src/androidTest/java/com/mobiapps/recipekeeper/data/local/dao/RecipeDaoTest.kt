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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeDaoTest {

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
    fun insertRecipeWithIngredients_getById_returnsCorrectData() = runTest {
        val recipe = RecipeEntity(
            id = "r1", userId = null, title = "Pasta", description = "Simple",
            prepTimeMinutes = 20, servings = 2,
            instructions = emptyList(), tags = emptyList(),
            createdAt = 1000L, updatedAt = 1000L
        )
        val ingredients = listOf(
            IngredientEntity(id = "i1", recipeId = "r1", name = "Flour",
                quantity = "1", unit = "cup", dietaryTag = "")
        )
        recipeDao.insertRecipe(recipe)
        ingredientDao.insertIngredients(ingredients)

        val result = recipeDao.getRecipeById("r1").first()

        assertNotNull(result)
        assertEquals("Pasta", result!!.recipe.title)
        assertEquals(1, result.ingredients.size)
        assertEquals("Flour", result.ingredients[0].name)
    }
}
