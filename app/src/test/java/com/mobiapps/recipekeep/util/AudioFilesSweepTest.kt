package com.mobiapps.recipekeep.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AudioFilesSweepTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val now = 10_000_000L
    private val minAge = 1_000_000L

    private fun file(name: String, modified: Long): File =
        tmp.newFile(name).also { it.setLastModified(modified) }

    private val old get() = now - minAge - 1
    private val recent get() = now - minAge + 1

    @Test
    fun unreferencedOld_isDeleted() {
        val f = file("recipe_audio_a.m4a", old)
        assertEquals(1, AudioFiles.sweepOrphans(tmp.root, emptySet(), now, minAge))
        assertFalse(f.exists())
    }

    @Test
    fun referencedOld_isKept() {
        val f = file("recipe_audio_a.m4a", old)
        assertEquals(0, AudioFiles.sweepOrphans(tmp.root, setOf(f.absolutePath), now, minAge))
        assertTrue(f.exists())
    }

    @Test
    fun unreferencedRecent_isKept() {
        val f = file("recipe_audio_a.m4a", recent)
        assertEquals(0, AudioFiles.sweepOrphans(tmp.root, emptySet(), now, minAge))
        assertTrue(f.exists())
    }

    @Test
    fun nonMatchingName_isKept() {
        val a = file("photo.jpg", old)
        val b = file("recipe_audio_a.mp3", old)
        val dir = tmp.newFolder("recipe_audio_dir.m4a").also { it.setLastModified(old) }
        assertEquals(0, AudioFiles.sweepOrphans(tmp.root, emptySet(), now, minAge))
        assertTrue(a.exists() && b.exists() && dir.exists())
    }

    @Test
    fun missingDir_returnsZero() {
        assertEquals(0, AudioFiles.sweepOrphans(File(tmp.root, "nope"), emptySet(), now, minAge))
    }

    @Test
    fun returnsCountDeleted() {
        file("recipe_audio_1.m4a", old)
        file("recipe_audio_2.m4a", old)
        val kept = file("recipe_audio_3.m4a", old)
        file("recipe_audio_4.m4a", recent)
        assertEquals(2, AudioFiles.sweepOrphans(tmp.root, setOf(kept.absolutePath), now, minAge))
    }
}
