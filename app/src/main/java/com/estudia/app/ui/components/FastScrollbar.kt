package com.estudia.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SETTLE_MS = 500L

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
    onDragging: (Boolean) -> Unit = {},
) {
    val latestOnScroll by rememberUpdatedState(onScrollTo)
    val latestOnDragging by rememberUpdatedState(onDragging)
    val latestFraction by rememberUpdatedState(fraction)
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    // al soltar, la posicion real llega un poco despues: se mantiene la del dedo hasta que coincidan
    var settling by remember { mutableStateOf(false) }
    // donde quedo puesto el indicador, para pasar el toque a posicion en el riel
    val placed = remember { FloatArray(1) }

    val thumbHeight = 56.dp
    val touchWidth = 36.dp
    val barWidth by animateDpAsState(if (dragging) 10.dp else 6.dp, label = "barWidth")
    val visibility by animateFloatAsState(if (dragging) 1f else 0.75f, label = "barAlpha")

    if (settling) {
        LaunchedEffect(Unit) {
            withTimeoutOrNull(SETTLE_MS) {
                snapshotFlow { latestFraction }.first { abs(it - dragFraction) < 0.01f }
            }
            settling = false
        }
    }

    BoxWithConstraints(modifier.fillMaxHeight()) {
        val density = LocalDensity.current
        val thumbPx = with(density) { thumbHeight.toPx() }
        val travel = (constraints.maxHeight - thumbPx).coerceAtLeast(1f)
        val shown = if (dragging || settling) dragFraction else fraction.coerceIn(0f, 1f)
        val y = (shown * travel).roundToInt()
        // el alto puede cambiar a mitad del arrastre: como clave de pointerInput cortaba el gesto y soltaba el dedo
        val currentTravel by rememberUpdatedState(travel)

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
                .offset { IntOffset(0, y).also { placed[0] = it.y.toFloat() } }
                .size(touchWidth, thumbHeight)
                .pointerInput(Unit) {
                    // el indicador se mueve bajo el dedo: sumar pasos chicos se desfasaba al ir rapido,
                    // asi que la posicion sale de donde esta el dedo en el riel, menos donde se tomo el indicador
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val grab = down.position.y
                        settling = false
                        dragFraction = (placed[0] / currentTravel).coerceIn(0f, 1f)
                        dragging = true
                        latestOnDragging(true)
                        var last = dragFraction
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                dragFraction = ((placed[0] + change.position.y - grab) / currentTravel).coerceIn(0f, 1f)
                                if (dragFraction != last) {
                                    last = dragFraction
                                    latestOnScroll(dragFraction)
                                }
                            }
                        } finally {
                            dragging = false
                            settling = true
                            latestOnDragging(false)
                        }
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
