package com.estudia.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.estudia.app.data.FolderEntity

@Composable
fun NameDialog(
    title: String,
    initial: String = "",
    confirmLabel: String = "Guardar",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.text.isNotBlank(),
                onClick = { onConfirm(value.text.trim()) },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Elige carpeta de destino mostrando el árbol de carpetas. [excluded] evita mover una carpeta dentro de sí misma. */
@Composable
fun MoveToFolderDialog(
    folders: List<FolderEntity>,
    excluded: Set<Long> = emptySet(),
    onDismiss: () -> Unit,
    onMove: (Long?) -> Unit,
) {
    val tree = remember(folders, excluded) { flattenTree(folders, excluded) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover a…") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                item { FolderRow(Icons.Default.Home, "Inicio", 0) { onMove(null) } }
                items(tree, key = { it.first.id }) { (folder, depth) ->
                    FolderRow(Icons.Default.Folder, folder.name, depth + 1) { onMove(folder.id) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun FolderRow(icon: androidx.compose.ui.graphics.vector.ImageVector, name: String, depth: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (depth * 16).dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(name)
    }
}

private fun flattenTree(folders: List<FolderEntity>, excluded: Set<Long>): List<Pair<FolderEntity, Int>> {
    val byParent = folders.groupBy { it.parentId }
    val out = mutableListOf<Pair<FolderEntity, Int>>()
    fun walk(parent: Long?, depth: Int) {
        for (f in byParent[parent].orEmpty()) {
            if (f.id in excluded) continue
            out += f to depth
            walk(f.id, depth + 1)
        }
    }
    walk(null, 0)
    return out
}

/** La carpeta y todas sus subcarpetas. */
fun descendantsOf(folderId: Long, folders: List<FolderEntity>): Set<Long> {
    val byParent = folders.groupBy { it.parentId }
    val out = mutableSetOf(folderId)
    val queue = ArrayDeque(listOf(folderId))
    while (queue.isNotEmpty()) {
        for (child in byParent[queue.removeFirst()].orEmpty()) {
            if (out.add(child.id)) queue.addLast(child.id)
        }
    }
    return out
}
