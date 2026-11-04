package com.estudia.app.ui.pdf

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.estudia.app.data.StickyNoteEntity
import com.estudia.app.ui.notes.NOTE_COLORS
import com.estudia.app.ui.notes.NOTE_ICONS
import com.estudia.app.ui.notes.NoteBadge
import com.estudia.app.ui.notes.NoteLookDialog
import com.estudia.app.ui.notes.NotePopup
import com.estudia.app.ui.notes.NotesIndexSheet
import com.estudia.app.ui.notes.PlacingBanner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.estudia.app.EstudiaApp
import com.estudia.app.data.ColorKind
import com.estudia.app.data.DocumentEntity
import com.estudia.app.data.PdfStroke
import com.estudia.app.ui.components.ColorPickerDialog
import com.estudia.app.ui.components.FastScrollbar
import com.estudia.app.ui.components.HIGHLIGHT_PRESETS
import com.estudia.app.ui.components.Swatch
import com.estudia.app.ui.theme.appBarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color as ComposeColor

private enum class Tool { FREE, STRAIGHT, ERASER }

/** Grosor del marcador como fracción del ancho de la página. */
private const val MIN_WIDTH = 0.004f
private const val MAX_WIDTH = 0.07f
private const val MIN_OPACITY = 0.1f

class PdfViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {
    private val repo = (app as EstudiaApp).repository
    private val docId: Long = checkNotNull(savedState["id"])

