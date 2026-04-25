package com.mobiapps.recipekeep.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageUtils {
    fun compressAndSaveImage(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val fileName = "recipe_${UUID.randomUUID()}.jpg"
            val file = File(context.getExternalFilesDir(null), fileName)
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out) // 70% quality
            out.flush()
            out.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun compressAndSaveImage(context: Context, filePath: String): String? {
        return try {
            val file = File(filePath)
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            
            val fileName = "recipe_${UUID.randomUUID()}.jpg"
            val compressedFile = File(context.getExternalFilesDir(null), fileName)
            val out = FileOutputStream(compressedFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
            out.flush()
            out.close()
            
            // Delete original if it's different (e.g. from camera temp)
            if (file.exists() && file.absolutePath != compressedFile.absolutePath) {
                file.delete()
            }
            
            compressedFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
