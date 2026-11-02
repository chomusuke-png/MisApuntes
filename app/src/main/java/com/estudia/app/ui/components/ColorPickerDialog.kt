package com.estudia.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import android.graphics.Color as AndroidColor

val HIGHLIGHT_PRESETS = listOf(
    0xFFFFF59D, 0xFFC5E1A5, 0xFF90CAF9, 0xFFF48FB1,
    0xFFFFCC80, 0xFFCE93D8, 0xFF80DEEA, 0xFFEF9A9A,
).map { it.toInt() }

val TEXT_PRESETS = listOf(
    0xFF000000, 0xFFD32F2F, 0xFF1976D2, 0xFF388E3C,
    0xFFF57C00, 0xFF7B1FA2, 0xFF616161, 0xFF5D4037,
).map { it.toInt() }

/**
 * Selector de color libre (saturación/brillo + tono + hexadecimal), con fila de colores
 * recientes y una paleta rápida.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Int,
    recent: List<Int>,
    presets: List<Int>,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val hsv = remember { FloatArray(3).also { AndroidColor.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(hue, sat, value)
    var hexText by remember { mutableStateOf(toHex(color.toArgb())) }

    fun setColor(argb: Int) {
        val out = FloatArray(3)
        AndroidColor.colorToHSV(argb, out)
        hue = out[0]; sat = out[1]; value = out[2]
        hexText = toHex(argb)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (recent.isNotEmpty()) {
                    Text("Recientes", style = MaterialTheme.typography.labelLarge)
                    SwatchRow(recent, color.toArgb(), ::setColor)
                }
                Text("Paleta", style = MaterialTheme.typography.labelLarge)
                SwatchRow(presets, color.toArgb(), ::setColor)

                SaturationValuePanel(hue, sat, value) { s, v ->
                    sat = s; value = v
                    hexText = toHex(Color.hsv(hue, s, v).toArgb())
                }
                HueBar(hue) {
                    hue = it
                    hexText = toHex(Color.hsv(it, sat, value).toArgb())
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { input ->
                            hexText = input.take(7)
                            parseHex(input)?.let { argb ->
                                val out = FloatArray(3)
                                AndroidColor.colorToHSV(argb, out)
                                hue = out[0]; sat = out[1]; value = out[2]
                            }
                        },
                        label = { Text("Hex") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(color.toArgb()) }) { Text("Aplicar") }
        },
        dismissButton = {
            Row {
                if (onClear != null) TextButton(onClick = onClear) { Text("Quitar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
fun SwatchRow(colors: List<Int>, selected: Int?, onClick: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(colors) { argb ->
            Swatch(argb, selected = argb == selected, onClick = { onClick(argb) })
        }
    }
}

@Composable
fun Swatch(argb: Int, selected: Boolean, size: Int = 32, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun SaturationValuePanel(hue: Float, sat: Float, value: Float, onChange: (Float, Float) -> Unit) {
    // pointerInput se crea una sola vez: hay que leer siempre el callback más reciente
    val latestOnChange by rememberUpdatedState(onChange)
    fun update(pos: Offset, size: IntSize) {
        val s = (pos.x / size.width).coerceIn(0f, 1f)
        val v = 1f - (pos.y / size.height).coerceIn(0f, 1f)
        latestOnChange(s, v)
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(down.position, size)
                    drag(down.id) { change ->
                        update(change.position, size)
                        change.consume()
                    }
                }
            }
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val center = Offset(sat * size.width, (1f - value) * size.height)
        drawCircle(Color.White, radius = 11.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black, radius = 13.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    val hues = remember { (0..6).map { Color.hsv(minOf(it * 60f, 359f), 1f, 1f) } }
    val latestOnChange by rememberUpdatedState(onChange)
    fun update(x: Float, width: Int) = latestOnChange((x / width).coerceIn(0f, 1f) * 359f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(down.position.x, size.width)
                    drag(down.id) { change ->
                        update(change.position.x, size.width)
                        change.consume()
                    }
                }
            }
    ) {
        drawRect(Brush.horizontalGradient(hues))
        val x = hue / 359f * size.width
        drawCircle(Color.White, radius = size.height / 2.2f, center = Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
    }
}

private fun toHex(argb: Int) = "#%06X".format(argb and 0xFFFFFF)

private fun parseHex(text: String): Int? {
    val clean = text.trim().removePrefix("#")
    if (clean.length != 6) return null
    return clean.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
}
