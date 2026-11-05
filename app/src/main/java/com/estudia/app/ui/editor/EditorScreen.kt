package com.estudia.app.ui.editor

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.BorderAll
import androidx.compose.material.icons.filled.BorderClear
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.FormatColorFill
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ScreenRotation
import kotlin.math.abs
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertPageBreak
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material.icons.filled.Subscript
import androidx.compose.material.icons.filled.Superscript
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.estudia.app.importers.WordBridge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.estudia.app.data.ColorKind
import com.estudia.app.rich.cssColor
import com.estudia.app.ui.components.ColorPickerDialog
import com.estudia.app.ui.components.FastScrollbar
import com.estudia.app.ui.components.HIGHLIGHT_PRESETS
import com.estudia.app.ui.components.NameDialog
import com.estudia.app.ui.components.Swatch
import com.estudia.app.EstudiaApp
import com.estudia.app.data.ToolbarPosition
import com.estudia.app.ui.components.TEXT_PRESETS
import com.estudia.app.ui.theme.appBarColors
import com.estudia.app.ui.notes.NOTE_COLORS
import com.estudia.app.ui.notes.NOTE_ICONS
import com.estudia.app.ui.notes.NoteLookDialog
import com.estudia.app.ui.notes.NotePopup
import com.estudia.app.ui.notes.NotesIndexSheet
import com.estudia.app.ui.notes.PlacingBanner
import com.estudia.app.ui.notes.noteTint
import com.estudia.app.ui.notes.rememberNoteIconUris
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import android.graphics.Color as AndroidColor
import kotlin.math.roundToInt
import android.graphics.Typeface as AndroidTypeface

/**
 * Hojas de la vista de página, en unidades de desplazamiento del editor: cuántas hay, cada cuánto
 * empieza una, dónde empieza la primera y el alto visible. count = 0 en vista móvil.
 */
internal data class PageGeometry(val count: Int = 0, val step: Float = 0f, val top: Float = 0f, val viewport: Float = 0f) {
    /** Hoja (desde 1) que queda en el centro de la pantalla con el desplazamiento [scrollY]. */
    fun pageAt(scrollY: Float): Int =
        if (count <= 0 || step <= 0f) 0 else (((scrollY + viewport / 2 - top) / step).toInt() + 1).coerceIn(1, count)
}

private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

/** Familias disponibles en todos los Android (nombre CSS → etiqueta). */
private val FONTS = listOf(
    "sans-serif" to "Sans Serif",
    "serif" to "Serif",
    "monospace" to "Monoespaciada",
    "sans-serif-condensed" to "Condensada",
    "sans-serif-light" to "Ligera",
    "serif-monospace" to "Máquina de escribir",
    "casual" to "Casual",
    "cursive" to "Cursiva",
)

private val SIZES = listOf(8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 36, 48, 72)

/** Envía órdenes al editor web. Los argumentos van como literales JSON para escaparlos bien. */
internal class WebEditor {
    var view: WebView? = null

    fun call(function: String, vararg args: Any?) {
        val params = args.joinToString(",") { arg ->
            when (arg) {
                null -> "null"
                is String -> Json.encodeToString(String.serializer(), arg)
                else -> arg.toString()
            }
        }
        view?.evaluateJavascript("$function($params)", null)
    }

    /** Ejecuta JavaScript ya armado (p. ej. con un JSON como argumento). */
    fun callRaw(js: String) {
        view?.evaluateJavascript(js, null)
    }

    /** Evalúa una expresión que devuelve texto y entrega ese texto (sin las comillas de JSON). */
    fun eval(js: String, onResult: (String) -> Unit) {
        view?.evaluateJavascript(js) { raw ->
            onResult(runCatching { Json.decodeFromString(String.serializer(), raw) }.getOrDefault(""))
        }
    }

