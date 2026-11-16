package com.estudia.app.speech

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

/** texto listo para leer y, por cada caracter, en que posicion del texto original de la pagina estaba */
class MappedText(val text: String, val map: IntArray)

/** rectangulo en fracciones del ancho y alto de la pagina */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** texto de una pagina de PDF y la caja de cada caracter, 4 numeros por caracter (NaN si es un espacio agregado) */
class PdfPageText(val text: String, val boxes: FloatArray)

/** junta espacios y saltos de linea, y une las palabras cortadas con guion al final de linea */
private fun normalize(raw: String, from: Int, to: Int): MappedText {
    val text = StringBuilder()
    val map = ArrayList<Int>()
    var space = false
    var i = from
    while (i < to) {
        val c = raw[i]
        if (c == '-' && i + 2 < to && raw[i + 1] == '\n' && raw[i + 2].isLowerCase()) {
            i += 2
            continue
        }
        if (c.isWhitespace()) {
            if (text.isNotEmpty()) space = true
            i++
            continue
        }
        if (space) {
            text.append(' ')
            map += i
            space = false
        }
        text.append(c)
        map += i
        i++
    }
    return MappedText(text.toString(), map.toIntArray())
}

/**
 * parte el texto de una pagina de PDF en trozos para leer y saltar de a uno
 * agrupa oraciones hasta unos target caracteres y recuerda de donde salio cada caracter
 */
fun pdfPageReading(raw: String, target: Int = 500): List<MappedText> {
    val segments = mutableListOf<MappedText>()
    var start = 0
    val paragraphs = Regex("\\n\\s*\\n").findAll(raw).map { it.range }.toList()
    for (end in paragraphs.map { it.first } + raw.length) {
        val paragraph = normalize(raw, start, end)
        start = paragraphs.firstOrNull { it.first == end }?.let { it.last + 1 } ?: raw.length
        if (paragraph.text.isEmpty()) continue
        val text = paragraph.text
        var from = 0
        var cut = 0
        fun emit(to: Int) {
            if (to > from) segments += MappedText(text.substring(from, to), paragraph.map.copyOfRange(from, to))
        }
        for (gap in Regex("(?<=[.!?…])\\s+").findAll(text)) {
            if (cut > from && gap.range.first - from > target) {
                emit(cut)
                from = cut + 1
            }
            cut = gap.range.first
        }
        if (cut > from && text.length - from > target) {
            emit(cut)
            from = cut + 1
        }
        emit(text.length)
    }
    return segments
}

fun pdfPageSegments(text: String, target: Int = 500): List<String> = pdfPageReading(text, target).map { it.text }

/**
 * cajas de los caracteres del texto original entre from y to, juntadas en una por renglon
 * para marcar un trozo o una palabra sobre la pagina
 */
fun lineBoxes(boxes: FloatArray, from: Int, to: Int): List<Box> {
    val result = mutableListOf<Box>()
    var current: Box? = null
    for (i in from..to) {
        if (i < 0 || i * 4 + 3 >= boxes.size) continue
        val l = boxes[i * 4]
        if (l.isNaN()) continue
        val b = Box(l, boxes[i * 4 + 1], boxes[i * 4 + 2], boxes[i * 4 + 3])
        val c = current
        val overlap = if (c == null) 0f else minOf(c.bottom, b.bottom) - maxOf(c.top, b.top)
        val height = if (c == null) 0f else minOf(c.bottom - c.top, b.bottom - b.top)
        // mismo renglon si se solapan en alto y sigue hacia la derecha
        if (c != null && overlap >= height * 0.5f && b.left >= c.left) {
            current = Box(c.left, minOf(c.top, b.top), maxOf(c.right, b.right), maxOf(c.bottom, b.bottom))
        } else {
            c?.let(result::add)
            current = b
        }
    }
    current?.let(result::add)
    return result
}

/** saca el texto de la pagina y la posicion de cada caracter */
private class PositionStripper : PDFTextStripper() {
    val text = StringBuilder()
    val boxes = ArrayList<Float>()
    private var width = 1f
    private var height = 1f

    override fun startPage(page: PDPage) {
        super.startPage(page)
        width = page.cropBox.width.takeIf { it > 0f } ?: 1f
        height = page.cropBox.height.takeIf { it > 0f } ?: 1f
    }

    override fun writeString(string: String, textPositions: List<TextPosition>) {
        for (p in textPositions) {
            val unicode = p.unicode ?: continue
            val left = p.xDirAdj / width
            val right = (p.xDirAdj + p.widthDirAdj) / width
            val bottom = p.yDirAdj / height
            val top = (p.yDirAdj - p.heightDir) / height
            for (c in unicode) {
                text.append(c)
                boxes += left; boxes += top; boxes += right; boxes += bottom
            }
        }
    }

    override fun writeWordSeparator() = addSeparator(wordSeparator)

    override fun writeLineSeparator() = addSeparator(lineSeparator)

    private fun addSeparator(s: String) {
        for (c in s) {
            text.append(c)
            repeat(4) { boxes += Float.NaN }
        }
    }
}

/** texto y posiciones de todas las paginas. No funciona con PDF escaneados, que no tienen texto */
fun extractPdfPages(context: Context, file: File): List<PdfPageText> {
    PDFBoxResourceLoader.init(context.applicationContext)
    return PDDocument.load(file).use { doc ->
        (1..doc.numberOfPages).map { p ->
            val stripper = PositionStripper().apply {
                startPage = p
                endPage = p
            }
            stripper.getText(doc)
            PdfPageText(stripper.text.toString(), stripper.boxes.toFloatArray())
        }
    }
}
