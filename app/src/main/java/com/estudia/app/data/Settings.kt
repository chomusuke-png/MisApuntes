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

    /** cuanto suaviza el pulso el lapiz de PDF, de 0 a 1 */
    var penStabilizer by mutableFloatStateOf(prefs.getFloat(KEY_PEN_STABILIZER, 0.4f))
        private set

    fun updatePenStabilizer(value: Float) {
        penStabilizer = value
        prefs.edit().putFloat(KEY_PEN_STABILIZER, value).apply()
    }

    /** el grosor del lapiz sigue la presion cuando el dispositivo la mide. Si no, la velocidad */
    var penPressure by mutableStateOf(prefs.getBoolean(KEY_PEN_PRESSURE, true))
        private set

    fun updatePenPressure(value: Boolean) {
        penPressure = value
        prefs.edit().putBoolean(KEY_PEN_PRESSURE, value).apply()
    }

    /** el borrador de PDF quita solo lo que toca (si no, el trazo completo) */
    var eraserPartial by mutableStateOf(prefs.getBoolean(KEY_ERASER_PARTIAL, false))
        private set

    fun updateEraserPartial(value: Boolean) {
        eraserPartial = value
        prefs.edit().putBoolean(KEY_ERASER_PARTIAL, value).apply()
    }

    /** radio del borrador de PDF en dp */
    var eraserSize by mutableFloatStateOf(prefs.getFloat(KEY_ERASER_SIZE, 14f))
        private set

    fun updateEraserSize(value: Float) {
        eraserSize = value
        prefs.edit().putFloat(KEY_ERASER_SIZE, value).apply()
    }

    /** rango de presion del dedo visto en este dispositivo, para el grosor del lapiz */
    val fingerPressure = PressureRange(prefs.getFloat(KEY_PRESSURE_MIN, 1f), prefs.getFloat(KEY_PRESSURE_MAX, 0f))

    fun saveFingerPressure() {
        fingerPressure.endStroke()
        prefs.edit()
            .putFloat(KEY_PRESSURE_MIN, fingerPressure.min)
            .putFloat(KEY_PRESSURE_MAX, fingerPressure.max)
            .apply()
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

    /** busca una version nueva cada vez que se abre la app */
    var autoUpdateCheck by mutableStateOf(prefs.getBoolean(KEY_AUTO_UPDATE, true))
        private set

    fun updateAutoUpdateCheck(value: Boolean) {
        autoUpdateCheck = value
        prefs.edit().putBoolean(KEY_AUTO_UPDATE, value).apply()
    }

    private companion object {
        const val KEY_AUTO_UPDATE = "auto_update_check"
        const val KEY_THEME = "theme"
        const val KEY_TOOLBAR = "toolbar_position"
        const val KEY_PEN_WIDTH = "pen_width"
        const val KEY_PEN_STABILIZER = "pen_stabilizer"
        const val KEY_PEN_PRESSURE = "pen_pressure"
        const val KEY_ERASER_PARTIAL = "eraser_partial"
        const val KEY_ERASER_SIZE = "eraser_size"
        const val KEY_PRESSURE_MIN = "finger_pressure_min"
        const val KEY_PRESSURE_MAX = "finger_pressure_max"
        const val KEY_HL_STRAIGHT = "highlighter_straight"
        const val KEY_HL_WIDTH = "highlighter_width"
        const val KEY_HL_OPACITY = "highlighter_opacity"
    }
}