    /** Guarda lo pendiente y avisa cuando el HTML ya llegó a Kotlin. */
    fun flush(then: () -> Unit = {}) {
        val v = view ?: return then()
        v.evaluateJavascript("window.flushSave && flushSave();1") { then() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    darkTheme: Boolean,
    onBack: () -> Unit,
    onOpenStickyNote: (Long) -> Unit,
    vm: EditorViewModel = viewModel(),
) {
    val context = LocalContext.current
    val doc by vm.document.collectAsState()
    val content by vm.content.collectAsState()
    val error by vm.error.collectAsState()
    val format by vm.state.collectAsState()
    val recentHighlights by vm.recentHighlights.collectAsState()
    val recentTextColors by vm.recentTextColors.collectAsState()
    val notes by vm.notes.collectAsState()
    val recentNoteColors by vm.recentNoteColors.collectAsState()
    val editor = remember { WebEditor() }

    // Notas adhesivas
    val iconUris = rememberNoteIconUris()
    var webReady by remember { mutableIntStateOf(0) }
    var placing by rememberSaveable { mutableStateOf(false) }
    var pendingAnchor by remember { mutableStateOf<String?>(null) }
    var openNoteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var indexOpen by remember { mutableStateOf(false) }
    var docScroll by remember { mutableStateOf(0 to 0) }
    var pages by remember { mutableStateOf(PageGeometry()) }
    LaunchedEffect(Unit) { vm.stickyCreated.collect(onOpenStickyNote) }
    LaunchedEffect(notes, webReady) {
        if (webReady == 0) return@LaunchedEffect
        val list = notes.map { n ->
            val uris = iconUris[n.icon] ?: iconUris.values.first()
            WebNote(
                id = n.id.toString(),
                img = if (noteTint(n.color) == Color.Black) uris.second else uris.first,
                color = cssColor(n.color),
                x = n.x, y = n.y, block = n.block, docY = n.docY, size = n.size,
            )
        }
        editor.callRaw("notes.set(${Json.encodeToString(ListSerializer(WebNote.serializer()), list)})")
    }
    LaunchedEffect(placing, webReady) {
        if (webReady > 0) editor.callRaw(if (placing) "notes.startPlacing()" else "notes.cancelPlacing()")
    }

    // Todo documento se abre listo para editar; el modo lectura está en el menú
    var editing by rememberSaveable { mutableStateOf(true) }
    // Vista de página (como Word) si el documento trae tamaño de hoja
    var pageView by rememberSaveable { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(content != null) {
        val c = content ?: return@LaunchedEffect
        if (pageView == null) pageView = c.html.contains("data-w=")
    }
    var picker by remember { mutableStateOf<ColorKind?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    // Revisar: buscar y reemplazar, contar palabras
    var finding by rememberSaveable { mutableStateOf(false) }
    var found by remember { mutableStateOf(0 to 0) }
    var stats by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(editing) { if (!editing && finding) { finding = false; editor.call("finder.clear") } }
    stats?.let { raw -> WordCountDialog(raw) { stats = null } }

    // Guardar / compartir como Word: primero se vacía lo pendiente del editor
    val snackbar = remember { SnackbarHostState() }
    val busy by vm.busy.collectAsState()
    val wordSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DOCX_MIME)) { uri ->
        uri?.let { editor.flush { vm.exportWord(it) } }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    // La misma galería sirve para insertar una imagen nueva o reemplazar la seleccionada
    var replacingImage by remember { mutableStateOf(false) }
    var pickingLogo by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::importImage)
    }
    LaunchedEffect(Unit) {
        vm.imageReady.collect {
            when {
                // Diseño "Logo y título" del encabezado/pie abierto
                pickingLogo -> editor.call("headerFooter.preset", "logo", doc?.title.orEmpty(), it)
                replacingImage -> editor.call("imageTools.replace", it)
                else -> editor.call("editor.insertImage", it)
            }
            replacingImage = false
            pickingLogo = false
        }
    }
    LaunchedEffect(Unit) {
        vm.shareFile.collect { f ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
            val send = Intent(Intent.ACTION_SEND)
                .setType(DOCX_MIME)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "Compartir como Word"))
        }
    }

    val quickHighlight = recentHighlights.firstOrNull() ?: HIGHLIGHT_PRESETS.first()
    fun highlight(color: Int?) {
        editor.call("editor.highlight", color?.let(::cssColor))
        color?.let { vm.rememberColor(ColorKind.HIGHLIGHT, it) }
    }
    fun exit() = editor.flush(onBack)

    // Encabezado ("h") y pie ("f"): se editan en la hoja; "n": cuadro del número de página
    var pageNumber by remember { mutableStateOf<HeadersState?>(null) }
    fun openHeaders(kind: String) {
        if (!editing) return
        if (kind == "n") {
            editor.eval("headerFooter.get()") { raw ->
                pageNumber = runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(HeadersState.serializer(), raw) }.getOrNull()
                    ?: HeadersState()
            }
            return
        }
        // En vista móvil no hay hojas: primero se pasa a vista de página
        if (pageView == true) {
            editor.call("headerFooter.enter", kind)
        } else {
            pageView = true
            scope.launch {
                delay(450)
                editor.call("headerFooter.enter", kind)
            }
        }
    }
    pageNumber?.let { st ->
        PageNumberDialog(
            current = st.h.num?.let { "h" to it } ?: st.f.num?.let { "f" to it },
            numbering = format.pageFmt,
            start = format.pageStart,
            onDismiss = { pageNumber = null },
            onApply = { c ->
                pageNumber = null
                pageView = true
                c.where?.let { editor.call("headerFooter.number", it, c.align, c.fmt) }
                if (c.numbering != format.pageFmt || c.start != format.pageStart) {
                    editor.call("headerFooter.numbering", c.numbering, c.start)
                }
            },
        )
    }

    BackHandler { if (placing) placing = false else exit() }
    // Atrás mientras se edita el encabezado: vuelve al cuerpo (registrado después, gana)
    BackHandler(enabled = format.hf != null) { editor.call("headerFooter.exit") }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) editor.flush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            editor.flush()
        }
    }

    LaunchedEffect(editing) {
        editor.call("editor.setReadOnly", !editing)
        if (!editing) editor.view?.let { v ->
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(v.windowToken, 0)
        }
    }
    LaunchedEffect(pageView) { pageView?.let { editor.call("editor.setMode", if (it) "page" else "mobile") } }
    LaunchedEffect(darkTheme) { editor.call("editor.setDark", darkTheme) }

    val toolbarOnTop = (context.applicationContext as EstudiaApp).settings.toolbarPosition == ToolbarPosition.TOP
    val toolbars: @Composable () -> Unit = {
        when {
            editing -> FormattingToolbar(
                format = format,
                noteCount = notes.size,
                placingNote = placing,
                onAddNote = { placing = !placing },
                onNotesIndex = { indexOpen = true },
                quickHighlight = quickHighlight,
                recentHighlights = recentHighlights,
                editor = editor,
                onHighlight = ::highlight,
                onPick = { picker = it },
                onInsertImage = {
                    replacingImage = false
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onReplaceImage = {
                    replacingImage = true
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onFind = { finding = true },
                onLayoutChange = { pageView = true },
                onStats = { editor.eval("docStats()") { stats = it } },
                onHeaderFooter = ::openHeaders,
                docTitle = doc?.title.orEmpty(),
                onLogo = {
                    pickingLogo = true
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
            else -> {
                if (format.hasSelection) {
                    HighlightBar(
                        colors = (recentHighlights + HIGHLIGHT_PRESETS).distinct().take(8),
                        onHighlight = ::highlight,
                        onPick = { picker = ColorKind.HIGHLIGHT },
                        onRemove = { highlight(null) },
                    )
                }
                // En modo lectura también se pueden agregar y buscar notas
                RibbonBar(tabs = { RibbonTabButton("Notas", selected = true) {} }) {
                    NotesTools(notes.size, placing, onAdd = { placing = !placing }, onIndex = { indexOpen = true })
                }
            }
        }
        if (finding && editing) {
            FindBar(
                found = found,
                onSearch = { editor.call("finder.search", it) },
                onNext = { editor.call("finder.next") },
                onPrev = { editor.call("finder.prev") },
                onReplace = { editor.call("finder.replace", it) },
                onReplaceAll = { text ->
                    editor.eval("String(finder.replaceAll(${Json.encodeToString(String.serializer(), text)}))") { n ->
                        scope.launch { snackbar.showSnackbar(if (n == "1") "1 reemplazo" else "$n reemplazos") }
                    }
                },
                onClose = { finding = false; found = 0 to 0; editor.call("finder.clear") },
            )
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!toolbarOnTop) Column(Modifier.navigationBarsPadding()) { toolbars() }
        },
        topBar = {
            // Herramientas justo debajo de la barra superior, salvo que en Ajustes se pidan abajo
            Column {
            TopAppBar(
                title = {
                    Text(
                        doc?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { renaming = true },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::exit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
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
                                text = { Text(if (editing) "Ir a modo lectura" else "Ir a modo edición") },
                                leadingIcon = {
                                    Icon(if (editing) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.Edit, null)
                                },
                                onClick = { menu = false; editing = !editing },
                            )
                            DropdownMenuItem(
                                text = { Text(if (pageView == true) "Vista móvil (ajustar texto)" else "Vista de página") },
                                onClick = { menu = false; pageView = pageView != true },
                            )
                            DropdownMenuItem(
                                text = { Text(if (doc?.pinned == true) "Quitar de fijados" else "Fijar") },
                                leadingIcon = {
                                    Icon(if (doc?.pinned == true) Icons.Outlined.PushPin else Icons.Filled.PushPin, null)
                                },
                                onClick = { menu = false; vm.togglePin() },
                            )
                            DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menu = false; renaming = true })
                            if (WordBridge.available) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Guardar como Word (.docx)") },
                                    leadingIcon = { Icon(Icons.Default.SaveAlt, null) },
                                    enabled = !busy,
                                    onClick = { menu = false; wordSaver.launch("${doc?.title ?: "Documento"}.docx") },
                                )
                                DropdownMenuItem(
                                    text = { Text("Compartir como Word") },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                    enabled = !busy,
                                    onClick = { menu = false; editor.flush { vm.shareWord() } },
                                )
                            }
                        }
                    }
                },
                colors = appBarColors(),
            )
            if (toolbarOnTop) toolbars()
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val initial = content
            when {
                error != null -> Text(error!!, Modifier.align(Alignment.Center).padding(24.dp))
                initial == null || pageView == null ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> {
                    val startReadOnly = !editing
                    val startPaged = pageView == true
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            createEditorWebView(
                                ctx,
                                html = initial.html,
                                baseDir = "file://${initial.baseDir.absolutePath}/",
                                readOnly = startReadOnly,
                                paged = startPaged,
                                dark = darkTheme,
                                bridge = EditorBridge(
                                    onSave = vm::save,
                                    onState = vm::onState,
                                    onNoteTap = { openNoteId = it },
                                    onNotePlaced = { placing = false; pendingAnchor = it },
                                    onNoteMoved = vm::moveStickyNote,
                                    onScroll = { y, max -> vm.onScroll(y); docScroll = y to max },
                                    onModel = vm::saveModel,
                                    onFound = { cur, total -> found = cur to total },
                                    onRotateImage = vm::rotateImage,
                                    onPages = { pages = it },
                                    onHeaderFooter = ::openHeaders,
                                ),
                                initialScroll = vm.scrollY,
                                onLoaded = { webReady++ },
                            ).also { editor.view = it }
                        },
                        onRelease = { editor.view = null },
                    )
                }
            }
            val (scrollY, scrollMax) = docScroll
            if (scrollMax > 0 && content != null) {
                FastScrollbar(
                    fraction = scrollY.toFloat() / scrollMax,
                    onScrollTo = { editor.call("editor.scrollToFraction", it) },
                    // En vista de página, la hoja; en vista móvil (sin hojas), el porcentaje
                    label = { f ->
                        val n = pages.pageAt(f * scrollMax)
                        if (n > 0) "Página $n de ${pages.count}" else "${(f * 100).roundToInt()} %"
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, bottom = 88.dp),
                )
            }
            if (placing) PlacingBanner(onCancel = { placing = false }, modifier = Modifier.align(Alignment.TopCenter))
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
                onEdit = { openNoteId = null; editor.flush { onOpenStickyNote(note.id) } },
                onDelete = { openNoteId = null; vm.deleteStickyNote(note.id) },
                onClose = { openNoteId = null },
            )
        }
    }

    pendingAnchor?.let { anchor ->
        NoteLookDialog(
            title = "Nueva nota",
            confirmLabel = "Crear",
            initialIcon = NOTE_ICONS.first().first,
            initialColor = recentNoteColors.firstOrNull() ?: NOTE_COLORS.first(),
            recentColors = recentNoteColors,
            onDismiss = { pendingAnchor = null },
        ) { icon, noteColor, size ->
            pendingAnchor = null
            // El párrafo recibió su data-anchor: se guarda antes de abrir la nota
            editor.flush { vm.createStickyNote(anchor, icon, noteColor, size) }
        }
    }

    if (indexOpen) {
        NotesIndexSheet(
            notes = notes,
            isPdf = false,
            onJump = { indexOpen = false; editor.call("notes.focus", it.id.toString()) },
            onDismiss = { indexOpen = false },
        )
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

    if (renaming) {
        NameDialog("Renombrar", doc?.title.orEmpty(), onDismiss = { renaming = false }) {
            renaming = false
            vm.rename(it)
        }
    }
}

