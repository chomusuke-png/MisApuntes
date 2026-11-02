package com.estudia.app.ui.notes

import android.app.Application
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.estudia.app.EstudiaApp
import com.estudia.app.data.ColorKind
import com.estudia.app.data.EditorContent
import com.estudia.app.data.StickyNoteEntity
import com.estudia.app.ui.components.ConfirmDialog
import com.estudia.app.ui.components.HIGHLIGHT_PRESETS
import com.estudia.app.ui.editor.EditorBridge
import com.estudia.app.ui.editor.EditorState
import com.estudia.app.ui.editor.FormatColorPicker
import com.estudia.app.ui.editor.FormattingToolbar
import com.estudia.app.ui.editor.WebEditor
import com.estudia.app.ui.editor.createEditorWebView
import com.estudia.app.ui.theme.appBarColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

class NoteEditorViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {
    private val repo = (app as EstudiaApp).repository
    val noteId: Long = checkNotNull(savedState["id"])
    private val json = Json { ignoreUnknownKeys = true }

    val note: StateFlow<StickyNoteEntity?> = repo.stickyNotes.observe(noteId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val content = MutableStateFlow<EditorContent?>(null)
    val state = MutableStateFlow(EditorState())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    /** Nombre de archivo de una imagen lista para insertar en el editor. */
    val imageReady = MutableSharedFlow<String>(extraBufferCapacity = 2)

    val recentHighlights = repo.recentColors.recent(ColorKind.HIGHLIGHT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentTextColors = repo.recentColors.recent(ColorKind.TEXT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentNoteColors = repo.recentColors.recent(ColorKind.NOTE)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var titleJob: Job? = null
    private var deleted = false

    init {
        viewModelScope.launch { content.value = repo.loadNoteBody(noteId) }
    }

    fun save(html: String) {
        if (deleted) return
        content.value?.let { content.value = EditorContent(html, it.baseDir) }
        repo.appScope.launch { repo.saveNoteBody(noteId, html) }
    }

    fun onState(raw: String) {
        runCatching { json.decodeFromString(EditorState.serializer(), raw) }.onSuccess { state.value = it }
    }

    fun setTitle(title: String) {
        titleJob?.cancel()
        titleJob = repo.appScope.launch {
            delay(400)
            repo.stickyNotes.rename(noteId, title.trim())
        }
    }

    fun setLook(icon: String, color: Int) = viewModelScope.launch {
        repo.stickyNotes.setLook(noteId, icon, color)
        repo.rememberColor(ColorKind.NOTE, color)
    }

    fun rememberColor(kind: ColorKind, color: Int) = repo.rememberColor(kind, color)

    fun newCameraFile(): File = repo.newCameraFile()

    fun addImage(uri: Uri, deleteAfter: File? = null) = viewModelScope.launch {
        runCatching { repo.importNoteImage(noteId, uri) }
            .onSuccess { imageReady.emit(it) }
            .onFailure { messages.emit(it.message ?: "No se pudo agregar la imagen") }
        deleteAfter?.delete()
    }

    fun delete(then: () -> Unit) = viewModelScope.launch {
        titleJob?.cancel()
        deleted = true
        repo.deleteStickyNote(noteId)
        then()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(darkTheme: Boolean, onBack: () -> Unit, vm: NoteEditorViewModel = viewModel()) {
    val context = LocalContext.current
    val note by vm.note.collectAsState()
    val content by vm.content.collectAsState()
    val format by vm.state.collectAsState()
    val recentHighlights by vm.recentHighlights.collectAsState()
    val recentTextColors by vm.recentTextColors.collectAsState()
    val recentNoteColors by vm.recentNoteColors.collectAsState()
    val editor = remember { WebEditor() }
    val snackbar = remember { SnackbarHostState() }

    var title by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(note?.id) { if (title == null) note?.let { title = it.title } }
    val titleFocus = remember { FocusRequester() }

    var picker by remember { mutableStateOf<ColorKind?>(null) }
    var lookDialog by remember { mutableStateOf(false) }
    var imageDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.addImage(it) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile?.let(::File)
        if (ok && f != null) vm.addImage(Uri.fromFile(f), deleteAfter = f) else f?.delete()
        cameraFile = null
    }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { vm.imageReady.collect { editor.call("editor.insertImage", it) } }
    LaunchedEffect(darkTheme) { editor.call("editor.setDark", darkTheme) }
    // Nota recién creada: el cursor va directo al título
    LaunchedEffect(title != null) {
        if (title?.isEmpty() == true && note?.plainText.isNullOrBlank()) runCatching { titleFocus.requestFocus() }
    }

    val quickHighlight = recentHighlights.firstOrNull() ?: HIGHLIGHT_PRESETS.first()
    fun highlight(color: Int?) {
        editor.call("editor.highlight", color?.let { "#%06X".format(it and 0xFFFFFF) })
        color?.let { vm.rememberColor(ColorKind.HIGHLIGHT, it) }
    }
    fun exit() = editor.flush(onBack)
    BackHandler { exit() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) editor.flush() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            editor.flush()
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            // Herramientas justo debajo de la barra superior
            Column {
            TopAppBar(
                title = { Text("Nota") },
                navigationIcon = { IconButton(onClick = ::exit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = {
                    IconButton(onClick = { editor.call("editor.undo") }, enabled = format.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "Deshacer")
                    }
                    IconButton(onClick = { editor.call("editor.redo") }, enabled = format.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, "Rehacer")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más opciones") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Cambiar ícono y color") },
                                leadingIcon = { Icon(Icons.Default.Palette, null) },
                                onClick = { menu = false; lookDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar nota") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
                colors = appBarColors(),
            )
            FormattingToolbar(
                format = format,
                quickHighlight = quickHighlight,
                recentHighlights = recentHighlights,
                editor = editor,
                onHighlight = ::highlight,
                onPick = { picker = it },
                onInsertImage = { imageDialog = true },
            )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                note?.let { n -> NoteBadge(n.icon, n.color, 36.dp, Modifier.clickable { lookDialog = true }) }
                Spacer(Modifier.width(4.dp))
                TextField(
                    value = title.orEmpty(),
                    onValueChange = { title = it; vm.setTitle(it) },
                    placeholder = { Text("Título") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f).focusRequester(titleFocus),
                )
            }
            HorizontalDivider()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val initial = content
                if (initial == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            createEditorWebView(
                                ctx,
                                html = initial.html,
                                baseDir = "file://${initial.baseDir.absolutePath}/",
                                readOnly = false,
                                paged = false,
                                dark = darkTheme,
                                bridge = EditorBridge(onSave = vm::save, onState = vm::onState),
                            ).also { editor.view = it }
                        },
                        onRelease = { editor.view = null },
                    )
                }
            }
        }
    }

