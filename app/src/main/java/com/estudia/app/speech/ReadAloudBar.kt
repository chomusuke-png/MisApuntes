package com.estudia.app.speech

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.estudia.app.EstudiaApp

/** un lector por pantalla. Se apaga al salir de ella y usa la velocidad elegida en ajustes */
@Composable
fun rememberReadAloud(): ReadAloud {
    val context = LocalContext.current
    val settings = (context.applicationContext as EstudiaApp).settings
    val reader = remember { ReadAloud(context, settings.readSpeed) }
    reader.speed = settings.readSpeed
    DisposableEffect(reader) { onDispose { reader.shutdown() } }
    return reader
}

fun speedLabel(speed: Float) = "${speed.toString().removeSuffix(".0").replace('.', ',')}×"

/** reproductor flotante: anterior, pausa, siguiente, donde va la lectura, velocidad y cerrar */
@Composable
fun ReadAloudBar(reader: ReadAloud, label: String, modifier: Modifier = Modifier) {
    val settings = (LocalContext.current.applicationContext as EstudiaApp).settings
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier.widthIn(max = 520.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = reader::previous, enabled = reader.index > 0) {
                Icon(Icons.Default.SkipPrevious, "Anterior")
            }
            FilledIconButton(onClick = reader::toggle, enabled = reader.error == null) {
                Icon(
                    if (reader.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (reader.playing) "Pausar" else "Leer",
                )
            }
            IconButton(onClick = reader::next, enabled = reader.index < reader.segments.lastIndex) {
                Icon(Icons.Default.SkipNext, "Siguiente")
            }
            Text(
                reader.error ?: label,
                style = MaterialTheme.typography.bodySmall,
                color = if (reader.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            )
            Box {
                var open by remember { mutableStateOf(false) }
                TextButton(onClick = { open = true }) { Text(speedLabel(settings.readSpeed)) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    READ_SPEEDS.forEach { s ->
                        DropdownMenuItem(
                            text = {
                                Text(speedLabel(s), fontWeight = if (s == settings.readSpeed) FontWeight.Bold else FontWeight.Normal)
                            },
                            onClick = { open = false; settings.updateReadSpeed(s) },
                        )
                    }
                }
            }
            IconButton(onClick = reader::stop) { Icon(Icons.Default.Close, "Dejar de leer") }
        }
    }
}