/** Selector de color para el texto o el resaltado de la selección actual. */
@Composable
internal fun FormatColorPicker(
    kind: ColorKind,
    format: EditorState,
    quickHighlight: Int,
    recentHighlights: List<Int>,
    recentTextColors: List<Int>,
    editor: WebEditor,
    onHighlight: (Int?) -> Unit,
    onTextColorUsed: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val isHighlight = kind == ColorKind.HIGHLIGHT
    ColorPickerDialog(
        title = if (isHighlight) "Color de resaltado" else "Color de texto",
        initial = (if (isHighlight) parseCssColor(format.back) ?: quickHighlight else parseCssColor(format.color))
            ?: TEXT_PRESETS[1],
        recent = if (isHighlight) recentHighlights else recentTextColors,
        presets = if (isHighlight) HIGHLIGHT_PRESETS else TEXT_PRESETS,
        onDismiss = onDismiss,
        onPick = { color ->
            onDismiss()
            if (isHighlight) onHighlight(color) else {
                editor.call("editor.color", cssColor(color))
                onTextColorUsed(color)
            }
        },
        onClear = {
            onDismiss()
            if (isHighlight) onHighlight(null) else editor.call("editor.color", null)
        },
    )
}

/**
 * Lo que el editor web le avisa a Kotlin. Las llamadas llegan en el hilo del WebView
 * y se reenvían al hilo principal.
 */
internal class EditorBridge(
    private val onSave: (String) -> Unit,
    private val onState: (String) -> Unit,
    private val onNoteTap: (Long) -> Unit = {},
    private val onNotePlaced: (String) -> Unit = {},
    private val onNoteMoved: (Long, String) -> Unit = { _, _ -> },
    private val onScroll: (Int, Int) -> Unit = { _, _ -> },
    private val onModel: (String) -> Unit = {},
    private val onFound: (Int, Int) -> Unit = { _, _ -> },
    private val onRotateImage: (String, Int) -> String = { _, _ -> "" },
    private val onPages: (PageGeometry) -> Unit = {},
    private val onHeaderFooter: (String) -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface fun save(html: String) = onSave(html)
    /** Qué cambió respecto al Word original, para poder guardarlo como .docx. */
    @JavascriptInterface fun model(json: String) = onModel(json)
    /** Resultado de Buscar: coincidencia actual (desde 1) y total. */
    @JavascriptInterface fun found(current: Int, total: Int) { main.post { onFound(current, total) } }
    /** Gira una imagen del documento; devuelve el archivo nuevo ("" si no se pudo). */
    @JavascriptInterface fun rotateImage(src: String, degrees: Int): String = onRotateImage(src, degrees)
    @JavascriptInterface fun pages(count: Int, step: Double, top: Double, viewport: Double) {
        main.post { onPages(PageGeometry(count, step.toFloat(), top.toFloat(), viewport.toFloat())) }
    }
    /** Doble toque en el margen de una hoja: "h" encabezado, "f" pie. */
    @JavascriptInterface fun headerFooter(kind: String) { main.post { onHeaderFooter(kind) } }
    @JavascriptInterface fun onState(json: String) = onState.invoke(json)
    @JavascriptInterface fun openNote(id: String) { id.toLongOrNull()?.let { main.post { onNoteTap(it) } } }
    @JavascriptInterface fun placeNote(anchor: String) { main.post { onNotePlaced(anchor) } }
    @JavascriptInterface fun moveNote(id: String, anchor: String) { id.toLongOrNull()?.let { main.post { onNoteMoved(it, anchor) } } }
    @JavascriptInterface fun scrolled(y: Int, max: Int) { main.post { onScroll(y, max) } }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
internal fun createEditorWebView(
    context: Context,
    html: String,
    baseDir: String,
    readOnly: Boolean,
    paged: Boolean,
    dark: Boolean,
    bridge: EditorBridge,
    initialScroll: Int = 0,
    onLoaded: () -> Unit = {},
): WebView {
    val css = context.assets.open("editor/editor.css").bufferedReader().use { it.readText() }
    val js = context.assets.open("editor/editor.js").bufferedReader().use { it.readText() }
    val body = if (html.trimStart().startsWith("<article")) html else "<article id=\"page\">$html</article>"
    val classes = listOfNotNull(if (paged) "paged" else "mobile", if (dark) "dark" else null, if (readOnly) "readonly" else null)
    val page = """<!DOCTYPE html><html><head><meta charset="utf-8">
        |<meta name="viewport" content="width=device-width, initial-scale=1, minimum-scale=0.3, maximum-scale=5">
        |<style>$css</style></head><body class="${classes.joinToString(" ")}" data-scroll="$initialScroll">
        |<div id="sheet">$body<div id="notes-layer"></div></div><script>$js</script></body></html>""".trimMargin()

    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(AndroidColor.TRANSPARENT)
        settings.javaScriptEnabled = true
        // La barra de desplazamiento rápido (FastScrollbar) reemplaza a la del WebView
        isVerticalScrollBarEnabled = false
        // Solo para mostrar las imágenes del documento, guardadas junto al HTML
        settings.allowFileAccess = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.setSupportZoom(true)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript("window.editor && editor.fit()", null)
                onLoaded()
            }
        }
        addJavascriptInterface(bridge, "Android")
        // Cada vez que el WebView cambia de ancho (al aparecer, al girar) la hoja se vuelve a ajustar
        addOnLayoutChangeListener { v, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) (v as WebView).evaluateJavascript("window.editor && editor.fit()", null)
        }
        loadDataWithBaseURL(baseDir, page, "text/html", "utf-8", null)
    }
}

