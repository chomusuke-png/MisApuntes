package com.estudia.app.importers

import android.content.Context
import com.estudia.app.rich.RichBuilder
import com.estudia.app.rich.RichDocument
import com.estudia.app.rich.SpanType
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object PlainTextImporter {
    fun import(file: File): RichDocument = RichDocument.plain(readText(file))

    /** UTF-8 si es válido; si no, Windows-1252 (típico de .txt guardados en Windows). */
    fun readText(file: File): String {
        val bytes = file.readBytes()
        val content = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            bytes.copyOfRange(3, bytes.size)
        } else bytes
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(content))
                .toString()
        } catch (_: CharacterCodingException) {
            String(content, Charset.forName("windows-1252"))
        }
    }
}

/**
 * Markdown básico: títulos (#), listas, citas, bloques de código, **negrita**, *cursiva*,
 * ~~tachado~~, `código`, ==resaltado== y [enlaces](url).
 */
object MarkdownImporter {

    private val inline = Regex(
        """`([^`]+)`|\*\*(.+?)\*\*|__(.+?)__|~~(.+?)~~|==(.+?)==|\*(?!\s)(.+?)\*|(?<!\w)_(?!\s)(.+?)_(?!\w)|\[([^\]]+)]\(([^)]*)\)"""
    )
    private const val HIGHLIGHT_YELLOW = 0xFFFFF59D.toInt()

    fun import(file: File): RichDocument = parse(PlainTextImporter.readText(file))

    fun parse(markdown: String): RichDocument {
        val out = RichBuilder()
        var inCode = false
        for (rawLine in markdown.replace("\r\n", "\n").split('\n')) {
            val line = rawLine.trimEnd()
            if (line.trimStart().startsWith("```")) {
                inCode = !inCode
                continue
            }
            if (inCode) {
                val s = out.length
                out.append(rawLine)
                out.span(SpanType.FONT, s, out.length, f = "monospace")
                out.append("\n")
                continue
            }

            val heading = Regex("""^(#{1,6})\s+(.*)$""").find(line)
            val bullet = Regex("""^(\s*)[-*+]\s+(.*)$""").find(line)
            val quote = Regex("""^>\s?(.*)$""").find(line)
            when {
                heading != null -> {
                    val s = out.length
                    parseInline(heading.groupValues[2].trimEnd('#', ' '), out)
                    out.span(SpanType.HEADER, s, out.length, heading.groupValues[1].length.coerceAtMost(3))
                }
                Regex("""^\s*([-*_])(\s*\1){2,}\s*$""").matches(line) -> out.append("──────────")
                bullet != null -> {
                    out.append(bullet.groupValues[1] + "• ")
                    parseInline(bullet.groupValues[2], out)
                }
                quote != null -> {
                    val s = out.length
                    parseInline(quote.groupValues[1], out)
                    out.span(SpanType.ITALIC, s, out.length)
                }
                else -> parseInline(line, out)
            }
            out.append("\n")
        }
        return out.build()
    }

    private fun parseInline(text: String, out: RichBuilder) {
        var pos = 0
        while (pos < text.length) {
            val m = inline.find(text, pos)
            if (m == null) {
                out.append(text.substring(pos))
                return
            }
            out.append(text.substring(pos, m.range.first))
            val g = m.groupValues
            val s = out.length
            when {
                g[1].isNotEmpty() -> {
                    out.append(g[1])
                    out.span(SpanType.FONT, s, out.length, f = "monospace")
                }
                g[2].isNotEmpty() || g[3].isNotEmpty() -> {
                    parseInline(g[2].ifEmpty { g[3] }, out)
                    out.span(SpanType.BOLD, s, out.length)
                }
                g[4].isNotEmpty() -> {
                    parseInline(g[4], out)
                    out.span(SpanType.STRIKE, s, out.length)
                }
                g[5].isNotEmpty() -> {
                    parseInline(g[5], out)
                    out.span(SpanType.HIGHLIGHT, s, out.length, HIGHLIGHT_YELLOW)
                }
                g[6].isNotEmpty() || g[7].isNotEmpty() -> {
                    parseInline(g[6].ifEmpty { g[7] }, out)
                    out.span(SpanType.ITALIC, s, out.length)
                }
                g[8].isNotEmpty() -> {
                    parseInline(g[8], out)
                    out.span(SpanType.UNDERLINE, s, out.length)
                }
                else -> out.append(m.value)
            }
            pos = m.range.last + 1
        }
    }
}

/** Texto de un PDF para convertirlo en nota editable (no funciona con PDF escaneados). */
object PdfTextExtractor {
    fun extract(context: Context, file: File): RichDocument {
        PDFBoxResourceLoader.init(context.applicationContext)
        val text = PDDocument.load(file).use { doc ->
            PDFTextStripper().apply { pageEnd = "\n\n" }.getText(doc)
        }
        if (text.isBlank()) error("El PDF no tiene texto seleccionable (¿es un escaneo?)")
        return RichDocument.plain(text.replace(Regex("\n{3,}"), "\n\n").trim())
    }
}
