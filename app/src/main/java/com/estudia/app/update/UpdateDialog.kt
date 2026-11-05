package com.estudia.app.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.estudia.app.EstudiaApp

/** va solo en las pantallas sin edicion, porque instalar cierra la app */
@Composable
fun UpdateDialog() {
    val updater = (LocalContext.current.applicationContext as EstudiaApp).updater
    when (val state = updater.state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = updater::dismiss,
            title = { Text("Versión ${state.release.version} disponible") },
            text = {
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    if (state.release.notes.isNotEmpty()) {
                        Text(state.release.notes)
                        Spacer(Modifier.height(12.dp))
                    }
                    Text("Tus documentos se conservan. Android te pedirá confirmar la instalación.")
                }
            },
            confirmButton = { TextButton(onClick = { updater.download(state.release) }) { Text("Actualizar") } },
            dismissButton = { TextButton(onClick = updater::dismiss) { Text("Ahora no") } },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Descargando ${state.release.version}") },
            text = { LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = updater::dismiss) { Text("Cancelar") } },
        )
        is UpdateState.Failed -> state.release?.let { release ->
            AlertDialog(
                onDismissRequest = updater::dismiss,
                title = { Text("No se pudo actualizar") },
                text = { Text(state.message) },
                confirmButton = { TextButton(onClick = { updater.download(release) }) { Text("Reintentar") } },
                dismissButton = { TextButton(onClick = updater::dismiss) { Text("Cerrar") } },
            )
        }
        else -> Unit
    }
}