/** "rgb(r, g, b)" o "#rrggbb" → ARGB. Null si es transparente o no se reconoce. */
internal fun parseCssColor(css: String): Int? {
    val s = css.trim()
    if (s.startsWith("#") && s.length == 7) return s.substring(1).toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
    val m = Regex("""rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)""").find(s) ?: return null
    if (m.groupValues[4].isNotEmpty() && m.groupValues[4].toFloat() == 0f) return null
    return AndroidColor.rgb(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
}

@Composable
internal fun FormattingToolbar(
    format: EditorState,
    quickHighlight: Int,
    recentHighlights: List<Int>,
    editor: WebEditor,
    onHighlight: (Int?) -> Unit,
    onPick: (ColorKind) -> Unit,
    onInsertImage: (() -> Unit)? = null,
    onFind: (() -> Unit)? = null,
    onStats: (() -> Unit)? = null,
    onReplaceImage: (() -> Unit)? = null,
    /** Se cambió la hoja: la pantalla pasa a vista de página para que se vea. */
    onLayoutChange: (() -> Unit)? = null,
    /** Título del documento, para los diseños de encabezado. */
    docTitle: String = "",
    /** Elegir el logo del diseño "Logo y título". */
    onLogo: (() -> Unit)? = null,
    /** Encabezado ("h"), pie ("f") o número de página ("n"); solo en documentos. */
    onHeaderFooter: ((String) -> Unit)? = null,
    /** Pestaña Notas; solo en documentos. */
    noteCount: Int = 0,
    placingNote: Boolean = false,
    onAddNote: (() -> Unit)? = null,
    onNotesIndex: (() -> Unit)? = null,
) {
    // Cinta como la de Word: pestañas arriba y sus herramientas debajo
    var tab by rememberSaveable { mutableStateOf(RibbonTab.HOME) }
    val image = format.image
    val tabs = RibbonTab.entries.filter {
        when (it) {
            RibbonTab.REVIEW -> (onFind != null || onStats != null) && format.hf == null
            RibbonTab.LAYOUT -> onLayoutChange != null && format.hf == null
            RibbonTab.NOTES -> onAddNote != null && onNotesIndex != null && format.hf == null
            RibbonTab.HEADER -> format.hf != null
            RibbonTab.IMAGE -> image != null
            RibbonTab.TABLE -> format.table != null
            else -> true
        }
    }
    // Al tocar una imagen se abre su pestaña; al soltarla se vuelve a Inicio
    LaunchedEffect(image != null) {
        if (image != null) tab = RibbonTab.IMAGE
        else if (tab == RibbonTab.IMAGE) tab = RibbonTab.HOME
    }
    // Al salir de la tabla su pestaña desaparece
    LaunchedEffect(format.table != null) {
        if (format.table == null && tab == RibbonTab.TABLE) tab = RibbonTab.HOME
    }
    // Al abrir el encabezado o pie aparece su pestaña; al cerrarlo se vuelve a Inicio
    LaunchedEffect(format.hf) {
        if (format.hf != null) tab = RibbonTab.HEADER
        else if (tab == RibbonTab.HEADER) tab = RibbonTab.HOME
    }
    RibbonBar(tabs = { tabs.forEach { t -> RibbonTabButton(t.label, selected = t == tab) { tab = t } } }) {
        when (tab) {
            RibbonTab.HOME -> HomeTools(format, quickHighlight, recentHighlights, editor, onHighlight, onPick)
            RibbonTab.INSERT -> InsertTools(editor, onInsertImage, onHeaderFooter, inHeader = format.hf != null)
            RibbonTab.HEADER -> HeaderTools(format, editor, docTitle, onPageNumber = { onHeaderFooter?.invoke("n") }, onLogo = onLogo)
            RibbonTab.PARAGRAPH -> ParagraphTools(format, editor)
            RibbonTab.IMAGE -> if (image != null) ImageTools(image, editor, onReplaceImage)
            RibbonTab.TABLE -> format.table?.let { TableTools(it, editor) }
            RibbonTab.LAYOUT -> LayoutTools(format.page) { p ->
                editor.call("editor.setPage", p.w, p.h, p.mt, p.mr, p.mb, p.ml)
                onLayoutChange?.invoke()
            }
            RibbonTab.REVIEW -> {
                onFind?.let { RibbonButton(Icons.Default.FindReplace, "Buscar y reemplazar", it) }
                onStats?.let { RibbonButton(Icons.Default.Numbers, "Contar palabras", it) }
            }
            RibbonTab.NOTES -> if (onAddNote != null && onNotesIndex != null) {
                NotesTools(noteCount, placingNote, onAddNote, onNotesIndex)
            }
        }
    }
}

/** Cinta como la de Word: pestañas arriba y sus herramientas debajo. */
@Composable
private fun RibbonBar(tabs: @Composable () -> Unit, tools: @Composable () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 2.dp) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
            ) {
                tabs()
            }
            HorizontalDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tools()
            }
        }
    }
}

/** Notas: agregar una (volver a tocar cancela) y abrir el índice. */
@Composable
private fun NotesTools(count: Int, placing: Boolean, onAdd: () -> Unit, onIndex: () -> Unit) {
    RibbonButton(Icons.Default.AddComment, "Agregar nota", selected = placing, onClick = onAdd)
    RibbonButton(Icons.AutoMirrored.Filled.ListAlt, if (count > 0) "Índice ($count)" else "Índice", onIndex)
}

private enum class RibbonTab(val label: String) {
    HOME("Inicio"),
    INSERT("Insertar"),
    PARAGRAPH("Párrafo"),
    LAYOUT("Diseño"),
    REVIEW("Revisar"),
    NOTES("Notas"),
    IMAGE("Imagen"),
    TABLE("Tabla"),
    HEADER("Encabezado y pie"),
}

/** Colores de sombreado de celda (los claros de Word). */
private val CELL_SHADES = listOf(
    0xFFF2F2F2, 0xFFD9D9D9, 0xFFFFF2CC, 0xFFFBE4D5, 0xFFFADBD8, 0xFFFCE4EC,
    0xFFE2EFD9, 0xFFDEEAF6, 0xFFD9E2F3, 0xFFEDE2F6, 0xFFFFE699, 0xFFA8D08D,
).map { it.toInt() }

