package com.estudia.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode(val label: String) {
    SYSTEM("Igual que el sistema"),
    LIGHT("Claro"),
    DARK("Oscuro"),
}

/** Preferencias de la app. El estado es observable desde Compose. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var themeMode by mutableStateOf(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM)
    )
        private set

    fun updateTheme(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    /** Grosor del destacador de PDF, como fracción del ancho de la página. */
    var highlighterWidth by mutableFloatStateOf(prefs.getFloat(KEY_HL_WIDTH, 0.022f))
        private set

    var highlighterOpacity by mutableFloatStateOf(prefs.getFloat(KEY_HL_OPACITY, 1f))
        private set

    fun updateHighlighterWidth(value: Float) {
        highlighterWidth = value
        prefs.edit().putFloat(KEY_HL_WIDTH, value).apply()
    }

    fun updateHighlighterOpacity(value: Float) {
        highlighterOpacity = value
        prefs.edit().putFloat(KEY_HL_OPACITY, value).apply()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_HL_WIDTH = "highlighter_width"
        const val KEY_HL_OPACITY = "highlighter_opacity"
    }
}
