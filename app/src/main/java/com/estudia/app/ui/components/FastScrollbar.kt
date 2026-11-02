package com.estudia.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Barra de desplazamiento rápido en el borde derecho: se arrastra el indicador para ir a
 * cualquier parte del documento. Mientras se arrastra muestra una etiqueta (página o %).
 * Solo el indicador recibe toques; el resto del borde deja pasar los gestos al documento.
 *
 * @param fraction posición actual, de 0 (inicio) a 1 (final).
 */
@Composable
fun FastScrollbar(
    fraction: Float,
    onScrollTo: (Float) -> Unit,
    label: (Float) -> String,
    modifier: Modifier = Modifier,
) {
    val latestOnScroll by rememberUpdatedState(onScrollTo)
    val latestFraction by rememberUpdatedState(fraction)
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val thumbHeight = 56.dp
    val touchWidth = 36.dp
    val barWidth by animateDpAsState(if (dragging) 10.dp else 6.dp, label = "barWidth")
    val visibility by animateFloatAsState(if (dragging) 1f else 0.75f, label = "barAlpha")

    BoxWithConstraints(modifier.fillMaxHeight()) {
        val density = LocalDensity.current
        val thumbPx = with(density) { thumbHeight.toPx() }
        val travel = (constraints.maxHeight - thumbPx).coerceAtLeast(1f)
        val shown = if (dragging) dragFraction else fraction.coerceIn(0f, 1f)
        val y = (shown * travel).roundToInt()

        if (dragging) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(-(touchWidth.roundToPx() + 8.dp.roundToPx()), y + (thumbPx / 2).roundToInt() - 20.dp.roundToPx()) },
            ) {
                Text(
                    label(shown),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, y) }
                .size(touchWidth, thumbHeight)
                .pointerInput(travel) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true; dragFraction = latestFraction.coerceIn(0f, 1f) },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, dy ->
                        change.consume()
                        dragFraction = (dragFraction + dy / travel).coerceIn(0f, 1f)
                        latestOnScroll(dragFraction)
                    }
                },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .width(barWidth)
                    .fillMaxHeight()
                    .alpha(visibility)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            )
        }
    }
}