/** Tabla (con el cursor dentro de una): filas y columnas, combinar y dividir, sombreado, bordes y alineación. */
@Composable
private fun TableTools(table: TableState, editor: WebEditor) {
    val context = LocalContext.current
    var splitDialog by remember { mutableStateOf(false) }

    RibbonButton(Icons.Default.KeyboardArrowUp, "Fila arriba") { editor.call("tableTools.insertRow", false) }
    RibbonButton(Icons.Default.KeyboardArrowDown, "Fila abajo") { editor.call("tableTools.insertRow", true) }
    RibbonButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Col. izq.") { editor.call("tableTools.insertCol", false) }
    RibbonButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Col. der.") { editor.call("tableTools.insertCol", true) }
    ChoiceMenu(
        Icons.Default.Delete, "Eliminar",
        listOf(
            Choice(if (table.cells > 1) "Filas" else "Fila", null, false) { editor.call("tableTools.deleteRow") },
            Choice(if (table.cells > 1) "Columnas" else "Columna", null, false) { editor.call("tableTools.deleteCol") },
            Choice("Tabla", null, false) { editor.call("tableTools.deleteTable") },
        ),
    )
    ToolbarDivider()
    if (table.cells > 1) RibbonButton(Icons.AutoMirrored.Filled.CallMerge, "Combinar") { editor.call("tableTools.merge") }
    RibbonButton(Icons.AutoMirrored.Filled.CallSplit, "Dividir") { splitDialog = true }
    ToolbarDivider()
    ShadeMenu(table.bg) { editor.call("tableTools.shade", it) }
    ChoiceMenu(
        if (table.borders) Icons.Default.BorderAll else Icons.Default.BorderClear, "Bordes",
        listOf(
            Choice("Todos los bordes", null, table.borders) { editor.call("tableTools.borders", true) },
            Choice("Sin bordes", null, !table.borders) { editor.call("tableTools.borders", false) },
        ),
    )
    ToolbarDivider()
    StyleToggle(Icons.Default.VerticalAlignTop, "Texto arriba", table.valign == "top") { editor.call("tableTools.valign", "top") }
    StyleToggle(Icons.Default.VerticalAlignCenter, "Texto al centro", table.valign == "middle") { editor.call("tableTools.valign", "middle") }
    StyleToggle(Icons.Default.VerticalAlignBottom, "Texto abajo", table.valign == "bottom") { editor.call("tableTools.valign", "bottom") }
    RibbonButton(Icons.Default.ViewWeek, "Igualar col.") { editor.call("tableTools.distribute") }

    if (splitDialog) SplitDialog(onDismiss = { splitDialog = false }) { cols, rows ->
        splitDialog = false
        editor.eval("String(tableTools.split($cols,$rows))") { ok ->
            if (ok == "false") {
                Toast.makeText(context, "La celda combinada no se puede repartir en $rows filas iguales", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
private fun ShadeMenu(current: String, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = parseCssColor(current)
    Box {
        Column(
            Modifier
                .clickable { open = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.FormatColorFill, null, modifier = Modifier.size(22.dp))
            Box(Modifier.width(20.dp).height(3.dp).background(selected?.let(::Color) ?: Color.Transparent))
            Text("Sombreado", fontSize = 11.sp, maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CELL_SHADES.chunked(6).forEach { row ->
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    row.forEach { color ->
                        Box(Modifier.padding(4.dp)) {
                            Swatch(color, selected = selected == color, size = 30) { open = false; onSelect(cssColor(color)) }
                        }
                    }
                }
            }
            DropdownMenuItem(
                text = { Text("Sin color") },
                leadingIcon = { Icon(Icons.Default.FormatColorReset, null) },
                onClick = { open = false; onSelect(null) },
            )
        }
    }
}

@Composable
private fun SplitDialog(onDismiss: () -> Unit, onSplit: (Int, Int) -> Unit) {
    var cols by remember { mutableIntStateOf(2) }
    var rows by remember { mutableIntStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dividir celda") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Stepper("Columnas", cols, 1..12) { cols = it }
                Stepper("Filas", rows, 1..20) { rows = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSplit(cols, rows) }, enabled = cols * rows > 1) { Text("Dividir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private class PaperSize(val label: String, val detail: String, val w: Float, val h: Float)

/** Tamaños de hoja (en vertical, puntos). */
private val PAPERS = listOf(
    PaperSize("Carta", "21,6 × 27,9 cm", 612f, 792f),
    PaperSize("Oficio", "21,6 × 33 cm", 612f, 936f),
    PaperSize("A4", "21 × 29,7 cm", 595.3f, 841.9f),
    PaperSize("A5", "14,8 × 21 cm", 419.5f, 595.3f),
)

private class MarginPreset(val label: String, val detail: String, val top: Float, val side: Float)

/** Márgenes de Word: arriba/abajo y a los costados (puntos). */
private val MARGINS = listOf(
    MarginPreset("Normal", "2,54 cm", 72f, 72f),
    MarginPreset("Estrecho", "1,27 cm", 36f, 36f),
    MarginPreset("Moderado", "2,54 / 1,91 cm", 72f, 54f),
    MarginPreset("Ancho", "2,54 / 5,08 cm", 72f, 144f),
)

private fun near(a: Float, b: Float) = abs(a - b) < 2f

/** Diseño: tamaño de la hoja, orientación y márgenes. */
@Composable
private fun LayoutTools(page: PageState, onChange: (PageState) -> Unit) {
    val landscape = page.w > page.h
    val shortSide = minOf(page.w, page.h)
    val longSide = maxOf(page.w, page.h)
    fun oriented(w: Float, h: Float) = if (landscape) page.copy(w = h, h = w) else page.copy(w = w, h = h)

    ChoiceMenu(
        Icons.Default.Description, "Tamaño",
        PAPERS.map { paper ->
            Choice(paper.label, paper.detail, near(paper.w, shortSide) && near(paper.h, longSide)) { onChange(oriented(paper.w, paper.h)) }
        },
    )
    ChoiceMenu(
        Icons.Default.ScreenRotation, "Orientación",
        listOf(
            Choice("Vertical", null, !landscape) { if (landscape) onChange(page.copy(w = page.h, h = page.w)) },
            Choice("Horizontal", null, landscape) { if (!landscape) onChange(page.copy(w = page.h, h = page.w)) },
        ),
    )
    ChoiceMenu(
        Icons.Default.BorderOuter, "Márgenes",
        MARGINS.map { m ->
            val current = near(page.mt, m.top) && near(page.mb, m.top) && near(page.ml, m.side) && near(page.mr, m.side)
            Choice(m.label, m.detail, current) { onChange(page.copy(mt = m.top, mb = m.top, ml = m.side, mr = m.side)) }
        },
    )
}

private class Choice(val label: String, val detail: String?, val selected: Boolean, val onClick: () -> Unit)

@Composable
private fun ChoiceMenu(icon: ImageVector, label: String, choices: List<Choice>) {
    var open by remember { mutableStateOf(false) }
    Box {
        RibbonButton(icon, label) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { c ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(c.label, fontWeight = if (c.selected) FontWeight.Bold else null)
                            c.detail?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    },
                    leadingIcon = { if (c.selected) Icon(Icons.Default.Check, null) else Box(Modifier.size(24.dp)) },
                    onClick = { open = false; c.onClick() },
                )
            }
        }
    }
}

private const val MIN_IMAGE_PT = 12f
private const val PT_PER_CM = 28.3465f

@Composable
private fun SizeSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Column(Modifier.width(150.dp).padding(horizontal = 6.dp)) {
        Text("$label: ${"%.1f".format(Locale.forLanguageTag("es"), value / PT_PER_CM)} cm", fontSize = 11.sp)
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = range,
            modifier = Modifier.height(32.dp),
        )
    }
}

/** Imagen (solo con una imagen seleccionada): tamaño, alineación, girar, reemplazar y eliminar. */
@Composable
private fun ImageTools(image: ImageState, editor: WebEditor, onReplace: (() -> Unit)?) {
    // Ancho (X) y alto (Y); vinculados mantienen la proporción que tiene la imagen ahora
    var linked by rememberSaveable { mutableStateOf(true) }
    var w by remember(image.w) { mutableFloatStateOf(image.w) }
    var h by remember(image.h) { mutableFloatStateOf(image.h) }
    val ratio = if (image.w > 0f) image.h / image.w else 1f
    val maxW = maxOf(image.maxW, image.w)
    val maxH = maxOf(image.maxW * 1.5f, image.h)
    fun apply() = editor.call("imageTools.setSize", w.roundToInt(), h.roundToInt())

    SizeSlider("Ancho", w, MIN_IMAGE_PT..maxW, onChange = {
        w = it
        if (linked) h = (it * ratio).coerceAtLeast(MIN_IMAGE_PT)
    }, onDone = ::apply)
    IconToggleButton(checked = linked, onCheckedChange = { linked = it }) {
        Icon(
            if (linked) Icons.Default.Link else Icons.Default.LinkOff,
            if (linked) "Desvincular ancho y alto" else "Vincular ancho y alto",
            tint = if (linked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SizeSlider("Alto", h, MIN_IMAGE_PT..maxH, onChange = {
        h = it
        if (linked && ratio > 0f) w = (it / ratio).coerceIn(MIN_IMAGE_PT, maxW)
    }, onDone = ::apply)
    // Tamaños rápidos: con la proporción original de la imagen
    listOf(25 to "Pequeña", 50 to "Mediana", 100 to "Completa").forEach { (p, label) ->
        TextButton(onClick = { editor.call("imageTools.size", p) }) { Text(label, fontSize = 12.sp) }
    }
    ToolbarDivider()
    StyleToggle(Icons.AutoMirrored.Filled.FormatAlignLeft, "Alinear a la izquierda", image.align == "left") {
        editor.call("imageTools.align", "left")
    }
    StyleToggle(Icons.Default.FormatAlignCenter, "Centrar", image.align == "center") {
        editor.call("imageTools.align", "center")
    }
    StyleToggle(Icons.AutoMirrored.Filled.FormatAlignRight, "Alinear a la derecha", image.align == "right") {
        editor.call("imageTools.align", "right")
    }
    ToolbarDivider()
    RibbonButton(Icons.AutoMirrored.Filled.RotateLeft, "Girar izq.") { editor.call("imageTools.rotate", -90) }
    RibbonButton(Icons.AutoMirrored.Filled.RotateRight, "Girar der.") { editor.call("imageTools.rotate", 90) }
    ToolbarDivider()
    if (onReplace != null) RibbonButton(Icons.Default.SwapHoriz, "Reemplazar", onReplace)
    RibbonButton(Icons.Default.Delete, "Eliminar") { editor.call("imageTools.remove") }
}

/** Barra de Buscar y reemplazar, bajo la cinta. */
@Composable
private fun FindBar(
    found: Pair<Int, Int>,
    onSearch: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReplace: (String) -> Unit,
    onReplaceAll: (String) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    var replacing by rememberSaveable { mutableStateOf(false) }
    // Busca mientras se escribe, con una pequeña pausa
    LaunchedEffect(query) {
        delay(250)
        onSearch(query)
    }
    Surface(tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    suffix = { if (query.isNotEmpty()) Text("${found.first}/${found.second}") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onNext() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onPrev, enabled = found.second > 0) { Icon(Icons.Default.KeyboardArrowUp, "Anterior") }
                IconButton(onClick = onNext, enabled = found.second > 0) { Icon(Icons.Default.KeyboardArrowDown, "Siguiente") }
                IconToggleButton(checked = replacing, onCheckedChange = { replacing = it }) {
                    Icon(Icons.Default.FindReplace, "Reemplazar")
                }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar búsqueda") }
            }
            if (replacing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = replacement,
                        onValueChange = { replacement = it },
                        placeholder = { Text("Reemplazar por") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onReplace(replacement) }, enabled = found.second > 0) { Text("Reemplazar") }
                    TextButton(onClick = { onReplaceAll(replacement) }, enabled = found.second > 0) { Text("Todo") }
                }
            }
        }
    }
}

@Composable
private fun WordCountDialog(raw: String, onDismiss: () -> Unit) {
    val stats = remember(raw) { runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() }
    fun value(key: String) = stats?.get(key)?.jsonPrimitive?.intOrNull ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contar palabras") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Palabras" to value("words"),
                    "Caracteres (sin espacios)" to value("charsNoSpaces"),
                    "Caracteres (con espacios)" to value("chars"),
                    "Párrafos" to value("paragraphs"),
                ).forEach { (label, n) ->
                    Row {
                        Text(label, Modifier.weight(1f))
                        Text("%,d".format(Locale.forLanguageTag("es"), n), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun RibbonTabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            color = color,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
        )
        Box(
            Modifier
                .width(28.dp)
                .height(3.dp)
                .background(if (selected) color else Color.Transparent, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
        )
    }
}

/** Inicio: fuente, formato del texto, colores y copiar formato. */
@Composable
private fun HomeTools(
    format: EditorState,
    quickHighlight: Int,
    recentHighlights: List<Int>,
    editor: WebEditor,
    onHighlight: (Int?) -> Unit,
    onPick: (ColorKind) -> Unit,
) {
    FontMenu(format.font) { editor.call("editor.cmd", "fontName", it) }
    SizeMenu(format.size) { editor.call("editor.fontSize", it) }
    IconButton(onClick = { editor.call("editor.fontSize", nextSize(format.size, up = true)) }) {
        Icon(Icons.Default.TextIncrease, "Agrandar fuente")
    }
    IconButton(onClick = { editor.call("editor.fontSize", nextSize(format.size, up = false)) }) {
        Icon(Icons.Default.TextDecrease, "Achicar fuente")
    }
    ToolbarDivider()

    StyleToggle(Icons.Default.FormatBold, "Negrita", format.bold) { editor.call("editor.cmd", "bold") }
    StyleToggle(Icons.Default.FormatItalic, "Cursiva", format.italic) { editor.call("editor.cmd", "italic") }
    StyleToggle(Icons.Default.FormatUnderlined, "Subrayado", format.underline) { editor.call("editor.cmd", "underline") }
    StyleToggle(Icons.Default.FormatStrikethrough, "Tachado", format.strike) { editor.call("editor.cmd", "strikeThrough") }
    StyleToggle(Icons.Default.Superscript, "Superíndice", format.sup) { editor.call("editor.cmd", "superscript") }
    StyleToggle(Icons.Default.Subscript, "Subíndice", format.sub) { editor.call("editor.cmd", "subscript") }
    CaseMenu { editor.call("editor.changeCase", it) }
    ToolbarDivider()

    // Color de texto: barra inferior con el color actual
    IconButton(onClick = { onPick(ColorKind.TEXT) }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.FormatColorText, "Color de texto")
            Box(
                Modifier
                    .width(20.dp)
                    .height(4.dp)
                    .background(parseCssColor(format.color)?.let(::Color) ?: MaterialTheme.colorScheme.onSurface)
            )
        }
    }
    // Resaltado: el marcador aplica el último color usado; la paleta abre el selector
    IconButton(onClick = { onHighlight(quickHighlight) }) {
        Icon(Icons.Default.BorderColor, "Resaltar", tint = Color(quickHighlight))
    }
    IconButton(onClick = { onPick(ColorKind.HIGHLIGHT) }) {
        Icon(Icons.Default.Palette, "Elegir color de resaltado")
    }
    recentHighlights.drop(1).take(5).forEach { color ->
        Box(Modifier.padding(horizontal = 4.dp)) {
            Swatch(color, selected = parseCssColor(format.back) == color, size = 26) { onHighlight(color) }
        }
    }
    IconButton(onClick = { onHighlight(null) }) {
        Icon(Icons.Default.FormatColorReset, "Quitar resaltado")
    }
    ToolbarDivider()

    // Brocha: primer toque copia el formato; tras seleccionar otro texto, el segundo lo aplica
    StyleToggle(Icons.Default.FormatPaint, if (format.painter) "Aplicar formato copiado" else "Copiar formato", format.painter) {
        editor.call("editor.painter")
    }
    IconButton(onClick = { editor.call("editor.clear") }) {
        Icon(Icons.Default.FormatClear, "Quitar formato")
    }
}

/** Insertar: imagen, tabla, enlace, saltos, línea, símbolos y fecha. */
@Composable
private fun InsertTools(
    editor: WebEditor,
    onInsertImage: (() -> Unit)?,
    onHeaderFooter: ((String) -> Unit)? = null,
    /** Editando el encabezado o pie: sin saltos de página ni volver a abrirlos. */
    inHeader: Boolean = false,
) {
    var tableDialog by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }

    if (onInsertImage != null) RibbonButton(Icons.Default.Image, "Imagen", onInsertImage)
    RibbonButton(Icons.Default.TableChart, "Tabla") { tableDialog = true }
    RibbonButton(Icons.Default.Link, "Enlace") { linkDialog = true }
    ToolbarDivider()
    if (!inHeader) RibbonButton(Icons.Default.InsertPageBreak, "Salto de página") { editor.call("editor.insertPageBreak") }
    RibbonButton(Icons.Default.HorizontalRule, "Línea") { editor.call("editor.insertRule") }
    ToolbarDivider()
    SymbolMenu { editor.call("editor.insertText", it) }
    DateMenu { editor.call("editor.insertText", it) }
    if (onHeaderFooter != null && !inHeader) {
        ToolbarDivider()
        RibbonButton(Icons.Default.VerticalAlignTop, "Encabezado") { onHeaderFooter("h") }
        RibbonButton(Icons.Default.VerticalAlignBottom, "Pie de página") { onHeaderFooter("f") }
        RibbonButton(Icons.Default.Numbers, "Nº de página") { onHeaderFooter("n") }
    }

    if (tableDialog) TableDialog(onDismiss = { tableDialog = false }) { rows, cols ->
        tableDialog = false
        editor.call("editor.insertTable", rows, cols)
    }
    if (linkDialog) LinkDialog(onDismiss = { linkDialog = false }) { url, text ->
        linkDialog = false
        editor.call("editor.insertLink", url, text)
    }
}

