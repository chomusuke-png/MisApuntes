package com.estudia.app.ui.editor

import android.app.Application
import android.net.Uri
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.estudia.app.EstudiaApp
import com.estudia.app.data.ColorKind
import com.estudia.app.data.DocumentEntity
import com.estudia.app.data.EditorContent
import com.estudia.app.data.StickyNoteEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Estado de formato que envía el editor web en cada cambio de selección. */
@Serializable
data class EditorState(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val sup: Boolean = false,
    val sub: Boolean = false,
    /** Brocha armada: hay un formato copiado esperando aplicarse. */
    val painter: Boolean = false,
    /** Imagen seleccionada (null si no hay). */
    val image: ImageState? = null,
    /** Tabla donde está el cursor (null fuera de una tabla). */
    val table: TableState? = null,
    /** Hoja: tamaño y márgenes en puntos. */
    val page: PageState = PageState(),
    val block: String = "p",
    val font: String = "",
    val size: Float = 12f,
    val color: String = "",
    val back: String = "",
    val align: String = "left",
    val ul: Boolean = false,
    val ol: Boolean = false,
    val hasSelection: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

/** Tamaño de la hoja y márgenes, en puntos (Carta por defecto). */
@Serializable
data class PageState(
    val w: Float = 612f,
    val h: Float = 792f,
    val mt: Float = 72f,
    val mr: Float = 72f,
    val mb: Float = 72f,
    val ml: Float = 72f,
)

/** Tabla del cursor: tamaño, celdas tocadas y formato de la celda actual. */
@Serializable
data class TableState(
    val rows: Int = 1,
    val cols: Int = 1,
    /** Celdas que toca la selección (más de una: se pueden combinar). */
    val cells: Int = 1,
    val merged: Boolean = false,
    val bg: String = "",
    val valign: String = "top",
    val borders: Boolean = true,
)

/** Imagen seleccionada: ancho en % de la hoja, alineación de su párrafo y tamaño en puntos. */
@Serializable
data class ImageState(
    val pct: Int = 100,
    val align: String = "left",
    val w: Float = 0f,
    val h: Float = 0f,
    /** Ancho útil de la hoja. */
    val maxW: Float = 450f,
)

/** Punto donde se tocó en el editor web: párrafo (data-anchor) y posición relativa. */
@Serializable
data class WebAnchor(val x: Float, val y: Float, val block: String? = null, val docY: Float = 0f)

/** Nota tal como la dibuja el editor web. */
@Serializable
data class WebNote(val id: String, val img: String, val color: String, val x: Float, val y: Float, val block: String?, val docY: Float, val size: Int = 30)

class EditorViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {
    private val repo = (app as EstudiaApp).repository
    val docId: Long = checkNotNull(savedState["id"])
    private val json = Json { ignoreUnknownKeys = true }

    val document: StateFlow<DocumentEntity?> = repo.documents.observe(docId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _content = MutableStateFlow<EditorContent?>(null)
    val content: StateFlow<EditorContent?> = _content

    val error = MutableStateFlow<String?>(null)
    /** Documento en blanco recién creado: se abre listo para escribir. */
    val isNew: Boolean = repo.consumeNewDocument(docId)
    val state = MutableStateFlow(EditorState())

    val recentHighlights = repo.recentColors.recent(ColorKind.HIGHLIGHT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentTextColors = repo.recentColors.recent(ColorKind.TEXT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            val doc = repo.documents.get(docId) ?: run { error.value = "El documento ya no existe"; return@launch }
            repo.documents.touchOpened(docId)
            runCatching { repo.loadContent(doc) }
                .onSuccess { _content.value = it }
                .onFailure { error.value = it.message ?: "No se pudo abrir el documento" }
        }
    }

    /** Llamado desde JavaScript (hilo del WebView). El guardado sigue aunque se cierre la pantalla. */
    fun save(html: String) {
        _content.value?.let { _content.value = EditorContent(html, it.baseDir) }
        repo.appScope.launch { repo.saveHtml(docId, html) }
    }

    /** El último modelo llega antes de que termine de escribirse en disco: se exporta desde aquí. */
    @Volatile private var lastModel: String? = null

    fun saveModel(json: String) {
        lastModel = json
        repo.appScope.launch { repo.saveModel(docId, json) }
    }

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** .docx listo para abrir el menú de compartir. */
    val shareFile = MutableSharedFlow<File>(extraBufferCapacity = 1)
    val busy = MutableStateFlow(false)

    /** Nombre del archivo de imagen listo para insertar en el editor. */
    val imageReady = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Llamado desde JavaScript (sincrónico): gira la imagen y devuelve el nombre del archivo nuevo. */
    fun rotateImage(src: String, degrees: Int): String {
        val dir = _content.value?.baseDir ?: return ""
        return runCatching { repo.rotateImage(dir, src, degrees) }.getOrDefault("")
    }

    fun importImage(uri: Uri) = viewModelScope.launch {
        busy.value = true
        runCatching { repo.importDocumentImage(docId, uri) }
            .onSuccess { imageReady.emit(it) }
            .onFailure { messages.emit("No se pudo insertar la imagen: ${it.message}") }
        busy.value = false
    }

    fun exportWord(target: Uri) = viewModelScope.launch {
        val doc = document.value ?: return@launch
        busy.value = true
        runCatching { repo.exportDocx(doc, target, lastModel) }
            .onSuccess { messages.emit("Guardado como Word") }
            .onFailure { messages.emit("No se pudo guardar: ${it.message}") }
        busy.value = false
    }

    fun shareWord() = viewModelScope.launch {
        val doc = document.value ?: return@launch
        busy.value = true
        runCatching { repo.docxForSharing(doc, lastModel) }
            .onSuccess { shareFile.emit(it) }
            .onFailure { messages.emit("No se pudo preparar el archivo: ${it.message}") }
        busy.value = false
    }

    fun onState(raw: String) {
        runCatching { json.decodeFromString(EditorState.serializer(), raw) }.onSuccess { state.value = it }
    }

    fun rememberColor(kind: ColorKind, color: Int) = repo.rememberColor(kind, color)

    // ---------- Notas adhesivas ----------

    val notes = repo.stickyNotes.forDocument(docId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentNoteColors = repo.recentColors.recent(ColorKind.NOTE)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val stickyCreated = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    /** Desplazamiento del documento, para volver al mismo punto al regresar de una nota. */
    @Volatile var scrollY: Int = 0
        private set

    fun onScroll(y: Int) { scrollY = y }

    fun createStickyNote(anchorJson: String, icon: String, color: Int, size: Int) = viewModelScope.launch {
        val a = parseAnchor(anchorJson) ?: return@launch
        val id = repo.createStickyNote(
            StickyNoteEntity(docId = docId, icon = icon, color = color, x = a.x, y = a.y, block = a.block, docY = a.docY, size = size)
        )
        repo.rememberColor(ColorKind.NOTE, color)
        stickyCreated.emit(id)
    }

    fun moveStickyNote(id: Long, anchorJson: String) = viewModelScope.launch {
        val a = parseAnchor(anchorJson) ?: return@launch
        repo.stickyNotes.move(id, -1, a.x, a.y, a.block, a.docY)
    }

    fun deleteStickyNote(id: Long) = viewModelScope.launch { repo.deleteStickyNote(id) }

    private fun parseAnchor(raw: String) = runCatching { json.decodeFromString(WebAnchor.serializer(), raw) }.getOrNull()

    fun togglePin() = viewModelScope.launch {
        document.value?.let { repo.documents.setPinned(it.id, !it.pinned) }
    }

    fun rename(title: String) = viewModelScope.launch { repo.documents.rename(docId, title) }
}
