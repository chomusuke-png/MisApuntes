package com.estudia.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DocType(val label: String) {
    PDF("PDF"),
    DOCX("DOCX"),
    DOC("DOC"),
    TXT("TXT"),
    MD("MD"),
    NOTE("Nota");

    /** Los PDF se muestran como páginas; el resto se convierte a texto enriquecido editable. */
    val isEditable: Boolean get() = this != PDF

    companion object {
        fun fromFileName(name: String, mime: String?): DocType? {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when {
                ext == "pdf" || mime == "application/pdf" -> PDF
                ext == "docx" || mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DOCX
                ext == "doc" || mime == "application/msword" -> DOC
                ext == "md" || ext == "markdown" || mime == "text/markdown" -> MD
                ext == "txt" || mime?.startsWith("text/") == true -> TXT
                else -> null
            }
        }
    }
}

@Entity(tableName = "folders", indices = [Index("parentId")])
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "documents", indices = [Index("folderId"), Index("pinned")])
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: DocType,
    val folderId: Long? = null,
    /** Copia interna del archivo original (relativa a filesDir). */
    val originalPath: String? = null,
    /** Contenido enriquecido en JSON (relativo a filesDir). Null para PDF. */
    val contentPath: String? = null,
    val pinned: Boolean = false,
    val pinnedAt: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = 0,
)

enum class ColorKind { TEXT, HIGHLIGHT, NOTE }

@Entity(tableName = "recent_colors", primaryKeys = ["kind", "color"])
data class RecentColorEntity(
    val kind: ColorKind,
    val color: Int,
    val usedAt: Long = System.currentTimeMillis(),
)

/**
 * Nota adhesiva ("post-it") anclada a un punto del documento.
 * - PDF: [page] y [x]/[y] normalizados (0..1) dentro de la página.
 * - Documentos HTML: [block] es el atributo data-anchor del párrafo, [x] la fracción del ancho
 *   de la hoja e [y] la fracción dentro del párrafo. [docY] es la altura relativa en el documento,
 *   de respaldo si el párrafo se borra.
 */
@Entity(tableName = "sticky_notes", indices = [Index("docId")])
data class StickyNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val docId: Long,
    val title: String = "",
    val icon: String,
    val color: Int,
    val page: Int = -1,
    val x: Float,
    val y: Float,
    val block: String? = null,
    val docY: Float = 0f,
    /** Tamaño del pin, en dp. */
    val size: Int = 30,
    /** Texto sin formato del cuerpo, para buscar en el índice. */
    val plainText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