    val document: StateFlow<DocumentEntity?> = repo.documents.observe(docId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Proporción alto/ancho de cada página; null mientras se abre. */
    val pageRatios = MutableStateFlow<List<Float>?>(null)
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val noteCreated = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    val strokes = MutableStateFlow<List<PdfStroke>>(emptyList())
    private val undoStack = ArrayDeque<List<PdfStroke>>()
    private val redoStack = ArrayDeque<List<PdfStroke>>()
    val canUndo = MutableStateFlow(false)
    val canRedo = MutableStateFlow(false)
    private var saveJob: Job? = null

    val recentHighlights = repo.recentColors.recent(ColorKind.HIGHLIGHT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val notes = repo.stickyNotes.forDocument(docId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentNoteColors = repo.recentColors.recent(ColorKind.NOTE)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val stickyCreated = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    // PdfRenderer solo permite una página abierta a la vez
    private val lock = Mutex()
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    init {
        viewModelScope.launch {
            val doc = repo.documents.get(docId)
            val path = doc?.originalPath
            if (path == null) {
                error.value = "Archivo no encontrado"
                return@launch
            }
            repo.documents.touchOpened(docId)
            strokes.value = repo.loadStrokes(docId)
            runCatching {
                withContext(Dispatchers.IO) {
                    val pfd = ParcelFileDescriptor.open(repo.file(path), ParcelFileDescriptor.MODE_READ_ONLY)
                    val r = PdfRenderer(pfd)
                    fd = pfd
                    renderer = r
                    (0 until r.pageCount).map { i -> r.openPage(i).use { it.height.toFloat() / it.width } }
                }
            }.onSuccess { pageRatios.value = it }
                .onFailure { error.value = "No se pudo abrir el PDF (¿está protegido?)" }
        }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap? {
        val key = "$index@$widthPx"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            lock.withLock {
                val r = renderer ?: return@withLock null
                r.openPage(index).use { page ->
                    val height = (widthPx.toFloat() * page.height / page.width).roundToInt()
                    val bmp = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(key, bmp)
                    bmp
                }
            }
        }
    }

    // ---------- Resaltados ----------

    private fun change(newList: List<PdfStroke>) {
        undoStack.addLast(strokes.value)
        if (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
        apply(newList)
    }

    private fun apply(list: List<PdfStroke>) {
        strokes.value = list
        canUndo.value = undoStack.isNotEmpty()
        canRedo.value = redoStack.isNotEmpty()
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            repo.appScope.launch { repo.saveStrokes(docId, list) }
        }
    }

    fun addStroke(stroke: PdfStroke) {
        change(strokes.value + stroke)
        repo.rememberColor(ColorKind.HIGHLIGHT, stroke.color)
    }

    fun removeStroke(stroke: PdfStroke) {
        if (stroke in strokes.value) change(strokes.value - stroke)
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(strokes.value)
        apply(previous)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(strokes.value)
        apply(next)
    }

    fun exportTo(uri: Uri) = viewModelScope.launch {
        val doc = document.value ?: return@launch
        busy.value = true
        runCatching { repo.exportPdf(doc, strokes.value, uri) }
            .onSuccess { messages.emit("PDF guardado con tus resaltados") }
            .onFailure { messages.emit("No se pudo exportar: ${it.message}") }
        busy.value = false
    }

    // ---------- Notas adhesivas ----------

    fun createStickyNote(page: Int, x: Float, y: Float, icon: String, color: Int, size: Int) = viewModelScope.launch {
        val id = repo.createStickyNote(
            StickyNoteEntity(docId = docId, icon = icon, color = color, page = page, x = x, y = y, docY = y, size = size)
        )
        repo.rememberColor(ColorKind.NOTE, color)
        stickyCreated.emit(id)
    }

    fun moveStickyNote(note: StickyNoteEntity, x: Float, y: Float) = viewModelScope.launch {
        repo.stickyNotes.move(note.id, note.page, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), null, y.coerceIn(0f, 1f))
    }

    fun deleteStickyNote(id: Long) = viewModelScope.launch { repo.deleteStickyNote(id) }

    fun togglePin() = viewModelScope.launch {
        document.value?.let { repo.documents.setPinned(it.id, !it.pinned) }
    }

    fun createEditableNote() = viewModelScope.launch {
        val doc = document.value ?: return@launch
        busy.value = true
        runCatching { repo.createNoteFromPdf(doc) }
            .onSuccess { noteCreated.emit(it) }
            .onFailure { messages.emit(it.message ?: "No se pudo extraer el texto") }
        busy.value = false
    }

    override fun onCleared() {
        saveJob?.cancel()
        val pending = strokes.value
        repo.appScope.launch { repo.saveStrokes(docId, pending) }
        // Se cierra bajo el mismo candado para no cortar un renderizado en curso
        val r = renderer
        val f = fd
        renderer = null
        fd = null
        repo.appScope.launch { lock.withLock { r?.close(); f?.close() } }
        cache.evictAll()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    darkTheme: Boolean,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenStickyNote: (Long) -> Unit,
    vm: PdfViewModel = viewModel(),
) {
    val doc by vm.document.collectAsState()
    val ratios by vm.pageRatios.collectAsState()
    val error by vm.error.collectAsState()
    val busy by vm.busy.collectAsState()
    val strokes by vm.strokes.collectAsState()
    val canUndo by vm.canUndo.collectAsState()
    val canRedo by vm.canRedo.collectAsState()
    val recent by vm.recentHighlights.collectAsState()
    val notes by vm.notes.collectAsState()
    val recentNoteColors by vm.recentNoteColors.collectAsState()

    // Notas: colocar una nueva, ver la tocada, índice y salto con aviso visual
    var placing by rememberSaveable { mutableStateOf(false) }
    var pendingPlace by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }
    var openNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var indexOpen by remember { mutableStateOf(false) }
    var jumpTo by remember { mutableStateOf<StickyNoteEntity?>(null) }
    var pulse by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    LaunchedEffect(Unit) { vm.stickyCreated.collect(onOpenStickyNote) }

    var scale by remember { mutableFloatStateOf(1f) }
    val listState = rememberLazyListState()
    val hScroll = rememberScrollState()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }

    var highlighting by rememberSaveable { mutableStateOf(false) }
    var tool by rememberSaveable { mutableStateOf(Tool.FREE) }
    val settings = (LocalContext.current.applicationContext as EstudiaApp).settings
    var color by rememberSaveable { mutableIntStateOf(HIGHLIGHT_PRESETS.first()) }
    var pickerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(recent.firstOrNull()) { recent.firstOrNull()?.let { if (color == HIGHLIGHT_PRESETS.first()) color = it } }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let(vm::exportTo)
    }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { vm.noteCreated.collect(onOpenNote) }

    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    val strokesByPage = remember(strokes) { strokes.groupBy { it.page } }
    val notesByPage = remember(notes) { notes.groupBy { it.page } }

