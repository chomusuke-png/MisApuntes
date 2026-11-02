package com.estudia.app.rich

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Formato interno de los documentos editables: texto plano + rangos de estilo.
 * Es independiente de Android, así que los importadores lo pueden generar sin vistas.
 */
@Serializable
data class RichDocument(
    val version: Int = 1,
    val text: String = "",
    val spans: List<SpanData> = emptyList(),
) {
    fun toJson(): String = json.encodeToString(serializer(), normalized())

    /** HTML para el editor: un párrafo por línea, cada tramo con los estilos que lo cubren. */
    fun toHtml(): String {
        val out = StringBuilder("<article id=\"page\">")
        var pos = 0
        for (line in text.split('\n')) {
            val start = pos
            val end = pos + line.length
            val header = spans.firstOrNull { it.t == SpanType.HEADER && it.s <= start && it.e >= end }?.v
            val tag = header?.let { "h${it.coerceIn(1, 3)}" } ?: "p"
            out.append('<').append(tag).append('>')
            if (line.isEmpty()) {
                out.append("<br>")
            } else {
                val inline = spans.filter { it.t != SpanType.HEADER && it.e > start && it.s < end }
                val cuts = (inline.flatMap { listOf(it.s, it.e) } + start + end)
                    .map { it.coerceIn(start, end) }.distinct().sorted()
                for (i in 0 until cuts.size - 1) {
                    val a = cuts[i]
                    val b = cuts[i + 1]
                    val segment = escapeHtml(text.substring(a, b))
                    val css = inlineCss(inline.filter { it.s <= a && it.e >= b })
                    if (css.isEmpty()) out.append(segment)
                    else out.append("<span style=\"").append(css).append("\">").append(segment).append("</span>")
                }
            }
            out.append("</").append(tag).append('>')
            pos = end + 1
        }
        return out.append("</article>").toString()
    }

    private fun inlineCss(active: List<SpanData>): String {
        val parts = mutableListOf<String>()
        val decorations = mutableListOf<String>()
        for (sp in active) when (sp.t) {
            SpanType.BOLD -> parts += "font-weight:bold"
            SpanType.ITALIC -> parts += "font-style:italic"
            SpanType.UNDERLINE -> decorations += "underline"
            SpanType.STRIKE -> decorations += "line-through"
            SpanType.COLOR -> sp.v?.let { parts += "color:${cssColor(it)}" }
            SpanType.HIGHLIGHT -> sp.v?.let { parts += "background-color:${cssColor(it)}" }
            SpanType.SIZE -> sp.v?.let { parts += "font-size:${it * 3 / 4}pt" }
            SpanType.FONT -> sp.f?.let { parts += "font-family:${escapeHtml(it)}" }
        }
        if (decorations.isNotEmpty()) parts += "text-decoration:${decorations.distinct().joinToString(" ")}"
        return parts.joinToString(";")
    }

    /** Une rangos contiguos del mismo estilo y descarta rangos vacíos o fuera del texto. */
    fun normalized(): RichDocument {
        val len = text.length
        val merged = spans
            .map { it.copy(s = it.s.coerceIn(0, len), e = it.e.coerceIn(0, len)) }
            .filter { it.e > it.s || it.t == SpanType.HEADER }
            .groupBy { Triple(it.t, it.v, it.f) }
            .flatMap { (_, group) ->
                val sorted = group.sortedBy { it.s }
                val out = mutableListOf<SpanData>()
                for (span in sorted) {
                    val last = out.lastOrNull()
                    if (last != null && span.s <= last.e && span.t != SpanType.HEADER) {
                        out[out.lastIndex] = last.copy(e = maxOf(last.e, span.e))
                    } else {
                        out += span
                    }
                }
                out
            }
        return copy(spans = merged)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(raw: String): RichDocument = json.decodeFromString(serializer(), raw)

        fun plain(text: String) = RichDocument(text = text.replace("\r\n", "\n").replace('\r', '\n'))
    }
}

fun escapeHtml(s: CharSequence): String {
    val out = StringBuilder(s.length + 16)
    for (c in s) when (c) {
        '&' -> out.append("&amp;")
        '<' -> out.append("&lt;")
        '>' -> out.append("&gt;")
        '"' -> out.append("&quot;")
        '\'' -> out.append("&#39;")
        else -> out.append(c)
    }
    return out.toString()
}

fun cssColor(argb: Int) = "#%06X".format(argb and 0xFFFFFF)

/** t = tipo, s/e = inicio/fin, v = valor numérico (color, tamaño, nivel), f = familia de fuente. */
@Serializable
data class SpanData(
    val t: String,
    val s: Int,
    val e: Int,
    val v: Int? = null,
    val f: String? = null,
)

object SpanType {
    const val BOLD = "b"
    const val ITALIC = "i"
    const val UNDERLINE = "u"
    const val STRIKE = "s"
    const val COLOR = "color"
    const val HIGHLIGHT = "hl"
    const val SIZE = "size"
    const val FONT = "font"
    const val HEADER = "h"
}

/** Ayuda a los importadores a construir texto y estilos a la vez. */
class RichBuilder {
    val sb = StringBuilder()
    val spans = mutableListOf<SpanData>()
    val length get() = sb.length

    fun append(text: CharSequence) = apply { sb.append(text) }

    fun span(type: String, start: Int, end: Int, v: Int? = null, f: String? = null) {
        if (end > start || type == SpanType.HEADER) spans += SpanData(type, start, end, v, f)
    }

    fun build(): RichDocument {
        // Sin saltos de línea sobrantes al final
        var end = sb.length
        while (end > 0 && sb[end - 1] == '\n') end--
        sb.setLength(end)
        return RichDocument(text = sb.toString(), spans = spans).normalized()
    }
}
