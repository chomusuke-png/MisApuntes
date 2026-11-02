package com.estudia.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import android.provider.OpenableColumns
import com.estudia.app.importers.DocImporter
import com.estudia.app.importers.DocxImporter
import com.estudia.app.importers.MarkdownImporter
import com.estudia.app.importers.PdfExporter
import com.estudia.app.importers.PdfTextExtractor
import com.estudia.app.importers.PlainTextImporter
import com.estudia.app.rich.RichDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Texto visible de un HTML (para buscar). Quita etiquetas y decodifica las entidades básicas. */
fun htmlToPlainText(html: String): String =
    html.replace(Regex("<(br|/p|/h\\d|/li|/td)[^>]*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\n\\s*\n+"), "\n")
        .trim()

/** Contenido de un documento editable: HTML y la carpeta donde están sus imágenes. */
class EditorContent(val html: String, val baseDir: File)

/**
 * Trazo de resaltador sobre una página de PDF. Las coordenadas van de 0 a 1 respecto
 * al ancho/alto de la página, así no dependen del zoom. points = x0, y0, x1, y1…
 */
@Serializable
data class PdfStroke(
    val page: Int,
    val color: Int,
    val width: Float,
    val points: List<Float>,
    val straight: Boolean = false,
    /** Opacidad del marcador (0..1). Los trazos anteriores a esta opción son opacos. */
    val alpha: Float = 1f,
)

class Repository(private val context: Context, private val db: AppDatabase) {

    val folders = db.folders()
    val documents = db.documents()
    val recentColors = db.recentColors()
    val stickyNotes = db.stickyNotes()

    /** Para guardados que deben terminar aunque se cierre la pantalla. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val originalsDir get() = File(context.filesDir, "originals").apply { mkdirs() }
    private val annotationsDir get() = File(context.filesDir, "annotations").apply { mkdirs() }
    private fun contentDir(id: Long) = File(context.filesDir, "content/$id")

    fun file(relativePath: String) = File(context.filesDir, relativePath)

    /** Copia el archivo dentro de la app (así el acceso no se pierde) y lo convierte si es editable. */
    suspend fun importUri(uri: Uri, folderId: Long?): Long = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Documento"
        val mime = resolver.getType(uri)
        val type = DocType.fromFileName(name, mime)
            ?: throw IllegalArgumentException("Formato no soportado: $name")

        val original = File(originalsDir, "${UUID.randomUUID()}.${type.name.lowercase()}")
        resolver.openInputStream(uri)?.use { input -> original.outputStream().use { input.copyTo(it) } }
            ?: throw IllegalStateException("No se pudo leer el archivo")

        // Se convierte en una carpeta temporal: el id del documento aún no existe
        val tmp = File(context.filesDir, "content/tmp-${UUID.randomUUID()}").apply { mkdirs() }
        val html = try {
            convert(type, original, tmp)
        } catch (e: Exception) {
            original.delete()
            tmp.deleteRecursively()
            throw e
        }