    picker?.let { kind ->
        FormatColorPicker(
            kind = kind,
            format = format,
            quickHighlight = quickHighlight,
            recentHighlights = recentHighlights,
            recentTextColors = recentTextColors,
            editor = editor,
            onHighlight = ::highlight,
            onTextColorUsed = { vm.rememberColor(ColorKind.TEXT, it) },
            onDismiss = { picker = null },
        )
    }

    if (lookDialog) note?.let { n ->
        NoteLookDialog(
            title = "Ícono y color",
            confirmLabel = "Guardar",
            initialIcon = n.icon,
            initialColor = n.color,
            recentColors = recentNoteColors,
            onDismiss = { lookDialog = false },
        ) { icon, color ->
            lookDialog = false
            vm.setLook(icon, color)
        }
    }

    if (imageDialog) {
        AlertDialog(
            onDismissRequest = { imageDialog = false },
            title = { Text("Insertar imagen") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Galería") },
                        leadingContent = { Icon(Icons.Default.PhotoLibrary, null) },
                        modifier = Modifier.clickable {
                            imageDialog = false
                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                    ListItem(
                        headlineContent = { Text("Cámara") },
                        leadingContent = { Icon(Icons.Default.PhotoCamera, null) },
                        modifier = Modifier.clickable {
                            imageDialog = false
                            val f = vm.newCameraFile()
                            cameraFile = f.absolutePath
                            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", f))
                        },
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { imageDialog = false }) { Text("Cancelar") } },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Eliminar nota",
            message = "Se eliminará la nota y su contenido. No se puede deshacer.",
            confirmLabel = "Eliminar",
            onDismiss = { confirmDelete = false },
        ) {
            confirmDelete = false
            vm.delete(onBack)
        }
    }
}
