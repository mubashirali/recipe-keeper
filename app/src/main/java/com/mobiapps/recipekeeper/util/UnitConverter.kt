package com.mobiapps.recipekeeper.util

import java.util.Locale

object UnitConverter {
    
    // Scale quantity based on servings
    fun scaleQuantity(quantityStr: String, fromServings: Int, toServings: Int): String {
        if (fromServings == toServings || fromServings <= 0) return quantityStr
        
        val factor = toServings.toFloat() / fromServings.toFloat()
        
        // Handle common fractions
        val decimalValue = parseQuantity(quantityStr) ?: return quantityStr
        val scaledValue = decimalValue * factor
        
        return formatQuantity(scaledValue)
    }

    private fun parseQuantity(quantityStr: String): Float? {
        val trimmed = quantityStr.trim().replace(",", ".")
        
        // Try direct float parsing
        trimmed.toFloatOrNull()?.let { return it }
        
        // Try common fractions like 1/2, 1/4, 3/4
        if (trimmed.contains("/")) {
            val parts = trimmed.split("/")
            if (parts.size == 2) {
                val num = parts[0].trim().toFloatOrNull()
                val den = parts[1].trim().toFloatOrNull()
                if (num != null && den != null && den != 0f) {
                    return num / den
                }
            }
        }
        
        // Handle mixed numbers like "1 1/2"
        val mixedParts = trimmed.split(" ")
        if (mixedParts.size == 2) {
            val whole = mixedParts[0].toFloatOrNull()
            val fraction = parseQuantity(mixedParts[1])
            if (whole != null && fraction != null) {
                return whole + fraction
            }
        }

        return null
    }

    private fun formatQuantity(value: Float): String {
        // Round to 2 decimal places and remove trailing zeros
        val formatted = String.format(Locale.getDefault(), "%.2f", value)
            .replace(",", ".")
            .trimEnd('0')
            .trimEnd('.')
            
        // Convert some decimals back to common fractions if they are very close
        return when {
            Math.abs(value - 0.25f) < 0.01f -> "1/4"
            Math.abs(value - 0.5f) < 0.01f -> "1/2"
            Math.abs(value - 0.75f) < 0.01f -> "3/4"
            Math.abs(value - 0.33f) < 0.02f -> "1/3"
            Math.abs(value - 0.66f) < 0.02f -> "2/3"
            else -> formatted
        }
    }
}