/** Botón con icono y nombre debajo, como en la cinta de Word. */
@Composable
private fun RibbonButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    RibbonButton(icon, label, selected = false, onClick = onClick)
}

/** Botón de la cinta que puede quedar marcado. */
@Composable
private fun RibbonButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val tint = if (selected) c.onPrimaryContainer else LocalContentColor.current
    Column(
        Modifier
            .clickable(onClick = onClick)
            .background(if (selected) c.primaryContainer else Color.Transparent, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = tint, fontSize = 11.sp, maxLines = 1)
    }
}

private val SYMBOLS = listOf(
    "©", "®", "™", "°", "±", "×", "÷", "≈", "≠", "≤", "≥", "∞",
    "√", "∑", "π", "µ", "α", "β", "γ", "Δ", "θ", "λ", "σ", "Ω",
    "€", "£", "$", "¢", "§", "¶", "•", "…", "—", "«", "»", "¿",
    "→", "←", "↑", "↓", "⇒", "⇔", "✓", "✗", "★", "♥", "½", "¼",
)

@Composable
private fun SymbolMenu(onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RibbonButton(Icons.Default.Functions, "Símbolo") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SYMBOLS.chunked(6).forEach { row ->
                Row {
                    row.forEach { s ->
                        Box(
                            Modifier
                                .size(44.dp)
                                .clickable { open = false; onSelect(s) },
                            contentAlignment = Alignment.Center,
                        ) { Text(s, fontSize = 20.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateMenu(onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RibbonButton(Icons.Default.Event, "Fecha") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val now = LocalDateTime.now()
            val es = Locale.forLanguageTag("es")
            listOf("dd/MM/yyyy", "d 'de' MMMM 'de' yyyy", "EEEE, d 'de' MMMM 'de' yyyy", "dd/MM/yyyy HH:mm", "HH:mm")
                .map { now.format(DateTimeFormatter.ofPattern(it, es)) }
                .forEach { text -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(text) }) }
        }
    }
}

@Composable
private fun TableDialog(onDismiss: () -> Unit, onInsert: (Int, Int) -> Unit) {
    var rows by remember { mutableIntStateOf(3) }
    var cols by remember { mutableIntStateOf(3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insertar tabla") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Stepper("Filas", rows, 1..50) { rows = it }
                Stepper("Columnas", cols, 1..12) { cols = it }
            }
        },
        confirmButton = { TextButton(onClick = { onInsert(rows, cols) }) { Text("Insertar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) {
            Icon(Icons.Default.Remove, "Menos")
        }
        Text("$value", fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Default.Add, "Más")
        }
    }
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onInsert: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insertar enlace") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Dirección") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Texto (opcional)") },
                    supportingText = { Text("Si lo dejas vacío se enlaza el texto seleccionado") },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onInsert(url.trim(), text.trim()) }, enabled = url.isNotBlank()) { Text("Insertar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Párrafo: estilo, alineación, listas y sangría. */
@Composable
private fun ParagraphTools(format: EditorState, editor: WebEditor) {
    BlockMenu(format.block) { editor.call("editor.block", it) }
    ToolbarDivider()
    StyleToggle(Icons.AutoMirrored.Filled.FormatAlignLeft, "Alinear a la izquierda", format.align == "left") {
        editor.call("editor.cmd", "justifyLeft")
    }
    StyleToggle(Icons.Default.FormatAlignCenter, "Centrar", format.align == "center") {
        editor.call("editor.cmd", "justifyCenter")
    }
    StyleToggle(Icons.AutoMirrored.Filled.FormatAlignRight, "Alinear a la derecha", format.align == "right") {
        editor.call("editor.cmd", "justifyRight")
    }
    StyleToggle(Icons.Default.FormatAlignJustify, "Justificar", format.align == "justify") {
        editor.call("editor.cmd", "justifyFull")
    }
    ToolbarDivider()
    ListMenu(
        Icons.AutoMirrored.Filled.FormatListBulleted, "Viñetas", format.ul,
        listOf("disc" to "•  Círculo", "circle" to "◦  Círculo vacío", "square" to "▪  Cuadrado"),
        onRemove = { editor.call("editor.cmd", "insertUnorderedList") },
    ) { editor.call("editor.list", "ul", it) }
    ListMenu(
        Icons.Default.FormatListNumbered, "Numeración", format.ol,
        listOf(
            "decimal" to "1.  2.  3.",
            "lower-alpha" to "a.  b.  c.",
            "upper-alpha" to "A.  B.  C.",
            "lower-roman" to "i.  ii.  iii.",
            "upper-roman" to "I.  II.  III.",
        ),
        onRemove = { editor.call("editor.cmd", "insertOrderedList") },
    ) { editor.call("editor.list", "ol", it) }
    IconButton(onClick = { editor.call("editor.indent", -1) }) {
        Icon(Icons.AutoMirrored.Filled.FormatIndentDecrease, "Reducir sangría")
    }
    IconButton(onClick = { editor.call("editor.indent", 1) }) {
        Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, "Aumentar sangría")
    }
    ToolbarDivider()
    ValueMenu(Icons.Default.FormatLineSpacing, "Interlineado", listOf(1.0, 1.15, 1.5, 2.0, 2.5, 3.0).map { it to it.toString() }) {
        editor.call("editor.lineSpacing", it)
    }
    ValueMenu(Icons.Default.VerticalAlignTop, "Antes", SPACINGS) { editor.call("editor.spacing", "before", it) }
    ValueMenu(Icons.Default.VerticalAlignBottom, "Después", SPACINGS) { editor.call("editor.spacing", "after", it) }
}

private val SPACINGS = listOf(0, 6, 12, 18, 24).map { it to "$it pt" }

/** Botón de lista: un toque la pone o la quita; la flecha elige el tipo de viñeta o número. */
@Composable
private fun ListMenu(
    icon: ImageVector,
    label: String,
    active: Boolean,
    styles: List<Pair<String, String>>,
    onRemove: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StyleToggle(icon, label, active) { if (active) onRemove() else onSelect(styles.first().first) }
            Icon(
                Icons.Default.ArrowDropDown,
                "Tipos de $label",
                Modifier
                    .size(20.dp)
                    .clickable { open = true },
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            styles.forEach { (style, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(style) })
            }
        }
    }
}

