package com.estudia.app.speech

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.estudia.app.EstudiaApp

/** voz del sistema del telefono en el ajuste de voz */
const val SYSTEM_VOICE = "system"

private const val SAMPLE = "Hola. Así sueno leyendo tus apuntes. Puedes cambiar la velocidad cuando quieras."

/** elegir la voz: la del telefono o una natural, que se descarga una vez */
@Composable
fun ReadVoiceSettings() {
    val context = LocalContext.current
    val app = context.applicationContext as EstudiaApp
    val settings = app.settings
    val voices = app.naturalVoices
    var deleting by remember { mutableStateOf<NaturalVoice?>(null) }
    val preview = rememberReadAloud()

    Column(Modifier.selectableGroup()) {
        VoiceOption(
            title = "Voz del teléfono",
            detail = "La que esté elegida en Android",
            selected = settings.readVoice == SYSTEM_VOICE,
            onSelect = { settings.updateReadVoice(SYSTEM_VOICE) },
        )
        NATURAL_VOICES.forEach { voice ->
            val installed = voices.isInstalled(voice)
            val busy = voices.downloading == voice.id
            VoiceOption(
                title = "${voice.name} · ${voice.country}",
                detail = when {
                    busy && voices.installing -> "Instalando… ${(voices.progress * 100).toInt()} %"
                    busy -> "Descargando… ${(voices.progress * 100).toInt()} %"
                    installed -> "Voz natural · ${voice.license}"
                    else -> "Voz natural · descargar ${voice.sizeLabel}"
                },
                selected = settings.readVoice == voice.id && installed,
                progress = if (busy) voices.progress else null,
                onSelect = {
                    when {
                        installed -> settings.updateReadVoice(voice.id)
                        voices.downloading == null -> voices.download(voice) { settings.updateReadVoice(voice.id) }
                    }
                },
                action = {
                    when {
                        busy -> IconButton(onClick = voices::cancel) { Icon(Icons.Default.Close, "Cancelar descarga") }
                        installed -> IconButton(onClick = { deleting = voice }) { Icon(Icons.Default.Delete, "Borrar voz") }
                        else -> IconButton(
                            onClick = { voices.download(voice) { settings.updateReadVoice(voice.id) } },
                            enabled = voices.downloading == null,
                        ) { Icon(Icons.Default.Download, "Descargar voz") }
                    }
                },
            )
        }
    }
    voices.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
    ListItem(
        headlineContent = { Text(if (preview.active) "Detener prueba" else "Probar voz") },
        supportingContent = { Text(preview.error ?: if (preview.preparing) "Preparando la voz…" else "Lee una frase con la voz elegida") },
        leadingContent = { Icon(if (preview.active) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null) },
        modifier = Modifier.clickable { if (preview.active) preview.stop() else preview.start(listOf(SAMPLE), 0) },
    )
    ListItem(
        headlineContent = { Text("Ajustes de voz del teléfono") },
        supportingContent = { Text("Para la voz del teléfono: elegir el motor o instalar voces en español") },
        leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
        modifier = Modifier.clickable {
            // la pantalla de voces no existe con ese nombre en todos los telefonos
            runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                .recoverCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
        },
    )
    Text(
        "Las voces naturales son voces Piper distribuidas por el proyecto sherpa-onnx (Apache 2.0). " +
            "Funcionan sin internet una vez descargadas. Cada una indica su licencia.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )

    deleting?.let { voice ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Borrar la voz ${voice.name}?") },
            text = { Text("Se liberan unos ${voice.sizeLabel}. Podrás descargarla otra vez cuando quieras.") },
            confirmButton = {
                TextButton(onClick = {
                    if (settings.readVoice == voice.id) settings.updateReadVoice(SYSTEM_VOICE)
                    voices.delete(voice)
                    deleting = null
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun VoiceOption(
    title: String,
    detail: String,
    selected: Boolean,
    onSelect: () -> Unit,
    progress: Float? = null,
    action: @Composable () -> Unit = {},
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(detail)
                if (progress != null) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        trailingContent = action,
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    )
}
