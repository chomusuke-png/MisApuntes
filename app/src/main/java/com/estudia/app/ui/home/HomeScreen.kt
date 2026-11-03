package com.estudia.app.ui.home

import android.widget.Toast
import com.estudia.app.importers.WordBridge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.estudia.app.data.DocType
import com.estudia.app.data.DocumentEntity
import com.estudia.app.data.FolderEntity
import com.estudia.app.ui.components.ConfirmDialog
import com.estudia.app.ui.components.MoveToFolderDialog
import com.estudia.app.ui.components.NameDialog
import com.estudia.app.ui.components.descendantsOf
import com.estudia.app.R
import com.estudia.app.ui.pinDocumentShortcut
import com.estudia.app.ui.theme.appBarColors
import java.text.DateFormat
import java.util.Date

private val MIME_TYPES = arrayOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text/plain",
    "text/markdown",
    "text/x-markdown",
    "application/octet-stream", // algunos gestores reportan .md así
)

/** Diálogos de la pantalla; solo uno abierto a la vez. */
private sealed interface HomeDialog {
    data object NewNote : HomeDialog
    data object NewDocument : HomeDialog
    data object NewFolder : HomeDialog
    data class RenameFolder(val folder: FolderEntity) : HomeDialog
    data class MoveFolder(val folder: FolderEntity) : HomeDialog
    data class DeleteFolder(val folder: FolderEntity) : HomeDialog
    data class RenameDoc(val doc: DocumentEntity) : HomeDialog
    data class MoveDoc(val doc: DocumentEntity) : HomeDialog
    data class DeleteDoc(val doc: DocumentEntity) : HomeDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenDocument: (Long) -> Unit, onOpenSettings: () -> Unit, vm: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val folderId by vm.currentFolderId.collectAsState()
    val folders by vm.folders.collectAsState()
    val documents by vm.documents.collectAsState()
    val pinned by vm.pinned.collectAsState()
    val path by vm.path.collectAsState()
    val allFolders by vm.allFolders.collectAsState()
    val query by vm.query.collectAsState()
    val busy by vm.busy.collectAsState()

    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    var searching by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.import(uris)
    }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { vm.openDocument.collect(onOpenDocument) }

    BackHandler(enabled = searching || folderId != null) {
        if (searching) {
            searching = false
            vm.query.value = ""
        } else vm.goUp()
    }

    val docActions = DocActions(
        open = { onOpenDocument(it.id) },
        togglePin = vm::togglePin,
        rename = { dialog = HomeDialog.RenameDoc(it) },
        move = { dialog = HomeDialog.MoveDoc(it) },
        delete = { dialog = HomeDialog.DeleteDoc(it) },
        shortcut = { doc ->
            if (!pinDocumentShortcut(context, doc)) {
                Toast.makeText(context, "Tu launcher no permite accesos directos", Toast.LENGTH_SHORT).show()
            }
        },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = { vm.query.value = it },
                            placeholder = { Text("Buscar documentos") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(path.lastOrNull()?.name ?: stringResource(R.string.app_name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    if (folderId != null && !searching) {
                        IconButton(onClick = { vm.goUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Subir")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) vm.query.value = ""
                    }) {
                        Icon(if (searching) Icons.Default.Close else Icons.Default.Search, contentDescription = "Buscar")
                    }
                    if (!searching) {
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Ajustes") }
                    }
                },
                colors = appBarColors(),
            )
        },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { addMenu = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Agregar") },
                )
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Importar archivo") },
                        leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                        onClick = { addMenu = false; picker.launch(MIME_TYPES) },
                    )
                    if (WordBridge.available) DropdownMenuItem(
                        text = { Text("Nuevo documento") },
                        leadingIcon = { Icon(Icons.Default.Description, null) },
                        onClick = { addMenu = false; dialog = HomeDialog.NewDocument },
                    )
                    DropdownMenuItem(
                        text = { Text("Nueva nota") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null) },
                        onClick = { addMenu = false; dialog = HomeDialog.NewNote },
                    )
                    DropdownMenuItem(
                        text = { Text("Nueva carpeta") },
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                        onClick = { addMenu = false; dialog = HomeDialog.NewFolder },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (path.isNotEmpty() && !searching) Breadcrumbs(path, onClick = vm::openFolder)

            LazyColumn(
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (folderId == null && !searching && pinned.isNotEmpty()) {
                    item { SectionTitle("Fijados") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(pinned, key = { it.id }) { doc ->
                                PinnedCard(doc, vm.folderName(doc.folderId), docActions)
                            }
                        }
                    }
                }

                if (!searching && folders.isNotEmpty()) {
                    item { SectionTitle("Carpetas") }
                    items(folders, key = { "f${it.id}" }) { folder ->
                        FolderItem(
                            folder = folder,
                            onOpen = { vm.openFolder(folder.id) },
                            onRename = { dialog = HomeDialog.RenameFolder(folder) },
                            onMove = { dialog = HomeDialog.MoveFolder(folder) },
                            onDelete = { dialog = HomeDialog.DeleteFolder(folder) },
                        )
                    }
                }

                item { SectionTitle(if (searching) "Resultados" else "Documentos") }
                if (documents.isEmpty()) {
                    item {
                        Text(
                            if (searching) "Sin resultados" else "Toca \"Agregar\" para importar un PDF, Word, TXT o Markdown, o crear un documento o una nota.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(documents, key = { "d${it.id}" }) { doc ->
                    DocumentItem(doc, docActions)
                }
            }
        }
    }

    when (val d = dialog) {
        null -> {}
        HomeDialog.NewNote -> NameDialog("Nueva nota", confirmLabel = "Crear", onDismiss = { dialog = null }) {
            dialog = null; vm.createNote(it)
        }
        HomeDialog.NewDocument -> NameDialog("Nuevo documento", confirmLabel = "Crear", onDismiss = { dialog = null }) {
            dialog = null; vm.createDocument(it)
        }
        HomeDialog.NewFolder -> NameDialog("Nueva carpeta", confirmLabel = "Crear", onDismiss = { dialog = null }) {
            dialog = null; vm.createFolder(it)
        }
        is HomeDialog.RenameFolder -> NameDialog("Renombrar carpeta", d.folder.name, onDismiss = { dialog = null }) {
            dialog = null; vm.renameFolder(d.folder, it)
        }
        is HomeDialog.MoveFolder -> MoveToFolderDialog(
            folders = allFolders,
            excluded = descendantsOf(d.folder.id, allFolders),
            onDismiss = { dialog = null },
        ) { dialog = null; vm.moveFolder(d.folder, it) }
        is HomeDialog.DeleteFolder -> ConfirmDialog(
            title = "Eliminar carpeta",
            message = "Se eliminará \"${d.folder.name}\". Su contenido pasará a la carpeta superior.",
            confirmLabel = "Eliminar",
            onDismiss = { dialog = null },
        ) { dialog = null; vm.deleteFolder(d.folder) }
        is HomeDialog.RenameDoc -> NameDialog("Renombrar", d.doc.title, onDismiss = { dialog = null }) {
            dialog = null; vm.renameDocument(d.doc, it)
        }
        is HomeDialog.MoveDoc -> MoveToFolderDialog(folders = allFolders, onDismiss = { dialog = null }) {
            dialog = null; vm.moveDocument(d.doc, it)
        }
        is HomeDialog.DeleteDoc -> ConfirmDialog(
            title = "Eliminar documento",
            message = "Se eliminará \"${d.doc.title}\" y sus anotaciones. Esta acción no se puede deshacer.",
            confirmLabel = "Eliminar",
            onDismiss = { dialog = null },
        ) { dialog = null; vm.deleteDocument(d.doc) }
    }
}