@Composable
private fun <T> ValueMenu(icon: ImageVector, label: String, values: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        RibbonButton(icon, label) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            values.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(value) })
            }
        }
    }
}

/** Siguiente tamaño de la lista hacia arriba o hacia abajo (como Agrandar/Encoger fuente de Word). */
private fun nextSize(current: Float, up: Boolean): Int =
    if (up) SIZES.firstOrNull { it > current + 0.1f } ?: SIZES.last()
    else SIZES.lastOrNull { it < current - 0.1f } ?: SIZES.first()

@Composable
private fun CaseMenu(onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("Aa", fontWeight = FontWeight.Bold) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                "sentence" to "Tipo oración.",
                "lower" to "minúsculas",
                "upper" to "MAYÚSCULAS",
                "title" to "Poner En Mayúsculas Cada Palabra",
                "toggle" to "tIPO iNVERSO",
            ).forEach { (mode, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onSelect(mode) })
            }
        }
    }
}

/** En modo lectura, al seleccionar texto aparece esta barra para resaltar rápido. */
@Composable
internal fun HighlightBar(colors: List<Int>, onHighlight: (Int?) -> Unit, onPick: () -> Unit, onRemove: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.BorderColor, null, tint = MaterialTheme.colorScheme.primary)
            colors.forEach { Swatch(it, selected = false, size = 30) { onHighlight(it) } }
            IconButton(onClick = onPick) { Icon(Icons.Default.Palette, "Elegir color") }
            IconButton(onClick = onRemove) { Icon(Icons.Default.FormatColorReset, "Quitar resaltado") }
        }
    }
}

@Composable
private fun StyleToggle(icon: ImageVector, label: String, checked: Boolean, onClick: () -> Unit) {
    IconToggleButton(checked = checked, onCheckedChange = { onClick() }) {
        Box(
            Modifier
                .size(36.dp)
                .background(
                    if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label) }
    }
}