    Scaffold(
        topBar = {
            // Herramientas justo debajo de la barra superior
            Column {
            TopAppBar(
                title = { Text(doc?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    if (highlighting) {
                        IconButton(onClick = vm::undo, enabled = canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Deshacer") }
                        IconButton(onClick = vm::redo, enabled = canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Rehacer") }
                        IconButton(onClick = { highlighting = false }) { Icon(Icons.Default.Check, "Terminar de resaltar") }
                    } else {
                        NotesIndexButton(notes.size) { indexOpen = true }
                        IconButton(onClick = { placing = false; highlighting = true }) { Icon(Icons.Default.BorderColor, "Resaltar") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más opciones") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (doc?.pinned == true) "Quitar de fijados" else "Fijar") },
                                    leadingIcon = {
                                        Icon(if (doc?.pinned == true) Icons.Outlined.PushPin else Icons.Filled.PushPin, null)
                                    },
                                    onClick = { menu = false; vm.togglePin() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Guardar copia con resaltados") },
                                    leadingIcon = { Icon(Icons.Default.SaveAlt, null) },
                                    enabled = strokes.isNotEmpty(),
                                    onClick = { menu = false; exporter.launch("${doc?.title ?: "documento"} (resaltado).pdf") },
                                )
                                DropdownMenuItem(
                                    text = { Text("Convertir en nota editable") },
                                    leadingIcon = { Icon(Icons.Default.EditNote, null) },
                                    onClick = { menu = false; vm.createEditableNote() },
                                )
                            }
                        }
                    }
                },
                colors = appBarColors(),
            )
            if (highlighting) {
                HighlighterBar(
                    tool = tool,
                    onTool = { tool = it },
                    width = settings.highlighterWidth,
                    onWidth = settings::updateHighlighterWidth,
                    opacity = settings.highlighterOpacity,
                    onOpacity = settings::updateHighlighterOpacity,
                    color = color,
                    colors = (recent + HIGHLIGHT_PRESETS).distinct().take(10),
                    onColor = { color = it; if (tool == Tool.ERASER) tool = Tool.FREE },
                    onPick = { pickerOpen = true },
                )
            }
            }
        },
        floatingActionButton = {
            if (!highlighting && !placing && ratios != null) AddNoteFab { placing = true }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    // Dos dedos: zoom y desplazamiento (también mientras se resalta con un dedo)
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                                val pan = event.calculatePan()
                                listState.dispatchRawDelta(-pan.y)
                                hScroll.dispatchRawDelta(-pan.x)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            val pages = ratios
            when {
                error != null -> Text(error!!, Modifier.align(Alignment.Center).padding(24.dp))
                pages == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> {
                    val pageWidth = maxWidth * scale
                    val density = LocalDensity.current
                    // Calidad de render escalonada para no re-renderizar en cada paso del pellizco
                    val renderWidthPx = with(density) {
                        val bucket = (scale * 2).roundToInt().coerceIn(2, 6) / 2f
                        (maxWidth.toPx() * bucket).roundToInt().coerceAtMost(3000)
                    }
                    Box(Modifier.fillMaxSize().horizontalScroll(hScroll, enabled = !highlighting)) {
                        LazyColumn(
                            state = listState,
                            userScrollEnabled = !highlighting,
                            modifier = Modifier.width(pageWidth).fillMaxHeight(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(pages.size) { index ->
                                PdfPage(
                                    vm = vm,
                                    index = index,
                                    ratio = pages[index],
                                    widthPx = renderWidthPx,
                                    strokes = strokesByPage[index].orEmpty(),
                                    tool = if (highlighting) tool else null,
                                    color = color,
                                    strokeWidth = settings.highlighterWidth,
                                    strokeAlpha = settings.highlighterOpacity,
                                    notes = notesByPage[index].orEmpty(),
                                    notesInteractive = !highlighting && !placing,
                                    placing = placing,
                                    pulse = pulse,
                                    onPlace = { x, y ->
                                        placing = false
                                        pendingPlace = Triple(index, x, y)
                                    },
                                    onNoteTap = { openNoteId = it.id },
                                    onNoteMoved = vm::moveStickyNote,
                                )
                            }
                        }
                    }

                    // Saltar a una nota del índice: queda a un tercio de la altura de la pantalla
                    val viewportPx = with(density) { maxHeight.toPx() }
                    val viewportWidthPx = with(density) { maxWidth.toPx() }
                    val pageWidthPx = with(density) { (pageWidth - 16.dp).toPx() }
                    LaunchedEffect(jumpTo) {
                        val n = jumpTo ?: return@LaunchedEffect
                        jumpTo = null
                        val page = n.page.coerceIn(0, pages.lastIndex)
                        val pageHeightPx = pageWidthPx * pages[page]
                        listState.animateScrollToItem(page, (n.y * pageHeightPx - viewportPx / 3).roundToInt().coerceAtLeast(0))
                        if (hScroll.maxValue > 0) {
                            hScroll.animateScrollTo((n.x * pageWidthPx - viewportWidthPx / 2).roundToInt().coerceIn(0, hScroll.maxValue))
                        }
                        pulse = n.id to System.nanoTime()
                    }

                    if (placing) {
                        PlacingBanner(onCancel = { placing = false }, modifier = Modifier.align(Alignment.TopCenter))
                    }
                    if (pages.size > 1) {
                        // Posición como "página + fracción de página", sobre las páginas que pueden quedar arriba
                        val scrollScope = rememberCoroutineScope()
                        val fraction by remember(pages.size) {
                            derivedStateOf {
                                val info = listState.layoutInfo
                                val first = info.visibleItemsInfo.firstOrNull() ?: return@derivedStateOf 0f
                                val itemSize = first.size.coerceAtLeast(1)
                                val viewportItems = (info.viewportEndOffset - info.viewportStartOffset).toFloat() / itemSize
                                val maxStart = (pages.size - viewportItems).coerceAtLeast(1f)
                                val position = first.index + (-first.offset).toFloat() / itemSize
                                (position / maxStart).coerceIn(0f, 1f)
                            }
                        }
                        fun targetFor(f: Float): Pair<Int, Int> {
                            val info = listState.layoutInfo
                            val itemSize = info.visibleItemsInfo.firstOrNull()?.size?.coerceAtLeast(1) ?: 1
                            val viewportItems = (info.viewportEndOffset - info.viewportStartOffset).toFloat() / itemSize
                            val position = f * (pages.size - viewportItems).coerceAtLeast(0f)
                            val index = position.toInt().coerceIn(0, pages.lastIndex)
                            return index to ((position - index) * itemSize).roundToInt()
                        }
                        FastScrollbar(
                            fraction = fraction,
                            onScrollTo = { f ->
                                val (index, offset) = targetFor(f)
                                scrollScope.launch { listState.scrollToItem(index, offset) }
                            },
                            label = { f -> "Página ${targetFor(f).first + 1} / ${pages.size}" },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, bottom = if (highlighting) 8.dp else 88.dp),
                        )
                    }
                    if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        tonalElevation = 4.dp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    ) {
                        Text(
                            if (highlighting) "Usa dos dedos para moverte · $currentPage / ${pages.size}" else "$currentPage / ${pages.size}",
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }

    openNoteId?.let { id ->
        val note = notes.firstOrNull { it.id == id }
        if (note == null) {
            LaunchedEffect(id) { openNoteId = null }
        } else {
            NotePopup(
                note = note,
                darkTheme = darkTheme,
                onEdit = { openNoteId = null; onOpenStickyNote(note.id) },
                onDelete = { openNoteId = null; vm.deleteStickyNote(note.id) },
                onClose = { openNoteId = null },
            )
        }
    }

    pendingPlace?.let { (page, x, y) ->
        NoteLookDialog(
            title = "Nueva nota",
            confirmLabel = "Crear",
            initialIcon = NOTE_ICONS.first().first,
            initialColor = recentNoteColors.firstOrNull() ?: NOTE_COLORS.first(),
            recentColors = recentNoteColors,
            onDismiss = { pendingPlace = null },
        ) { icon, noteColor, size ->
            pendingPlace = null
            vm.createStickyNote(page, x, y, icon, noteColor, size)
        }
    }

    if (indexOpen) {
        NotesIndexSheet(
            notes = notes,
            isPdf = true,
            onJump = { indexOpen = false; jumpTo = it },
            onDismiss = { indexOpen = false },
        )
    }

    BackHandler(enabled = placing || highlighting) {
        if (placing) placing = false else highlighting = false
    }

    if (pickerOpen) {
        ColorPickerDialog(
            title = "Color del marcador",
            initial = color,
            recent = recent,
            presets = HIGHLIGHT_PRESETS,
            onDismiss = { pickerOpen = false },
            onPick = {
                pickerOpen = false
                color = it
                if (tool == Tool.ERASER) tool = Tool.FREE
            },
        )
    }
}

@Composable
private fun PdfPage(
    vm: PdfViewModel,
    index: Int,
    ratio: Float,
    widthPx: Int,
    strokes: List<PdfStroke>,
    tool: Tool?,
    color: Int,
    strokeWidth: Float,
    strokeAlpha: Float,
    notes: List<StickyNoteEntity>,
    notesInteractive: Boolean,
    placing: Boolean,
    pulse: Pair<Long, Long>?,
    onPlace: (Float, Float) -> Unit,
    onNoteTap: (StickyNoteEntity) -> Unit,
    onNoteMoved: (StickyNoteEntity, Float, Float) -> Unit,
) {
    val bitmap by produceState<Bitmap?>(null, index, widthPx) { value = vm.render(index, widthPx) }
    var current by remember { mutableStateOf<PdfStroke?>(null) }
    val latestStrokes by rememberUpdatedState(strokes)
    val eraseRadius = with(LocalDensity.current) { 14.dp.toPx() }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f / ratio)
            .background(ComposeColor.White)
            .onSizeChanged { pageSize = it },
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Página ${index + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val drawModifier = if (tool == null) Modifier else Modifier.pointerInput(tool, color, strokeWidth, strokeAlpha) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val w = size.width.toFloat()
                val h = size.height.toFloat()

                if (tool == Tool.ERASER) {
                    fun eraseAt(p: Offset) {
                        latestStrokes.firstOrNull { hits(it, p, w, h, eraseRadius) }?.let(vm::removeStroke)
                    }
                    eraseAt(down.position)
                    drag(down.id) { change -> eraseAt(change.position); change.consume() }
                    return@awaitEachGesture
                }

                val start = down.position
                val points = mutableListOf(start.x / w, start.y / h)
                val straight = tool == Tool.STRAIGHT
                current = PdfStroke(index, color, strokeWidth, points.toList(), straight, strokeAlpha)
                val completed = drag(down.id) { change ->
                    val p = change.position
                    if (straight) {
                        points.clear()
                        points += listOf(start.x / w, start.y / h, p.x / w, start.y / h)
                    } else {
                        points += listOf(p.x.coerceIn(0f, w) / w, p.y.coerceIn(0f, h) / h)
                    }
                    current = PdfStroke(index, color, strokeWidth, points.toList(), straight, strokeAlpha)
                    change.consume()
                }
                val stroke = current
                current = null
                if (completed && stroke != null && (!straight || stroke.points.size >= 4)) vm.addStroke(stroke)
            }
        }
        Canvas(Modifier.fillMaxSize().then(drawModifier)) {
            strokes.forEach { drawHighlight(it) }
            current?.let { drawHighlight(it) }
        }

        if (placing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { p -> onPlace(p.x / size.width, p.y / size.height) }
                    }
            )
        }
        if (pageSize != IntSize.Zero) {
            notes.forEach { note ->
                key(note.id) {
                    PageNoteMarker(note, pageSize, notesInteractive, pulse, onNoteTap, onNoteMoved)
                }
            }
        }
    }
}

/** Marcador de una nota sobre la página: tocar abre, mantener presionado y arrastrar mueve. */
@Composable
private fun PageNoteMarker(
    note: StickyNoteEntity,
    pageSize: IntSize,
    interactive: Boolean,
    pulse: Pair<Long, Long>?,
    onTap: (StickyNoteEntity) -> Unit,
    onMoved: (StickyNoteEntity, Float, Float) -> Unit,
) {
    val sizeDp = note.size.coerceIn(16, 72).dp
    val half = with(LocalDensity.current) { sizeDp.toPx() / 2 }
    var drag by remember(note.x, note.y) { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val emphasis = remember { Animatable(1f) }
    LaunchedEffect(pulse) {
        if (pulse?.first == note.id) repeat(3) {
            emphasis.animateTo(1.6f, tween(220))
            emphasis.animateTo(1f, tween(320))
        }
    }

    val cx = note.x * pageSize.width + drag.x
    val cy = note.y * pageSize.height + drag.y
    val gestures = if (!interactive) Modifier else Modifier
        .pointerInput(note.id) { detectTapGestures(onTap = { onTap(note) }) }
        .pointerInput(note.id, note.x, note.y, pageSize) {
            detectDragGesturesAfterLongPress(
                onDragStart = { dragging = true },
                onDrag = { change, delta -> change.consume(); drag += delta },
                onDragEnd = {
                    dragging = false
                    onMoved(
                        note,
                        (note.x * pageSize.width + drag.x) / pageSize.width,
                        (note.y * pageSize.height + drag.y) / pageSize.height,
                    )
                },
                onDragCancel = { dragging = false; drag = Offset.Zero },
            )
        }

    NoteBadge(
        icon = note.icon,
        color = note.color,
        size = sizeDp,
        modifier = Modifier
            .offset { IntOffset((cx - half).roundToInt(), (cy - half).roundToInt()) }
            .graphicsLayer {
                val s = emphasis.value * if (dragging) 1.25f else 1f
                scaleX = s
                scaleY = s
            }
            .then(gestures),
    )
}

/** Botón del índice de notas, con la cantidad. */
@Composable
internal fun NotesIndexButton(count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (count > 0) Badge { Text("$count") } }) {
            Icon(Icons.AutoMirrored.Filled.ListAlt, "Índice de notas")
        }
    }
}

