package com.mobiapps.recipekeep.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeDraftRulesTest {

    @Test fun empty_draft_has_no_content() =
        assertFalse(RecipeDraftRules.hasContent(listOf("", "  ", "\n"), hasImage = false, hasAudio = false))

    @Test fun text_only_has_content() =
        assertTrue(RecipeDraftRules.hasContent(listOf("", "Salt"), hasImage = false, hasAudio = false))

    @Test fun photo_only_has_content() =
        assertTrue(RecipeDraftRules.hasContent(emptyList(), hasImage = true, hasAudio = false))

    @Test fun audio_only_has_content() =
        assertTrue(RecipeDraftRules.hasContent(emptyList(), hasImage = false, hasAudio = true))

    @Test fun typed_title_is_kept_and_trimmed() =
        assertEquals("Pasta", RecipeDraftRules.resolveTitle("  Pasta ", hasAudio = true, nowMillis = 0L))

    @Test fun blank_title_with_audio_falls_back_to_voice_recipe() =
        assertTrue(RecipeDraftRules.resolveTitle(" ", hasAudio = true, nowMillis = 0L).startsWith("Voice recipe · "))

    @Test fun blank_title_without_audio_falls_back_to_untitled() =
        assertTrue(RecipeDraftRules.resolveTitle("", hasAudio = false, nowMillis = 0L).startsWith("Untitled recipe · "))
}