@Composable
private fun ToolbarDivider() {
    VerticalDivider(Modifier.height(28.dp).padding(horizontal = 4.dp))
}

@Composable
private fun BlockMenu(block: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val styles = listOf(
        "p" to "Normal", "h1" to "Título 1", "h2" to "Título 2", "h3" to "Título 3",
        "blockquote" to "Cita", "pre" to "Código",
    )
    val label = styles.firstOrNull { it.first == block }?.second ?: "Normal"
    Box {
        TextButton(onClick = { open = true }) {
            Text(label)
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            styles.forEach { (tag, text) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text,
                            fontSize = when (tag) { "h1" -> 24.sp; "h2" -> 20.sp; "h3" -> 18.sp; else -> 16.sp },
                            fontWeight = if (tag.startsWith("h")) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (tag == "blockquote") FontStyle.Italic else FontStyle.Normal,
                            fontFamily = if (tag == "pre") FontFamily.Monospace else null,
                        )
                    },
                    onClick = { open = false; onSelect(tag) },
                )
            }
        }
    }
}

@Composable
private fun FontMenu(current: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val currentName = current.substringBefore(',').trim().trim('"', '\'')
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.FontDownload, "Tipo de fuente") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FONTS.forEach { (family, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            fontFamily = FontFamily(AndroidTypeface.create(family, AndroidTypeface.NORMAL)),
                            fontWeight = if (family == currentName) FontWeight.Bold else null,
                        )
                    },
                    onClick = { open = false; onSelect(family) },
                )
            }
        }
    }
}

@Composable
private fun SizeMenu(current: Float, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = if (current % 1f == 0f) current.toInt().toString() else current.toString()
    Box {
        TextButton(onClick = { open = true }) { Text("$label pt") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SIZES.forEach { size ->
                DropdownMenuItem(
                    text = { Text("$size", fontWeight = if (size.toFloat() == current) FontWeight.Bold else null) },
                    onClick = { open = false; onSelect(size) },
                )
            }
        }
    }
}

private val ALIGN_CHOICES = listOf("left" to "Izquierda", "center" to "Centro", "right" to "Derecha")

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

/** Numeraciones de página de Word. */
private val PAGE_FORMATS = listOf("1" to "1, 2, 3…", "i" to "i, ii, iii…", "I" to "I, II, III…", "a" to "a, b, c…", "A" to "A, B, C…")

/** Lo que se eligió en el cuadro de número de página. where: "h", "f", "c" (cursor), "" (quitar) o null (no moverlo). */
private class PageNumberChoice(val where: String?, val align: String, val fmt: String, val numbering: String, val start: Int)

/** Número de página: dónde va, alineación, texto, numeración y desde qué número empieza. */
@Composable
private fun PageNumberDialog(
    current: Pair<String, PageNumber>?,
    numbering: String,
    start: Int,
    onDismiss: () -> Unit,
    onApply: (PageNumberChoice) -> Unit,
) {
    // Con un número ya puesto, por defecto solo se cambia la numeración
    var where by remember { mutableStateOf(if (current != null) "keep" else "f") }
    var align by remember { mutableStateOf(current?.second?.align?.takeIf { a -> ALIGN_CHOICES.any { it.first == a } } ?: "center") }
    var fmt by remember { mutableStateOf(current?.second?.fmt ?: "n") }
    var pf by remember { mutableStateOf(numbering) }
    var first by remember { mutableIntStateOf(start) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Número de página") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Posición", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (current != null) OptionRow("Dejarlo donde está", where == "keep") { where = "keep" }
                OptionRow("Arriba (encabezado)", where == "h") { where = "h" }
                OptionRow("Abajo (pie de página)", where == "f") { where = "f" }
                OptionRow("Donde está el cursor", where == "c") { where = "c" }
                if (where == "h" || where == "f") {
                    Text("Alineación", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    Row {
                        ALIGN_CHOICES.forEach { (value, label) ->
                            Row(Modifier.clickable { align = value }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = align == value, onClick = { align = value })
                                Text(label, fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
                            }
                        }
                    }
                }
                if (where != "keep") {
                    Text("Texto", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    OptionRow("Solo el número", fmt == "n") { fmt = "n" }
                    OptionRow("Página 1 de N", fmt == "nofm") { fmt = "nofm" }
                }
                Text("Numeración", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                PAGE_FORMATS.forEach { (value, label) -> OptionRow(label, pf == value) { pf = value } }
                Stepper("Empezar en", first, 0..999) { first = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(PageNumberChoice(where.takeIf { it != "keep" }, align, fmt, pf, first)) }) { Text("Aceptar") }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onApply(PageNumberChoice("", align, fmt, pf, first)) }) { Text("Quitar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

private const val CM = 28.3465f

/** Distancia del encabezado y del pie al borde de la hoja. */
@Composable
private fun DistanceDialog(top: Float, bottom: Float, onDismiss: () -> Unit, onApply: (Float, Float) -> Unit) {
    var h by remember { mutableFloatStateOf(top / CM) }
    var f by remember { mutableFloatStateOf(bottom / CM) }
    val es = Locale.forLanguageTag("es")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Distancia al borde") },
        text = {
            Column {
                Text("Encabezado desde arriba: ${"%.1f".format(es, h)} cm", fontSize = 13.sp)
                Slider(value = h, onValueChange = { h = (it * 10).roundToInt() / 10f }, valueRange = 0f..5f)
                Text("Pie desde abajo: ${"%.1f".format(es, f)} cm", fontSize = 13.sp)
                Slider(value = f, onValueChange = { f = (it * 10).roundToInt() / 10f }, valueRange = 0f..5f)
            }
        },
        confirmButton = { TextButton(onClick = { onApply(h * CM, f * CM) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Encabezado y pie (mientras se edita uno en la hoja): diseños, número de página, fecha, opciones, distancia, cambiar y cerrar. */
@Composable
private fun HeaderTools(format: EditorState, editor: WebEditor, title: String, onPageNumber: () -> Unit, onLogo: (() -> Unit)?) {
    val header = format.hf == "h"
    var distance by remember { mutableStateOf(false) }
    fun preset(name: String) = editor.call("headerFooter.preset", name, title, null)
    ChoiceMenu(
        Icons.Default.Dashboard, "Diseños",
        listOfNotNull(
            Choice("En blanco", "Borra lo que tiene", false) { preset("blank") },
            Choice("Tres columnas", "Izquierda, centro y derecha", false) { preset("three") },
            Choice("Título del documento", title.ifBlank { null }, false) { preset("title") },
            onLogo?.let { Choice("Logo y título", "Elige una imagen", false, it) },
        ),
    )
    RibbonButton(Icons.Default.Numbers, "Nº de página", onPageNumber)
    DateMenu { editor.call("editor.insertText", it) }
    ToolbarDivider()
    OptionCheck("Primera página diferente", format.hfFirst) { editor.call("headerFooter.options", it, format.hfOddEven) }
    OptionCheck("Pares e impares diferentes", format.hfOddEven) { editor.call("headerFooter.options", format.hfFirst, it) }
    RibbonButton(Icons.Default.Height, "Distancia") { distance = true }
    ToolbarDivider()
    RibbonButton(
        if (header) Icons.Default.VerticalAlignBottom else Icons.Default.VerticalAlignTop,
        if (header) "Ir al pie" else "Ir al encabezado",
    ) { editor.call("headerFooter.swap") }
    RibbonButton(Icons.Default.Close, "Cerrar") { editor.call("headerFooter.exit") }

    if (distance) DistanceDialog(format.hfTop, format.hfBottom, onDismiss = { distance = false }) { h, f ->
        distance = false
        editor.call("headerFooter.distance", h, f)
    }
}

@Composable
private fun OptionCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .clickable { onChange(!checked) }
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, fontSize = 12.sp, maxLines = 1)
    }
}
