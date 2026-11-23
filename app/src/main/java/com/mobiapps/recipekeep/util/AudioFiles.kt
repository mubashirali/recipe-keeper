package com.mobiapps.recipekeep.util

import android.content.Context
import java.io.File

object AudioFiles {
    /** Voice clips live here so backup rules can exclude them (audio stays on-device). */
    const val DIR_NAME = "audio"

    fun dir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, DIR_NAME).also { it.mkdirs() }

    /**
     * Deletes `recipe_audio_*.m4a` files in [dir] that are not in [referenced] (absolute paths)
     * and were last modified at least [minAgeMs] before [nowMs]. Returns the number deleted.
     */
    fun sweepOrphans(dir: File, referenced: Set<String>, nowMs: Long, minAgeMs: Long): Int {
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return 0
        return files.count { f ->
            f.isFile &&
                f.name.startsWith("recipe_audio_") && f.name.endsWith(".m4a") &&
                f.absolutePath !in referenced &&
                nowMs - f.lastModified() >= minAgeMs &&
                runCatching { f.delete() }.getOrDefault(false)
        }
    }
}
