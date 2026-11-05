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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AddComment
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PanTool
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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
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
import com.estudia.app.data.ToolbarPosition
import com.estudia.app.ui.components.ColorPickerDialog
import com.estudia.app.ui.components.FastScrollbar
import com.estudia.app.ui.components.HIGHLIGHT_PRESETS
import com.estudia.app.ui.components.Swatch
import com.estudia.app.ui.components.TEXT_PRESETS
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

/** Qué hace un dedo sobre la página. */
private enum class Tool(val label: String) {
    HAND("Mano"),
    PEN("Lápiz"),
    HIGHLIGHT("Destacador"),
    ERASER("Borrador"),
    NOTE("Nota");

    /** Un dedo dibuja o borra, así que moverse queda para dos dedos. */
    val draws get() = this == PEN || this == HIGHLIGHT || this == ERASER
    val hasOptions get() = this == PEN || this == HIGHLIGHT
}

/** Grosores como fracción del ancho de la página. */
private const val MIN_WIDTH = 0.004f
private const val MAX_WIDTH = 0.07f
private const val MIN_PEN_WIDTH = 0.001f
private const val MAX_PEN_WIDTH = 0.012f
private const val MIN_OPACITY = 0.1f

private val EraserIcon: ImageVector = ImageVector.Builder("Eraser", 24.dp, 24.dp, 24f, 24f)
    .addPath(
        pathData = addPathNodes(
            "M16.24 3.56l4.95 4.95c.78.79.78 2.05 0 2.84L12 20.53a4 4 0 0 1-5.66 0L2.81 17c-.79-.79-.79-2.05 0-2.84" +
                "l10.6-10.6c.79-.78 2.05-.78 2.83 0zM4.22 15.58l3.54 3.53c.78.79 2.04.79 2.83 0l3.53-3.53-4.95-4.95-4.95 4.95z"
        ),
        pathFillType = PathFillType.EvenOdd,
        fill = SolidColor(ComposeColor.Black),
    )
    .build()

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

    val recentPenColors = repo.recentColors.recent(ColorKind.TEXT)
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

    // ---------- Trazos ----------

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
        repo.rememberColor(if (stroke.pen) ColorKind.TEXT else ColorKind.HIGHLIGHT, stroke.color)
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
            .onSuccess { messages.emit("PDF guardado con tus anotaciones") }
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
    val recentPen by vm.recentPenColors.collectAsState()
    val notes by vm.notes.collectAsState()
    val recentNoteColors by vm.recentNoteColors.collectAsState()

    // Notas: colocar una nueva, ver la tocada, índice y salto con aviso visual
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

    var tool by rememberSaveable { mutableStateOf(Tool.HAND) }
    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    val settings = (LocalContext.current.applicationContext as EstudiaApp).settings
    val toolbarOnTop = settings.toolbarPosition == ToolbarPosition.TOP
    // Cada herramienta recuerda su propio color
    var highlightColor by rememberSaveable { mutableIntStateOf(HIGHLIGHT_PRESETS.first()) }
    var penColor by rememberSaveable { mutableIntStateOf(TEXT_PRESETS.first()) }
    var pickerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(recent.firstOrNull()) { recent.firstOrNull()?.let { if (highlightColor == HIGHLIGHT_PRESETS.first()) highlightColor = it } }
    LaunchedEffect(recentPen.firstOrNull()) { recentPen.firstOrNull()?.let { if (penColor == TEXT_PRESETS.first()) penColor = it } }

    // Volver a tocar la herramienta activa muestra u oculta sus opciones
    fun selectTool(t: Tool) {
        optionsOpen = t.hasOptions && (t != tool || !optionsOpen)
        tool = t
    }
    val toolbar: @Composable (Boolean) -> Unit = { atBottom ->
        PdfToolbar(
            tool = tool,
            penColor = penColor,
            highlightColor = highlightColor,
            noteCount = notes.size,
            atBottom = atBottom,
            onTool = ::selectTool,
            onNotesIndex = { indexOpen = true },
        )
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let(vm::exportTo)
    }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { vm.noteCreated.collect(onOpenNote) }

    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    val strokesByPage = remember(strokes) { strokes.groupBy { it.page } }
    val notesByPage = remember(notes) { notes.groupBy { it.page } }

    Scaffold(
        bottomBar = {
            if (!toolbarOnTop && ratios != null) toolbar(true)
        },
        topBar = {
            // Herramientas justo debajo de la barra superior, salvo que en Ajustes se pidan abajo
            Column {
            TopAppBar(
                title = { Text(doc?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    IconButton(onClick = vm::undo, enabled = canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Deshacer") }
                    IconButton(onClick = vm::redo, enabled = canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Rehacer") }
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
                                text = { Text("Guardar copia con anotaciones") },
                                leadingIcon = { Icon(Icons.Default.SaveAlt, null) },
                                enabled = strokes.isNotEmpty(),
                                onClick = { menu = false; exporter.launch("${doc?.title ?: "documento"} (anotado).pdf") },
                            )
                            DropdownMenuItem(
                                text = { Text("Convertir en nota editable") },
                                leadingIcon = { Icon(Icons.Default.EditNote, null) },
                                onClick = { menu = false; vm.createEditableNote() },
                            )
                        }
                    }
                },
                colors = appBarColors(),
            )
            if (toolbarOnTop && ratios != null) toolbar(false)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    // Dos dedos: zoom y desplazamiento, con cualquier herramienta
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
                    Box(Modifier.fillMaxSize().horizontalScroll(hScroll, enabled = !tool.draws)) {
                        LazyColumn(
                            state = listState,
                            userScrollEnabled = !tool.draws,
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
                                    tool = tool,
                                    color = if (tool == Tool.PEN) penColor else highlightColor,
                                    strokeWidth = if (tool == Tool.PEN) settings.penWidth else settings.highlighterWidth,
                                    strokeAlpha = if (tool == Tool.PEN) 1f else settings.highlighterOpacity,
                                    straightLine = tool == Tool.HIGHLIGHT && settings.highlighterStraight,
                                    onDrawStart = { optionsOpen = false },
                                    notes = notesByPage[index].orEmpty(),
                                    pulse = pulse,
                                    onPlace = { x, y ->
                                        tool = Tool.HAND
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

                    if (tool == Tool.NOTE) {
                        PlacingBanner(onCancel = { tool = Tool.HAND }, modifier = Modifier.align(Alignment.TopCenter))
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
                                .padding(vertical = 8.dp),
                        )
                    }
                    if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    // Del lado contrario a la barra, para que el panel de opciones no lo tape
                    if (tool != Tool.NOTE) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            tonalElevation = 4.dp,
                            modifier = Modifier
                                .align(if (toolbarOnTop) Alignment.BottomCenter else Alignment.TopCenter)
                                .padding(16.dp),
                        ) {
                            Text(
                                if (tool.draws) "Usa dos dedos para moverte · $currentPage / ${pages.size}" else "$currentPage / ${pages.size}",
                                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                    if (optionsOpen && tool.hasOptions) {
                        val panelModifier = Modifier
                            .align(if (toolbarOnTop) Alignment.TopCenter else Alignment.BottomCenter)
                            .padding(8.dp)
                        if (tool == Tool.PEN) {
                            ToolOptions(
                                color = penColor,
                                colors = (recentPen + TEXT_PRESETS).distinct().take(10),
                                onColor = { penColor = it },
                                onPick = { pickerOpen = true },
                                width = settings.penWidth,
                                widthRange = MIN_PEN_WIDTH..MAX_PEN_WIDTH,
                                onWidth = settings::updatePenWidth,
                                modifier = panelModifier,
                            )
                        } else {
                            ToolOptions(
                                color = highlightColor,
                                colors = (recent + HIGHLIGHT_PRESETS).distinct().take(10),
                                onColor = { highlightColor = it },
                                onPick = { pickerOpen = true },
                                width = settings.highlighterWidth,
                                widthRange = MIN_WIDTH..MAX_WIDTH,
                                onWidth = settings::updateHighlighterWidth,
                                modifier = panelModifier,
                                straight = settings.highlighterStraight,
                                onStraight = settings::updateHighlighterStraight,
                                opacity = settings.highlighterOpacity,
                                onOpacity = settings::updateHighlighterOpacity,
                            )
                        }
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

    BackHandler(enabled = optionsOpen || tool != Tool.HAND) {
        if (optionsOpen) optionsOpen = false else tool = Tool.HAND
    }

    if (pickerOpen) {
        val pen = tool == Tool.PEN
        ColorPickerDialog(
            title = if (pen) "Color del lápiz" else "Color del destacador",
            initial = if (pen) penColor else highlightColor,
            recent = if (pen) recentPen else recent,
            presets = if (pen) TEXT_PRESETS else HIGHLIGHT_PRESETS,
            onDismiss = { pickerOpen = false },
            onPick = {
                pickerOpen = false
                if (pen) penColor = it else highlightColor = it
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
    tool: Tool,
    color: Int,
    strokeWidth: Float,
    strokeAlpha: Float,
    straightLine: Boolean,
    onDrawStart: () -> Unit,
    notes: List<StickyNoteEntity>,
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
        val drawModifier = if (!tool.draws) Modifier else Modifier.pointerInput(tool, color, strokeWidth, strokeAlpha, straightLine) {
            awaitEachGesture {
                val down = awaitFirstDown()
                onDrawStart()
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
                val straight = straightLine
                val pen = tool == Tool.PEN
                current = PdfStroke(index, color, strokeWidth, points.toList(), straight, strokeAlpha, pen)
                val completed = drag(down.id) { change ->
                    val p = change.position
                    if (straight) {
                        points.clear()
                        points += listOf(start.x / w, start.y / h, p.x / w, start.y / h)
                    } else {
                        points += listOf(p.x.coerceIn(0f, w) / w, p.y.coerceIn(0f, h) / h)
                    }
                    current = PdfStroke(index, color, strokeWidth, points.toList(), straight, strokeAlpha, pen)
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

        if (tool == Tool.NOTE) {
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
                    PageNoteMarker(note, pageSize, tool == Tool.HAND, pulse, onNoteTap, onNoteMoved)
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

/** Lápiz: opaco. Destacador: en modo multiplicar, así el texto negro sigue viéndose negro. */
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
        blendMode = if (stroke.pen) BlendMode.SrcOver else BlendMode.Multiply,
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

/** Barra de herramientas del PDF; va arriba o abajo según los ajustes. */
@Composable
private fun PdfToolbar(
    tool: Tool,
    penColor: Int,
    highlightColor: Int,
    noteCount: Int,
    atBottom: Boolean,
    onTool: (Tool) -> Unit,
    onNotesIndex: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (atBottom) Modifier.navigationBarsPadding() else Modifier)
        ) {
            Tool.entries.forEach { t ->
                ToolbarItem(
                    icon = when (t) {
                        Tool.HAND -> Icons.Default.PanTool
                        Tool.PEN -> Icons.Default.Edit
                        Tool.HIGHLIGHT -> Icons.Default.BorderColor
                        Tool.ERASER -> EraserIcon
                        Tool.NOTE -> Icons.Default.AddComment
                    },
                    label = t.label,
                    selected = t == tool,
                    // Color actual de la herramienta
                    dot = when (t) {
                        Tool.PEN -> penColor
                        Tool.HIGHLIGHT -> highlightColor
                        else -> null
                    },
                    onClick = { onTool(t) },
                )
            }
            ToolbarItem(
                icon = Icons.AutoMirrored.Filled.ListAlt,
                label = if (noteCount > 0) "Índice ($noteCount)" else "Índice",
                selected = false,
                dot = null,
                onClick = onNotesIndex,
            )
        }
    }
}

@Composable
private fun RowScope.ToolbarItem(icon: ImageVector, label: String, selected: Boolean, dot: Int?, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(top = 6.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val c = MaterialTheme.colorScheme
        Box(
            Modifier
                .size(width = 48.dp, height = 28.dp)
                .background(if (selected) c.primaryContainer else ComposeColor.Transparent, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) c.onPrimaryContainer else c.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            if (dot != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 8.dp)
                        .size(9.dp)
                        .background(ComposeColor(dot), CircleShape)
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) c.onSurface else c.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Opciones de la herramienta activa, flotando sobre la página. */
@Composable
private fun ToolOptions(
    color: Int,
    colors: List<Int>,
    onColor: (Int) -> Unit,
    onPick: () -> Unit,
    width: Float,
    widthRange: ClosedFloatingPointRange<Float>,
    onWidth: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** Solo el destacador elige entre recto y libre, y su opacidad. */
    straight: Boolean? = null,
    onStraight: (Boolean) -> Unit = {},
    opacity: Float? = null,
    onOpacity: (Float) -> Unit = {},
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                colors.forEach { c -> Swatch(c, selected = c == color, size = 30) { onColor(c) } }
                IconButton(onClick = onPick) { Icon(Icons.Default.Palette, "Elegir color") }
            }
            if (straight != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = straight,
                        onClick = { onStraight(true) },
                        label = { Text("Recto") },
                        leadingIcon = { Icon(Icons.Default.HorizontalRule, null, Modifier.size(18.dp)) },
                    )
                    FilterChip(
                        selected = !straight,
                        onClick = { onStraight(false) },
                        label = { Text("Libre") },
                        leadingIcon = { Icon(Icons.Default.Gesture, null, Modifier.size(18.dp)) },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Vista previa: un punto con el color, grosor y opacidad elegidos
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    val fraction = (width - widthRange.start) / (widthRange.endInclusive - widthRange.start)
                    Box(
                        Modifier
                            .size((4 + 32 * fraction).dp)
                            .background(ComposeColor(color).copy(alpha = opacity ?: 1f), CircleShape)
                    )
                }
                LabeledSlider(
                    label = "Tamaño",
                    valueText = "${((width - widthRange.start) / (widthRange.endInclusive - widthRange.start) * 100).roundToInt()}%",
                    value = width,
                    range = widthRange,
                    onChange = onWidth,
                    modifier = Modifier.weight(1f),
                )
                if (opacity != null) {
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
