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
import com.estudia.app.importers.WordBridge
import com.estudia.app.rich.RichDocument
import android.util.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
 * Trazo de destacador o lápiz sobre una página de PDF. Las coordenadas van de 0 a 1 respecto
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
    /** Lápiz: tapa lo que hay debajo. Si no, destacador (los trazos anteriores a esta opción). */
    val pen: Boolean = false,
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
        store(title, type, folderId, original, tmp, html)
    }

    /** Registra el documento y mueve lo convertido (HTML e imágenes) a su carpeta definitiva. */
    private suspend fun store(title: String, type: DocType, folderId: Long?, original: File, tmp: File, html: String?): Long {
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
        return id
    }

    /** Documento de Word en blanco (hoja, márgenes y estilos de Word), editable y guardable como .docx. */
    suspend fun createWordDocument(title: String, folderId: Long?): Long = withContext(Dispatchers.IO) {
        check(WordBridge.available) { "Los documentos de Word no están disponibles en este equipo" }
        val original = File(originalsDir, "${UUID.randomUUID()}.docx")
        val tmp = File(context.filesDir, "content/tmp-${UUID.randomUUID()}").apply { mkdirs() }
        val html = try {
            WordBridge.toDocx("", "", "", tmp.path, original.path)
            WordBridge.toHtml(original.path, "docx", tmp.path)
        } catch (e: Exception) {
            original.delete()
            tmp.deleteRecursively()
            throw e
        }
        store(title, DocType.DOCX, folderId, original, tmp, html)
    }

    private fun convert(type: DocType, file: File, dir: File): String? = when (type) {
        DocType.PDF -> null
        DocType.DOCX -> wordHtml(file, "docx", dir) ?: DocxImporter.import(file, dir)
        DocType.DOC -> wordHtml(file, "doc", dir) ?: DocImporter.import(file)
        DocType.MD -> MarkdownImporter.import(file).toHtml()
        DocType.TXT -> PlainTextImporter.import(file).toHtml()
        DocType.NOTE -> RichDocument().toHtml()
    }

    /** Conversión con el núcleo de Word; null si no está disponible o falla (se usa la de Kotlin). */
    private fun wordHtml(file: File, ext: String, dir: File): String? {
        if (!WordBridge.available) return null
        return runCatching { WordBridge.toHtml(file.path, ext, dir.path) }
            .onFailure { Log.w("Repository", "Conversión nativa falló: ${file.name}", it) }
            .getOrNull()
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
        val target = writeAtomic(File(contentDir(id), "doc.html"), html)
        documents.setContentPath(id, target.relativeTo(context.filesDir).path)
    }

    /** Escritura atómica: si la app se cierra a mitad no se corrompe el archivo. */
    private fun writeAtomic(target: File, text: String): File {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
        return target
    }

    // ---------- Guardar como Word ----------

    private fun modelFile(id: Long) = File(contentDir(id), "model.json")

    /** Lo que el editor cambió respecto al original (ver editor.js). */
    suspend fun saveModel(docId: Long, json: String) = withContext(Dispatchers.IO) {
        writeLock.withLock { writeAtomic(modelFile(docId), json) }
    }

    /** Escribe el .docx del documento: el original con lo editado, o uno nuevo para notas y textos. */
    private suspend fun writeDocx(doc: DocumentEntity, model: String?, out: File) = withContext(Dispatchers.IO) {
        check(WordBridge.available) { "Guardar como Word no está disponible en este equipo" }
        val original = doc.originalPath?.let(::file)?.takeIf { it.exists() && (doc.type == DocType.DOCX || doc.type == DocType.DOC) }
        val json = model ?: writeLock.withLock { modelFile(doc.id).takeIf { it.exists() }?.readText() } ?: if (original != null) "" else plainModel(doc)
        out.parentFile?.mkdirs()
        WordBridge.toDocx(original?.path.orEmpty(), doc.type.name.lowercase(), json, contentDir(doc.id).path, out.path)
    }

    /** Modelo mínimo (solo el texto) para un documento que nunca se abrió en el editor. */
    private fun plainModel(doc: DocumentEntity): String {
        val html = doc.contentPath?.let(::file)?.takeIf { it.exists() }?.readText().orEmpty()
        val blocks = htmlToPlainText(html).split('\n').map { line ->
            buildJsonObject {
                put("p", buildJsonObject {
                    put("tag", "p")
                    put("segs", buildJsonArray { add(buildJsonObject { put("t", line) }) })
                })
            }
        }
        return buildJsonObject { put("blocks", JsonArray(blocks)) }.toString()
    }

    /** Copia .docx en la caché, con el nombre del documento, para compartirla. */
    suspend fun docxForSharing(doc: DocumentEntity, model: String? = null): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
        val name = doc.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifBlank { "Documento" }
        File(dir, "$name.docx").also { writeDocx(doc, model, it) }
    }

    suspend fun exportDocx(doc: DocumentEntity, target: Uri, model: String? = null) = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "export-${doc.id}.docx")
        try {
            writeDocx(doc, model, tmp)
            context.contentResolver.openOutputStream(target, "wt")?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                ?: error("No se pudo escribir el archivo")
        } finally {
            tmp.delete()
        }
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
    suspend fun importNoteImage(noteId: Long, source: Uri): String = importImage(noteDir(noteId), source)

    /** Imagen para un documento: queda junto a su HTML, de donde también la toma el guardado en Word. */
    suspend fun importDocumentImage(docId: Long, source: Uri): String = importImage(contentDir(docId), source)

    /** Copia la imagen achicada (máx. 1600 px) y derecha en [dir]; devuelve el nombre del archivo. */
    private suspend fun importImage(dir: File, source: Uri): String = withContext(Dispatchers.IO) {
        val name = "img-${UUID.randomUUID().toString().take(8)}.jpg"
        val target = File(dir.apply { mkdirs() }, name)
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

    /** Gira 90°/180° una imagen de [dir] en un archivo nuevo (el anterior queda para deshacer). */
    fun rotateImage(dir: File, name: String, degrees: Int): String {
        val source = File(dir, File(name).name).takeIf { it.exists() } ?: error("Imagen no encontrada")
        val bitmap = BitmapFactory.decodeFile(source.path) ?: error("No se pudo leer la imagen")
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val png = source.extension.equals("png", ignoreCase = true)
        val target = File(dir, "img-${UUID.randomUUID().toString().take(8)}.${if (png) "png" else "jpg"}")
        target.outputStream().use {
            rotated.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, it)
        }
        return target.name
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