@Composable
internal fun AddNoteFab(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Default.AddComment, null) },
        text = { Text("Nota") },
    )
}

/** Marcador: color opaco en modo multiplicar, así el texto negro sigue viéndose negro. */
private fun DrawScope.drawHighlight(stroke: PdfStroke) {
    val pts = stroke.points
    if (pts.size < 2) return
    val path = Path().apply {
        moveTo(pts[0] * size.width, pts[1] * size.height)
        if (pts.size == 2) lineTo(pts[0] * size.width + 0.1f, pts[1] * size.height)
        var i = 2
        while (i + 1 < pts.size) {
            lineTo(pts[i] * size.width, pts[i + 1] * size.height)
            i += 2
        }
    }
    drawPath(
        path = path,
        color = ComposeColor(stroke.color).copy(alpha = stroke.alpha.coerceIn(0f, 1f)),
        style = Stroke(
            width = stroke.width * size.width,
            cap = if (stroke.straight) StrokeCap.Square else StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
        blendMode = BlendMode.Multiply,
    )
}

/** ¿El punto toca el trazo? Distancia a cada segmento menor que medio grosor + margen. */
private fun hits(stroke: PdfStroke, p: Offset, w: Float, h: Float, margin: Float): Boolean {
    val limit = stroke.width * w / 2 + margin
    val pts = stroke.points
    if (pts.size == 2) return hypot(pts[0] * w - p.x, pts[1] * h - p.y) <= limit
    var i = 0
    while (i + 3 < pts.size) {
        val ax = pts[i] * w; val ay = pts[i + 1] * h
        val bx = pts[i + 2] * w; val by = pts[i + 3] * h
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((p.x - ax) * dx + (p.y - ay) * dy) / len2).coerceIn(0f, 1f)
        if (hypot(ax + t * dx - p.x, ay + t * dy - p.y) <= limit) return true
        i += 2
    }
    return false
}