        val title = name.substringBeforeLast('.').ifBlank { name }
        val id = documents.insert(
            DocumentEntity(
                title = title,
                type = type,
                folderId = folderId,
                originalPath = original.relativeTo(context.filesDir).path,
            )
        )
        if (html != null) {
            val dir = contentDir(id)
            dir.deleteRecursively()
            if (!tmp.renameTo(dir)) {
                tmp.copyRecursively(dir, overwrite = true)
                tmp.deleteRecursively()
            }
            writeHtml(id, html)
        } else {
            tmp.deleteRecursively()
        }
        id
    }

    private fun convert(type: DocType, file: File, dir: File): String? = when (type) {
        DocType.PDF -> null
        DocType.DOCX -> DocxImporter.import(file, dir)
        DocType.DOC -> DocImporter.import(file)
        DocType.MD -> MarkdownImporter.import(file).toHtml()
        DocType.TXT -> PlainTextImporter.import(file).toHtml()
        DocType.NOTE -> RichDocument().toHtml()
    }

    suspend fun createNote(title: String, folderId: Long?, html: String = RichDocument().toHtml()): Long =
        withContext(Dispatchers.IO) {
            val id = documents.insert(DocumentEntity(title = title, type = DocType.NOTE, folderId = folderId))
            writeHtml(id, html)
            id
        }

    suspend fun createNoteFromPdf(doc: DocumentEntity): Long = withContext(Dispatchers.IO) {
        val path = doc.originalPath ?: error("Archivo no encontrado")
        val rich = PdfTextExtractor.extract(context, file(path))
        createNote("${doc.title} (texto)", doc.folderId, rich.toHtml())
    }

    suspend fun loadContent(doc: DocumentEntity): EditorContent = withContext(Dispatchers.IO) {
        val dir = contentDir(doc.id).apply { mkdirs() }
        var stored = doc.contentPath?.let(::file)?.takeIf { it.exists() }
        // Los Word importados con la versión anterior se reconvierten con el importador completo
        if (stored != null && stored.name.endsWith(".json") && (doc.type == DocType.DOCX || doc.type == DocType.DOC) &&
            doc.originalPath?.let(::file)?.exists() == true
        ) {
            stored.delete()
            stored = null
        }
        val html = when {
            // Notas guardadas con la primera versión de la app (texto + rangos en JSON)
            stored != null && stored.name.endsWith(".json") -> {
                RichDocument.fromJson(stored.readText()).toHtml().also {
                    writeHtml(doc.id, it)
                    stored.delete()
                }
            }
            stored != null -> stored.readText()
            else -> {
                // Contenido perdido o de la versión anterior: se vuelve a convertir desde el original
                val original = doc.originalPath?.let(::file)?.takeIf { it.exists() }
                (original?.let { convert(doc.type, it, dir) } ?: RichDocument().toHtml())
                    .also { writeHtml(doc.id, it) }
            }
        }
        EditorContent(html, dir)
    }

    suspend fun saveHtml(docId: Long, html: String) = withContext(Dispatchers.IO) {
        writeHtml(docId, html)
        documents.touchUpdated(docId)
    }

    private suspend fun writeHtml(id: Long, html: String) = writeLock.withLock {
        val dir = contentDir(id).apply { mkdirs() }
        val target = File(dir, "doc.html")
        // Escritura atómica: si la app se cierra a mitad no se corrompe el archivo
        val tmp = File(dir, "doc.html.tmp")
        tmp.writeText(html)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
        documents.setContentPath(id, target.relativeTo(context.filesDir).path)
    }

    // ---------- Resaltados de PDF ----------

    private val strokesSerializer = ListSerializer(PdfStroke.serializer())

    suspend fun loadStrokes(docId: Long): List<PdfStroke> = withContext(Dispatchers.IO) {
        val f = File(annotationsDir, "$docId.json")
        if (f.exists()) runCatching { json.decodeFromString(strokesSerializer, f.readText()) }.getOrDefault(emptyList())
        else emptyList()
    }

    suspend fun saveStrokes(docId: Long, strokes: List<PdfStroke>) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val tmp = File(annotationsDir, "$docId.json.tmp")
            tmp.writeText(json.encodeToString(strokesSerializer, strokes))
            val target = File(annotationsDir, "$docId.json")
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        }
    }

    /** Escribe una copia del PDF con los resaltados incrustados (visibles en cualquier lector). */
    suspend fun exportPdf(doc: DocumentEntity, strokes: List<PdfStroke>, target: Uri) = withContext(Dispatchers.IO) {
        val path = doc.originalPath ?: error("Archivo no encontrado")
        context.contentResolver.openOutputStream(target)?.use { out ->
            PdfExporter.export(context, file(path), strokes, out)
        } ?: error("No se pudo escribir el archivo")
    }

    // ---------- Notas adhesivas ----------

    private fun noteDir(id: Long) = File(context.filesDir, "notes/$id")

    suspend fun createStickyNote(note: StickyNoteEntity): Long = withContext(Dispatchers.IO) {
        val id = stickyNotes.insert(note)
        writeNoteHtml(id, RichDocument().toHtml())
        id
    }

    suspend fun loadNoteBody(id: Long): EditorContent = withContext(Dispatchers.IO) {
        val dir = noteDir(id).apply { mkdirs() }
        val f = File(dir, "note.html")
        EditorContent(if (f.exists()) f.readText() else RichDocument().toHtml(), dir)
    }

    suspend fun saveNoteBody(id: Long, html: String) = withContext(Dispatchers.IO) {
        writeNoteHtml(id, html)
        stickyNotes.setPlainText(id, htmlToPlainText(html))
    }

    private suspend fun writeNoteHtml(id: Long, html: String) = writeLock.withLock {
        val dir = noteDir(id).apply { mkdirs() }
        val tmp = File(dir, "note.html.tmp")
        tmp.writeText(html)
        val target = File(dir, "note.html")
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    /** Copia una imagen a la carpeta de la nota, reducida a 1600 px como máximo. Devuelve su nombre. */
    suspend fun importNoteImage(noteId: Long, source: Uri): String = withContext(Dispatchers.IO) {
        val name = "img-${UUID.randomUUID().toString().take(8)}.jpg"
        val target = File(noteDir(noteId).apply { mkdirs() }, name)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val bitmap = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("No se pudo leer la imagen")
        val rotated = rotateByExif(source, bitmap)
        target.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        name
    }

    /** Las fotos de cámara guardan la orientación aparte (EXIF): se aplica para que no salgan giradas. */
    private fun rotateByExif(source: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(source)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Archivo temporal donde la cámara deja la foto (se comparte con un FileProvider). */
    fun newCameraFile(): File = File(context.cacheDir, "camera").apply { mkdirs() }.resolve("foto-${UUID.randomUUID()}.jpg")

    suspend fun deleteStickyNote(id: Long) = withContext(Dispatchers.IO) {
        noteDir(id).deleteRecursively()
        stickyNotes.delete(id)
    }

    suspend fun deleteDocument(doc: DocumentEntity) = withContext(Dispatchers.IO) {
        stickyNotes.allFor(doc.id).forEach { deleteStickyNote(it.id) }
        doc.originalPath?.let { file(it).delete() }
        contentDir(doc.id).deleteRecursively()
        doc.contentPath?.let { file(it).delete() }
        File(annotationsDir, "${doc.id}.json").delete()
        documents.delete(doc.id)
    }

    fun rememberColor(kind: ColorKind, color: Int) {
        appScope.launch {
            recentColors.upsert(RecentColorEntity(kind, color))
            recentColors.trim(kind)
        }
    }
}
