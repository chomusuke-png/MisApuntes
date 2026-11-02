package com.estudia.app.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Rosa pastel. El primario es un rosa algo más intenso para que los textos rosados se lean bien. */
private val LightColors = lightColorScheme(
    primary = Color(0xFFD25D8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF8D7E5),
    onPrimaryContainer = Color(0xFF5A0F33),
    secondary = Color(0xFF9C5A75),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBE3EC),
    onSecondaryContainer = Color(0xFF3F1527),
    tertiary = Color(0xFFE07A9F),
    background = Color(0xFFFFF8FA),
    onBackground = Color(0xFF22191C),
    surface = Color(0xFFFFF8FA),
    onSurface = Color(0xFF22191C),
    surfaceVariant = Color(0xFFF5E1E8),
    onSurfaceVariant = Color(0xFF524348),
    outline = Color(0xFF84737A),
    outlineVariant = Color(0xFFD7C1C9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1F5),
    surfaceContainer = Color(0xFFFCEAF0),
    surfaceContainerHigh = Color(0xFFF9E3EB),
    surfaceContainerHighest = Color(0xFFF6DCE6),
)

/** Rosa profundo para el tema oscuro. */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFF0629A),
    onPrimary = Color(0xFF4A0024),
    primaryContainer = Color(0xFF8E1450),
    onPrimaryContainer = Color(0xFFFFD9E4),
    secondary = Color(0xFFE3A3BE),
    onSecondary = Color(0xFF46182D),
    secondaryContainer = Color(0xFF5E2741),
    onSecondaryContainer = Color(0xFFFFD9E4),
    tertiary = Color(0xFFF48FB1),
    background = Color(0xFF1A1114),
    onBackground = Color(0xFFF0DEE3),
    surface = Color(0xFF1A1114),
    onSurface = Color(0xFFF0DEE3),
    surfaceVariant = Color(0xFF4A3740),
    onSurfaceVariant = Color(0xFFD7C1C9),
    outline = Color(0xFF9F8C94),
    outlineVariant = Color(0xFF524348),
    surfaceContainerLowest = Color(0xFF140C0F),
    surfaceContainerLow = Color(0xFF22181C),
    surfaceContainer = Color(0xFF261C20),
    surfaceContainerHigh = Color(0xFF31262A),
    surfaceContainerHighest = Color(0xFF3C3035),
)

@Composable
fun EstudiaTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

/** Barras superiores en rosa (pastel en claro, profundo en oscuro). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appBarColors(): TopAppBarColors {
    val c = MaterialTheme.colorScheme
    return TopAppBarDefaults.topAppBarColors(
        containerColor = c.primaryContainer,
        titleContentColor = c.onPrimaryContainer,
        navigationIconContentColor = c.onPrimaryContainer,
        actionIconContentColor = c.onPrimaryContainer,
    )
}
