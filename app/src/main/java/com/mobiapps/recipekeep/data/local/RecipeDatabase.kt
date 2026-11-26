package com.mobiapps.recipekeep.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mobiapps.recipekeep.data.local.converter.Converters
import com.mobiapps.recipekeep.data.local.dao.IngredientDao
import com.mobiapps.recipekeep.data.local.dao.RecipeDao
import com.mobiapps.recipekeep.data.local.entity.IngredientEntity
import com.mobiapps.recipekeep.data.local.entity.RecipeEntity

@Database(
    entities = [RecipeEntity::class, IngredientEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RecipeDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun ingredientDao(): IngredientDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN imagePath TEXT")
                db.execSQL("ALTER TABLE recipes ADD COLUMN difficulty TEXT NOT NULL DEFAULT 'Medium'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recipes ADD COLUMN audioPaths TEXT NOT NULL DEFAULT '[]'")
            }
        }
    }
}
