package com.mobiapps.recipekeep.util

import android.content.Context
import java.io.File

object AudioFiles {
    /** Voice clips live here so backup rules can exclude them (audio stays on-device). */
    const val DIR_NAME = "audio"

    fun dir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, DIR_NAME).also { it.mkdirs() }
}