private class DocActions(
    val open: (DocumentEntity) -> Unit,
    val togglePin: (DocumentEntity) -> Unit,
    val rename: (DocumentEntity) -> Unit,
    val move: (DocumentEntity) -> Unit,
    val delete: (DocumentEntity) -> Unit,
    val shortcut: (DocumentEntity) -> Unit,
)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun Breadcrumbs(path: List<FolderEntity>, onClick: (Long?) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onClick(null) }) { Text("Inicio") }
        path.forEachIndexed { i, folder ->
            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp))
            TextButton(onClick = { onClick(folder.id) }, enabled = i != path.lastIndex) { Text(folder.name) }
        }
    }
}

fun DocType.icon(): ImageVector = when (this) {
    DocType.PDF -> Icons.Default.PictureAsPdf
    DocType.DOC, DocType.DOCX -> Icons.Default.Description
    DocType.TXT -> Icons.Default.TextSnippet
    DocType.MD -> Icons.Default.Article
    DocType.NOTE -> Icons.Default.EditNote
}

@Composable
private fun DocMenu(doc: DocumentEntity, actions: DocActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Opciones") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (doc.pinned) "Quitar de fijados" else "Fijar") },
                leadingIcon = { Icon(if (doc.pinned) Icons.Outlined.PushPin else Icons.Default.PushPin, null) },
                onClick = { open = false; actions.togglePin(doc) },
            )
            DropdownMenuItem(
                text = { Text("Acceso directo en inicio") },
                leadingIcon = { Icon(Icons.Default.AddToHomeScreen, null) },
                onClick = { open = false; actions.shortcut(doc) },
            )
            DropdownMenuItem(
                text = { Text("Mover a carpeta") },
                leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) },
                onClick = { open = false; actions.move(doc) },
            )
            DropdownMenuItem(
                text = { Text("Renombrar") },
                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                onClick = { open = false; actions.rename(doc) },
            )
            DropdownMenuItem(
                text = { Text("Eliminar") },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                onClick = { open = false; actions.delete(doc) },
            )
        }
    }
}

@Composable
private fun DocumentItem(doc: DocumentEntity, actions: DocActions) {
    ListItem(
        headlineContent = { Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text("${doc.type.label} · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(doc.updatedAt))}")
        },
        leadingContent = { Icon(doc.type.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (doc.pinned) {
                    Icon(Icons.Default.PushPin, "Fijado", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                }
                DocMenu(doc, actions)
            }
        },
        modifier = Modifier.clickable { actions.open(doc) },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedCard(doc: DocumentEntity, folderName: String?, actions: DocActions) {
    Card(
        modifier = Modifier
            .width(150.dp)
            .height(120.dp)
            .combinedClickable(onClick = { actions.open(doc) }, onLongClick = { actions.togglePin(doc) }),
    ) {
        Column(Modifier.padding(12.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(doc.type.icon(), null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.PushPin, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                doc.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Text(
                folderName ?: doc.type.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FolderItem(
    folder: FolderEntity,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.secondary) },
        trailingContent = {
            Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "Opciones") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(text = { Text("Renombrar") }, onClick = { open = false; onRename() })
                    DropdownMenuItem(text = { Text("Mover") }, onClick = { open = false; onMove() })
                    DropdownMenuItem(text = { Text("Eliminar") }, onClick = { open = false; onDelete() })
                }
            }
        },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}