@Composable
private fun HighlighterBar(
    tool: Tool,
    onTool: (Tool) -> Unit,
    width: Float,
    onWidth: (Float) -> Unit,
    opacity: Float,
    onOpacity: (Float) -> Unit,
    color: Int,
    colors: List<Int>,
    onColor: (Int) -> Unit,
    onPick: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 2.dp) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = tool == Tool.FREE,
                    onClick = { onTool(Tool.FREE) },
                    label = { Text("Libre") },
                    leadingIcon = { Icon(Icons.Default.Gesture, null, Modifier.size(18.dp)) },
                )
                FilterChip(
                    selected = tool == Tool.STRAIGHT,
                    onClick = { onTool(Tool.STRAIGHT) },
                    label = { Text("Recto") },
                    leadingIcon = { Icon(Icons.Default.HorizontalRule, null, Modifier.size(18.dp)) },
                )
                FilterChip(
                    selected = tool == Tool.ERASER,
                    onClick = { onTool(Tool.ERASER) },
                    label = { Text("Borrar") },
                    leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(18.dp)) },
                )
                VerticalDivider(Modifier.padding(vertical = 8.dp).size(width = 1.dp, height = 28.dp))
                colors.forEach { c -> Swatch(c, selected = c == color && tool != Tool.ERASER, size = 30) { onColor(c) } }
                IconButton(onClick = onPick) { Icon(Icons.Default.Palette, "Elegir color") }
            }
            if (tool != Tool.ERASER) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Vista previa: un trazo con el color, grosor y opacidad elegidos
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        val previewSize = (4 + 32 * (width - MIN_WIDTH) / (MAX_WIDTH - MIN_WIDTH)).dp
                        Box(
                            Modifier
                                .size(previewSize)
                                .background(ComposeColor(color).copy(alpha = opacity), CircleShape)
                        )
                    }
                    LabeledSlider(
                        label = "Tamaño",
                        valueText = "${((width - MIN_WIDTH) / (MAX_WIDTH - MIN_WIDTH) * 100).roundToInt()}%",
                        value = width,
                        range = MIN_WIDTH..MAX_WIDTH,
                        onChange = onWidth,
                        modifier = Modifier.weight(1f),
                    )
                    LabeledSlider(
                        label = "Opacidad",
                        valueText = "${(opacity * 100).roundToInt()}%",
                        value = opacity,
                        range = MIN_OPACITY..1f,
                        onChange = onOpacity,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.height(32.dp))
    }
}
