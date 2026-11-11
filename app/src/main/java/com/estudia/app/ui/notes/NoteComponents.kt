package com.estudia.app.ui.notes

import android.graphics.Bitmap
import android.util.Base64
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.estudia.app.EstudiaApp
import com.estudia.app.data.StickyNoteEntity
import com.estudia.app.rich.cssColor
import com.estudia.app.ui.components.ColorPickerDialog
import com.estudia.app.ui.components.ConfirmDialog
import com.estudia.app.ui.components.Swatch
import java.io.ByteArrayOutputStream

/** Íconos disponibles para las notas (clave guardada en la base de datos → ícono). */
/** Tamaños del pin de una nota (dp) y su nombre. */
val PIN_SIZES = listOf(22 to "Pequeño", 30 to "Mediano", 40 to "Grande", 52 to "Muy grande")

val NOTE_ICONS: List<Pair<String, ImageVector>> = listOf(
    "note" to Icons.AutoMirrored.Filled.StickyNote2,
    "star" to Icons.Default.Star,
    "important" to Icons.Default.PriorityHigh,
    "question" to Icons.Default.QuestionMark,
    "idea" to Icons.Default.Lightbulb,
    "warning" to Icons.Default.Warning,
    "info" to Icons.Default.Info,
    "bookmark" to Icons.Default.Bookmark,
    "flag" to Icons.Default.Flag,
    "heart" to Icons.Default.Favorite,
    "check" to Icons.Default.CheckCircle,
    "pin" to Icons.Default.PushPin,
    "comment" to Icons.Default.ChatBubble,
    "study" to Icons.Default.School,
    "date" to Icons.Default.Event,
    "tag" to Icons.Default.LocalOffer,
)

fun noteIcon(key: String): ImageVector = NOTE_ICONS.firstOrNull { it.first == key }?.second ?: NOTE_ICONS.first().second

val NOTE_COLORS = listOf(
    0xFFD25D8C, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835,
    0xFF43A047, 0xFF00897B, 0xFF1E88E5, 0xFF8E24AA,
).map { it.toInt() }

/** Blanco o negro, lo que más contraste haga sobre el color de la nota. */
fun noteTint(color: Int): Color {
    val c = Color(color)
    val luminance = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
    return if (luminance > 0.6f) Color.Black else Color.White
}

/** Marcador redondo con el ícono de la nota, como se ve sobre el documento. */
@Composable
fun NoteBadge(icon: String, color: Int, size: Dp = 30.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .shadow(3.dp, CircleShape)
            .background(Color(color), CircleShape)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(noteIcon(icon), contentDescription = null, tint = noteTint(color), modifier = Modifier.size(size * 0.6f))
    }
}

/** Íconos como PNG en data-URI, para dibujarlos dentro del editor web. Clave → (blanco, negro). */
@Composable
fun rememberNoteIconUris(): Map<String, Pair<String, String>> {
    val painters = NOTE_ICONS.map { (key, vector) -> key to rememberVectorPainter(vector) }
    return remember { painters.associate { (key, p) -> key to (renderIcon(p, Color.White) to renderIcon(p, Color.Black)) } }
}

private fun renderIcon(painter: Painter, tint: Color): String {
    val px = 48
    val image = ImageBitmap(px, px)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(px.toFloat(), px.toFloat())) {
        with(painter) { draw(Size(px.toFloat(), px.toFloat()), colorFilter = ColorFilter.tint(tint)) }
    }
    val out = ByteArrayOutputStream()
    image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
    return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

