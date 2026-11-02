package com.estudia.app.ui.editor

import android.app.Application
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

/** Punto donde se tocó en el editor web: párrafo (data-anchor) y posición relativa. */
@Serializable
data class WebAnchor(val x: Float, val y: Float, val block: String? = null, val docY: Float = 0f)

/** Nota tal como la dibuja el editor web. */
@Serializable
data class WebNote(val id: String, val img: String, val color: String, val x: Float, val y: Float, val block: String?, val docY: Float)

class EditorViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {
    private val repo = (app as EstudiaApp).repository
    val docId: Long = checkNotNull(savedState["id"])
    private val json = Json { ignoreUnknownKeys = true }

    val document: StateFlow<DocumentEntity?> = repo.documents.observe(docId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _content = MutableStateFlow<EditorContent?>(null)
    val content: StateFlow<EditorContent?> = _content

    val error = MutableStateFlow<String?>(null)
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

    fun createStickyNote(anchorJson: String, icon: String, color: Int) = viewModelScope.launch {
        val a = parseAnchor(anchorJson) ?: return@launch
        val id = repo.createStickyNote(
            StickyNoteEntity(docId = docId, icon = icon, color = color, x = a.x, y = a.y, block = a.block, docY = a.docY)
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
