package com.mobiapps.recipekeep.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecipeDraftRules {

    /** A draft is saveable when it has any text, a photo, or at least one voice note. */
    fun hasContent(textFields: List<String>, hasImage: Boolean, hasAudio: Boolean): Boolean =
        hasImage || hasAudio || textFields.any { it.isNotBlank() }

    /** Typed title wins; otherwise "Voice recipe · 23 Nov" / "Untitled recipe · 23 Nov". */
    fun resolveTitle(
        title: String,
        hasAudio: Boolean,
        nowMillis: Long,
        voiceTitle: String = "Voice recipe",
        untitledTitle: String = "Untitled recipe"
    ): String {
        if (title.isNotBlank()) return title.trim()
        val date = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(nowMillis))
        return (if (hasAudio) voiceTitle else untitledTitle) + " · $date"
    }
}