/** Elegir ícono y color al crear la nota (o al cambiarlos después). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteLookDialog(
    title: String,
    confirmLabel: String,
    initialIcon: String,
    initialColor: Int,
    recentColors: List<Int>,
    initialSize: Int = PIN_SIZES[1].first,
    onDismiss: () -> Unit,
    onConfirm: (icon: String, color: Int, size: Int) -> Unit,
) {
    var icon by remember { mutableStateOf(initialIcon) }
    var color by remember { mutableIntStateOf(initialColor) }
    var size by remember { mutableIntStateOf(initialSize) }
    var picker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NoteBadge(icon, color, 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(title)
            }
        },
        text = {
            // en horizontal no cabe todo: se desplaza en vez de cortarse
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ícono", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NOTE_ICONS.forEach { (key, vector) ->
                        val selected = key == icon
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(vector, key, tint = if (selected) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("Color", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    (recentColors + NOTE_COLORS).distinct().take(12).forEach { c ->
                        Swatch(c, selected = c == color, size = 30) { color = c }
                    }
                    IconButton(onClick = { picker = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Palette, "Otro color")
                    }
                }
                Text("Tamaño del pin", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    PIN_SIZES.forEach { (dp, label) ->
                        val selected = dp == size
                        Column(
                            Modifier
                                .background(
                                    if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { size = dp }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            NoteBadge(icon, color, dp.dp)
                            Text(label, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(icon, color, size) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    if (picker) {
        ColorPickerDialog(
            title = "Color de la nota",
            initial = color,
            recent = recentColors,
            presets = NOTE_COLORS,
            onDismiss = { picker = false },
            onPick = { color = it; picker = false },
        )
    }
}

/** Aviso mientras se elige el lugar de una nota nueva. */
@Composable
fun PlacingBanner(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 6.dp,
        modifier = modifier.padding(12.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.TouchApp, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(10.dp))
            Text(
                "Toca el lugar donde irá la nota",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f, fill = false),
            )
            TextButton(onClick = onCancel) { Text("Cancelar") }
        }
    }
}

/** Tarjeta que se despliega sobre el documento al tocar un marcador. */
@Composable
fun NotePopup(
    note: StickyNoteEntity,
    darkTheme: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val repo = (LocalContext.current.applicationContext as EstudiaApp).repository
    val body by produceState<com.estudia.app.data.EditorContent?>(null, note.id, note.updatedAt) {
        value = repo.loadNoteBody(note.id)
    }
    var confirmDelete by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 520.dp)
                .clickable(enabled = false) {},
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(note.color).copy(alpha = 0.18f))
                        .padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NoteBadge(note.icon, note.color, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        note.title.ifBlank { "Sin título" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar") }
                }
                Box(Modifier.weight(1f, fill = false).padding(horizontal = 8.dp)) {
                    val content = body
                    if (content != null && (note.plainText.isNotBlank() || content.html.contains("<img"))) {
                        NoteBodyView(content.html, "file://${content.baseDir.absolutePath}/", darkTheme)
                    } else {
                        Text(
                            "Nota vacía. Toca Editar para escribir.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Eliminar")
                    }
                    TextButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Editar")
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Eliminar nota",
            message = "Se eliminará \"${note.title.ifBlank { "Sin título" }}\" y su contenido.",
            confirmLabel = "Eliminar",
            onDismiss = { confirmDelete = false },
        ) { confirmDelete = false; onDelete() }
    }
}

/** Contenido de la nota en solo lectura (texto con formato e imágenes). */
@Composable
private fun NoteBodyView(html: String, baseDir: String, darkTheme: Boolean) {
    val context = LocalContext.current
    val css = remember { context.assets.open("editor/editor.css").bufferedReader().use { it.readText() } }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val page = remember(html, darkTheme) {
        """<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
           <style>$css body.viewer, body.viewer #page { background: transparent !important; color: ${cssColor(textColor)}; }
           body.viewer #page { min-height: 0 !important; padding: 8px 6px 12px !important; }</style></head>
           <body class="mobile viewer${if (darkTheme) " dark" else ""}">$html</body></html>"""
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 400.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.allowFileAccess = true
                isVerticalScrollBarEnabled = true
            }
        },
        update = { it.loadDataWithBaseURL(baseDir, page, "text/html", "utf-8", null) },
    )
}

/** Índice de notas del documento, con búsqueda. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesIndexSheet(
    notes: List<StickyNoteEntity>,
    isPdf: Boolean,
    onJump: (StickyNoteEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(notes, query) {
        val q = query.trim()
        if (q.isEmpty()) notes
        else notes.filter { it.title.contains(q, ignoreCase = true) || it.plainText.contains(q, ignoreCase = true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "Notas (${notes.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (notes.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar en las notas") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when {
                notes.isEmpty() -> Text(
                    "Aún no hay notas. Toca \"Nota\" y luego el lugar del documento donde quieres dejarla.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
                filtered.isEmpty() -> Text(
                    "Sin resultados",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
                else -> LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(filtered, key = { it.id }) { note ->
                        ListItem(
                            leadingContent = { NoteBadge(note.icon, note.color, 34.dp) },
                            headlineContent = {
                                Text(note.title.ifBlank { "Sin título" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                val place = if (isPdf) "Página ${note.page + 1}" else null
                                val snippet = note.plainText.replace('\n', ' ').take(90)
                                Text(
                                    listOfNotNull(place, snippet.ifBlank { null }).joinToString(" · "),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.clickable { onJump(note) },
                        )
                    }
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}
