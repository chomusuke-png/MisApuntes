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

/** Dónde va la barra de herramientas de los editores (documentos, notas y PDF). */
enum class ToolbarPosition(val label: String) {
    TOP("Arriba"),
    BOTTOM("Abajo"),
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

    var toolbarPosition by mutableStateOf(
        runCatching { ToolbarPosition.valueOf(prefs.getString(KEY_TOOLBAR, null) ?: "") }.getOrDefault(ToolbarPosition.TOP)
    )
        private set

    fun updateToolbarPosition(position: ToolbarPosition) {
        toolbarPosition = position
        prefs.edit().putString(KEY_TOOLBAR, position.name).apply()
    }

    /** Grosor del lápiz de PDF, como fracción del ancho de la página. */
    var penWidth by mutableFloatStateOf(prefs.getFloat(KEY_PEN_WIDTH, 0.003f))
        private set

    fun updatePenWidth(value: Float) {
        penWidth = value
        prefs.edit().putFloat(KEY_PEN_WIDTH, value).apply()
    }

    /** El destacador de PDF marca en línea recta (si no, a mano alzada). */
    var highlighterStraight by mutableStateOf(prefs.getBoolean(KEY_HL_STRAIGHT, true))
        private set

    fun updateHighlighterStraight(value: Boolean) {
        highlighterStraight = value
        prefs.edit().putBoolean(KEY_HL_STRAIGHT, value).apply()
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
        const val KEY_TOOLBAR = "toolbar_position"
        const val KEY_PEN_WIDTH = "pen_width"
        const val KEY_HL_STRAIGHT = "highlighter_straight"
        const val KEY_HL_WIDTH = "highlighter_width"
        const val KEY_HL_OPACITY = "highlighter_opacity"
    }
}
