package com.estudia.app.importers

import com.estudia.app.rich.escapeHtml
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.Locale

/**
 * Convierte documentos Word 97-2003 (.doc) a HTML. Lee el contenedor OLE2, la tabla de piezas
 * del texto, la hoja de estilos (STSH) y las propiedades de carácter y párrafo (FKP de CHPX/PAPX):
 * negrita, cursiva, subrayado, tachado, tamaño, fuente, color, resaltado, sombreado,
 * superíndice, mayúsculas, alineación, sangrías, espaciado, títulos y tablas.
 * No se interpretan imágenes ni la numeración automática (las listas se muestran con viñeta).
 */
object DocImporter {
    fun import(file: File): String = DocConverter(file.readBytes()).convert()
}

private data class CharProps(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Int = 0,
    val strike: Boolean = false,
    val halfPoints: Int = 20,
    val font: Int = -1,
    val color: Int? = null,
    val highlight: Int? = null,
    val shading: Int? = null,
    val iss: Int = 0,
    val caps: Boolean = false,
    val smallCaps: Boolean = false,
    val hidden: Boolean = false,
)

private data class ParaPropsDoc(
    val align: Int = 0,
    val left: Int = 0,
    val right: Int = 0,
    val firstLine: Int = 0,
    val before: Int = 0,
    val after: Int = 0,
    val lineHeight: String? = null,
    val inTable: Boolean = false,
    val rowEnd: Boolean = false,
    val list: Boolean = false,
)

private class DocStyle(
    val sti: Int,
    val type: Int,
    val base: Int,
    val name: String,
    val papx: Pair<Int, Int>?,
    val chpx: Pair<Int, Int>?,
)

private class Run(val start: Int, val end: Int, val data: Int, val length: Int, val istd: Int = 0)

private class DocConverter(bytes: ByteArray) {
    private val cfb = CompoundFile(bytes)
    private val word: ByteArray = cfb.stream("WordDocument") ?: error("No es un documento de Word 97-2003")
    private val wd = ByteBuffer.wrap(word).order(ByteOrder.LITTLE_ENDIAN)
    private val table: ByteArray
    private val tb: ByteBuffer

    private val fonts: List<String>
    private val styles: Map<Int, DocStyle>
    private var defaultFont = -1
    private val charStyleCache = HashMap<Int, CharProps>()
    private val paraStyleCache = HashMap<Int, ParaPropsDoc>()

    init {
        if ((wd.getShort(0).toInt() and 0xFFFF) != 0xA5EC) error("Documento de Word no reconocido")
        val flags = wd.getShort(0x0A).toInt()
        if (flags and 0x0100 != 0) error("El documento está protegido con contraseña")
        table = cfb.stream(if (flags and 0x0200 != 0) "1Table" else "0Table") ?: error("Documento de Word incompleto")
        tb = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN)
        fonts = runCatching { parseFonts() }.getOrDefault(emptyList())
        styles = runCatching { parseStyles() }.getOrDefault(emptyMap())
    }

    fun convert(): String {
        val (text, fcs) = readText()
        val chpRuns = runCatching { readFkps(0xFA, paragraph = false) }.getOrDefault(emptyList())
        val papRuns = runCatching { readFkps(0x102, paragraph = true) }.getOrDefault(emptyList())

        val out = StringBuilder("<article id=\"page\" data-w=\"595\" style=\"width:595pt;padding:72pt 72pt 72pt 72pt\">")
        var inTable = false
        var rowOpen = false
        var cellOpen = false
        var fieldDepthHidden = 0
        val fields = ArrayDeque<Boolean>()
        var paraStart = 0

        fun closeTable() {
            if (cellOpen) out.append("</td>")
            if (rowOpen) out.append("</tr>")
            out.append("</table>")
            inTable = false; rowOpen = false; cellOpen = false
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != '\r' && c != '\u0007' && i != text.length - 1) { i++; continue }
            // Párrafo [paraStart, i]: sus propiedades son las de la marca de fin
            val end = i
            val pap = find(papRuns, fcs[end])
            val istd = pap?.istd ?: 0
            val para = paraProps(istd, pap)
            val paraChar = paraStyleChars(istd)

            if (para.inTable) {
                if (!inTable) { out.append("<table class=\"doc\">"); inTable = true }
                if (para.rowEnd) {
                    if (cellOpen) { out.append("</td>"); cellOpen = false }
                    if (rowOpen) { out.append("</tr>"); rowOpen = false }
                    paraStart = i + 1; i++; continue
                }
                if (!rowOpen) { out.append("<tr>"); rowOpen = true }
                if (!cellOpen) { out.append("<td>"); cellOpen = true }
            } else if (inTable) {
                closeTable()
            }

            // Texto del párrafo agrupado por tramos con el mismo formato
            val content = StringBuilder()
            var pageBreakAfter = false
            var runText = StringBuilder()
            var runProps: CharProps? = null
            fun flush() {
                val props = runProps
                if (runText.isEmpty() || props == null) return
                val css = runCss(props, paraChar)
                var html = escapeHtml(runText)
                if (css.isNotEmpty()) html = "<span style=\"$css\">$html</span>"
                when (props.iss) {
                    1 -> html = "<sup>$html</sup>"
                    2 -> html = "<sub>$html</sub>"
                }
                content.append(html)
                runText = StringBuilder()
            }

            for (k in paraStart until end + (if (c == '\r' || c == '\u0007') 0 else 1)) {
                val ch = text[k]
                when (ch) {
                    '\u0013' -> { fields.addLast(true); fieldDepthHidden++; continue }
                    '\u0014' -> { if (fields.removeLastOrNull() == true) fieldDepthHidden--; fields.addLast(false); continue }
                    '\u0015' -> { if (fields.removeLastOrNull() == true) fieldDepthHidden--; continue }
                }
                if (fieldDepthHidden > 0) continue
                val props = charProps(find(chpRuns, fcs[k]), paraChar)
                if (props.hidden) continue
                if (props != runProps) { flush(); runProps = props }
                when (ch) {
                    '\u000B' -> { flush(); content.append("<br>") }
                    '\u000C' -> { flush(); pageBreakAfter = true }
                    '\u001E' -> runText.append('‑')
                    '\u0001', '\u0002', '\u0005', '\u0008', '\u001F' -> {}
                    else -> if (ch >= ' ' || ch == '\t') runText.append(ch)
                }
            }
            flush()

            val heading = styles[istd]?.sti?.takeIf { it in 1..9 }
            val tag = heading?.let { "h${it.coerceAtMost(6)}" } ?: "p"
            out.append('<').append(tag).append(" style=\"").append(paraCss(para, paraChar)).append("\">")
            if (para.list) out.append("•\t")
            if (content.isEmpty() && !para.list) out.append("<br>") else out.append(content)
            out.append("</").append(tag).append('>')
            if (pageBreakAfter) out.append("<hr class=\"pb\">")

            if (para.inTable && c == '\u0007' && cellOpen) { out.append("</td>"); cellOpen = false }
            paraStart = i + 1
            i++
        }
        if (inTable) closeTable()
        return out.append("</article>").toString()
    }

    // ---------- Texto ----------

    /** Texto principal y, por cada carácter, su posición en el stream (para buscar su formato). */
    private fun readText(): Pair<String, IntArray> {
        val ccpText = wd.getInt(0x4C)
        val fcClx = wd.getInt(0x1A2)
        val lcbClx = wd.getInt(0x1A6)
        val sb = StringBuilder()
        val fcs = ArrayList<Int>()
        var pos = fcClx
        loop@ while (pos < fcClx + lcbClx) {
            when (table[pos].toInt()) {
                0x01 -> pos += 3 + u16(table, pos + 1)
                0x02 -> {
                    val lcb = tb.getInt(pos + 1)
                    val base = pos + 5
                    val n = (lcb - 4) / 12
                    val pcdBase = base + (n + 1) * 4
                    for (i in 0 until n) {
                        val cpStart = tb.getInt(base + i * 4)
                        val cpEnd = tb.getInt(base + (i + 1) * 4)
                        val fcRaw = tb.getInt(pcdBase + i * 8 + 2)
                        val chars = cpEnd - cpStart
                        if (chars <= 0) continue
                        if (fcRaw and 0x40000000 != 0) {
                            val off = (fcRaw and 0x3FFFFFFF) / 2
                            if (off + chars > word.size) continue
                            sb.append(String(word, off, chars, CP1252))
                            for (k in 0 until chars) fcs += off + k
                        } else {
                            val off = fcRaw
                            if (off + chars * 2 > word.size) continue
                            sb.append(String(word, off, chars * 2, Charsets.UTF_16LE))
                            for (k in 0 until chars) fcs += off + k * 2
                        }
                        if (sb.length >= ccpText) break
                    }
                    break@loop
                }
                else -> break@loop
            }
        }
        val len = if (ccpText in 1..sb.length) ccpText else sb.length
        return sb.substring(0, len) to fcs.subList(0, len).toIntArray()
    }

    // ---------- FKP (formato directo) ----------

    /** Lee la tabla de bloques de formato. fibOffset: 0xFA para carácter, 0x102 para párrafo. */
    private fun readFkps(fibOffset: Int, paragraph: Boolean): List<Run> {
        val fc = wd.getInt(fibOffset)
        val lcb = wd.getInt(fibOffset + 4)
        if (lcb <= 4) return emptyList()
        val n = (lcb - 4) / 8
        val runs = ArrayList<Run>()
        for (i in 0 until n) {
            val pn = tb.getInt(fc + (n + 1) * 4 + i * 4) and 0x3FFFFF
            val page = pn * 512
            if (page + 512 > word.size) continue
            val crun = word[page + 511].toInt() and 0xFF
            for (r in 0 until crun) {
                val start = wd.getInt(page + r * 4)
                val end = wd.getInt(page + (r + 1) * 4)
                if (paragraph) {
                    val bOffset = word[page + (crun + 1) * 4 + r * 13].toInt() and 0xFF
                    if (bOffset == 0) { runs += Run(start, end, -1, 0); continue }
                    var p = page + bOffset * 2
                    var cb = word[p].toInt() and 0xFF
                    val len: Int
                    if (cb == 0) { cb = word[p + 1].toInt() and 0xFF; len = cb * 2; p += 2 } else { len = cb * 2 - 1; p += 1 }
                    val istd = u16(word, p)
                    runs += Run(start, end, p + 2, len - 2, istd)
                } else {
                    val b = word[page + (crun + 1) * 4 + r].toInt() and 0xFF
                    if (b == 0) { runs += Run(start, end, -1, 0); continue }
                    val p = page + b * 2
                    runs += Run(start, end, p + 1, word[p].toInt() and 0xFF)
                }
            }
        }
        runs.sortBy { it.start }
        return runs
    }

    private fun find(runs: List<Run>, fc: Int): Run? {
        var lo = 0
        var hi = runs.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val r = runs[mid]
            when {
                fc < r.start -> hi = mid - 1
                fc >= r.end -> lo = mid + 1
                else -> return r
            }
        }
        return null
    }

    // ---------- Propiedades ----------

    private fun charProps(run: Run?, paraChar: CharProps): CharProps {
        if (run == null || run.data < 0) return paraChar
        return applyChpx(paraChar, word, run.data, run.length)
    }

    private fun paraProps(istd: Int, run: Run?): ParaPropsDoc {
        val base = paraStyleProps(istd)
        if (run == null || run.data < 0) return base
        return applyPapx(base, word, run.data, run.length)
    }

    /** Formato de carácter del estilo de párrafo (con herencia de estilos base). */
    private fun paraStyleChars(istd: Int, depth: Int = 0): CharProps = charStyleCache.getOrPut(istd) {
        val style = styles[istd]
        val base = if (style != null && style.base != 0xFFF && style.base != istd && depth < 20) {
            paraStyleChars(style.base, depth + 1)
        } else CharProps(font = defaultFont)
        val chpx = style?.chpx ?: return@getOrPut base
        applyChpx(base, table, chpx.first, chpx.second)
    }

    private fun paraStyleProps(istd: Int, depth: Int = 0): ParaPropsDoc = paraStyleCache.getOrPut(istd) {
        val style = styles[istd]
        val base = if (style != null && style.base != 0xFFF && style.base != istd && depth < 20) {
            paraStyleProps(style.base, depth + 1)
        } else ParaPropsDoc()
        val papx = style?.papx ?: return@getOrPut base
        applyPapx(base, table, papx.first, papx.second).copy(inTable = false, rowEnd = false)
    }

    private fun applyChpx(start: CharProps, data: ByteArray, offset: Int, length: Int): CharProps {
        var p = start
        val style = start
        forEachSprm(data, offset, length) { sprm, op ->
            fun toggle(current: Boolean, styleValue: Boolean): Boolean = when (data[op].toInt() and 0xFF) {
                0 -> false
                1 -> true
                0x80 -> styleValue
                0x81 -> !styleValue
                else -> current
            }
            p = when (sprm) {
                0x0835 -> p.copy(bold = toggle(p.bold, style.bold))
                0x0836 -> p.copy(italic = toggle(p.italic, style.italic))
                0x0837, 0x2A53 -> p.copy(strike = toggle(p.strike, style.strike))
                0x083A -> p.copy(smallCaps = toggle(p.smallCaps, style.smallCaps))
                0x083B -> p.copy(caps = toggle(p.caps, style.caps))
                0x083C -> p.copy(hidden = toggle(p.hidden, style.hidden))
                0x2A3E -> p.copy(underline = data[op].toInt() and 0xFF)
                0x4A43 -> p.copy(halfPoints = u16(data, op))
                0x4A4F -> p.copy(font = u16(data, op))
                0x2A42 -> p.copy(color = ICO.getOrNull(data[op].toInt() and 0xFF))
                0x6870 -> p.copy(color = colorRef(data, op))
                0x2A0C -> p.copy(highlight = ICO.getOrNull(data[op].toInt() and 0xFF))
                0xCA71 -> p.copy(shading = colorRef(data, op + 1 + 4)) // tamaño, cvFore, cvBack
                0x2A48 -> p.copy(iss = data[op].toInt() and 0xFF)
                // Estilo de carácter: sus propiedades se suman a las del párrafo
                0x4A30 -> styles[u16(data, op)]?.chpx?.let { (o, l) -> applyChpx(p, table, o, l) } ?: p
                else -> p
            }
        }
        return p
    }

    private fun applyPapx(start: ParaPropsDoc, data: ByteArray, offset: Int, length: Int): ParaPropsDoc {
        var p = start
        forEachSprm(data, offset, length) { sprm, op ->
            p = when (sprm) {
                0x2403, 0x2461 -> p.copy(align = data[op].toInt() and 0xFF)
                0x840F, 0x845E -> p.copy(left = s16(data, op))
                0x840E, 0x845D -> p.copy(right = s16(data, op))
                0x8411, 0x8460 -> p.copy(firstLine = s16(data, op))
                0xA413 -> p.copy(before = u16(data, op))
                0xA414 -> p.copy(after = u16(data, op))
                0x6412 -> {
                    val line = s16(data, op)
                    val mult = u16(data, op + 2)
                    p.copy(lineHeight = if (mult == 1) num(line / 240f) else num(kotlin.math.abs(line) / 20f) + "pt")
                }
                0x2416 -> p.copy(inTable = data[op].toInt() != 0)
                0x6649 -> p.copy(inTable = tbIntAt(data, op) > 0)
                0x2417, 0x244C -> p.copy(rowEnd = data[op].toInt() != 0)
                0x460B -> p.copy(list = u16(data, op) > 0)
                else -> p
            }
        }
        return p
    }

    /** Recorre los "sprm" (modificadores de propiedad) y llama con el inicio de cada operando. */
    private inline fun forEachSprm(data: ByteArray, offset: Int, length: Int, action: (Int, Int) -> Unit) {
        var i = offset
        val end = minOf(offset + length, data.size)
        while (i + 2 <= end) {
            val sprm = u16(data, i)
            i += 2
            val size = when (sprm ushr 13) {
                0, 1 -> 1
                2, 4, 5 -> 2
                3 -> 4
                7 -> 3
                else -> when (sprm) {
                    0xD606, 0xD608 -> u16(data, i) + 1
                    0xC615 -> if ((data[i].toInt() and 0xFF) == 255) return else (data[i].toInt() and 0xFF) + 1
                    else -> (data[i].toInt() and 0xFF) + 1
                }
            }
            if (i + size > end) return
            val operand = if (sprm ushr 13 == 6 && sprm != 0xD606 && sprm != 0xD608) i + 1 else i
            action(sprm, operand)
            i += size
        }
    }

    // ---------- Estilos y fuentes ----------

    private fun parseStyles(): Map<Int, DocStyle> {
        val fc = wd.getInt(0xA2)
        val lcb = wd.getInt(0xA6)
        if (lcb <= 0) return emptyMap()
        val cbStshi = u16(table, fc)
        val stshi = fc + 2
        val cstd = u16(table, stshi)
        val cbStdBase = u16(table, stshi + 2)
        if (cbStshi >= 14) defaultFont = u16(table, stshi + 12)

        val result = HashMap<Int, DocStyle>()
        var pos = stshi + cbStshi
        for (istd in 0 until cstd) {
            if (pos + 2 > fc + lcb) break
            val cbStd = u16(table, pos)
            pos += 2
            if (cbStd == 0) continue
            val s = pos
            pos += cbStd
            runCatching {
                val sti = u16(table, s) and 0x0FFF
                val w1 = u16(table, s + 2)
                val type = w1 and 0x000F
                val base = w1 ushr 4
                val cupx = u16(table, s + 4) and 0x000F
                var p = s + cbStdBase
                val cch = u16(table, p)
                val name = String(table, p + 2, cch * 2, Charsets.UTF_16LE)
                p += 2 + cch * 2 + 2
                val upx = ArrayList<Pair<Int, Int>>()
                repeat(cupx) {
                    if ((p - s) % 2 != 0) p++
                    val cb = u16(table, p)
                    upx += (p + 2) to cb
                    p += 2 + cb
                }
                // Estilo de párrafo: [papx (istd + grpprl), chpx]; de carácter: [chpx]
                val papx = if (type == 1) upx.getOrNull(0)?.let { (o, l) -> (o + 2) to (l - 2) } else null
                val chpx = when (type) {
                    1 -> upx.getOrNull(1)
                    2 -> upx.getOrNull(0)
                    else -> null
                }
                result[istd] = DocStyle(sti, type, base, name, papx, chpx)
            }
        }
        return result
    }

    private fun parseFonts(): List<String> {
        var p = wd.getInt(0x112)
        val lcb = wd.getInt(0x116)
        if (lcb <= 0) return emptyList()
        var count = u16(table, p)
        val extended = count == 0xFFFF
        if (extended) { p += 2; count = u16(table, p) }
        p += 2
        val cbExtra = u16(table, p)
        p += 2
        val names = ArrayList<String>()
        repeat(count) {
            val len = if (extended) u16(table, p).also { p += 2 } else (table[p].toInt() and 0xFF).also { p += 1 }
            val nameStart = p + 39 // ffid, peso, juego de caracteres, panose y firma de fuente
            val sb = StringBuilder()
            var q = nameStart
            while (q + 1 < p + len) {
                val ch = u16(table, q).toChar()
                if (ch == '\u0000') break
                sb.append(ch)
                q += 2
            }
            names += sb.toString()
            p += len + cbExtra
        }
        return names
    }

    // ---------- CSS ----------

    private fun paraCss(p: ParaPropsDoc, chars: CharProps): String {
        val css = StringBuilder()
        when (p.align) {
            1 -> css.append("text-align:center;")
            2 -> css.append("text-align:right;")
            3, 4 -> css.append("text-align:justify;")
        }
        css.append("margin:").append(num(p.before / 20f)).append("pt ").append(num(p.right / 20f)).append("pt ")
            .append(num(p.after / 20f)).append("pt ").append(num(p.left / 20f)).append("pt;")
        if (p.firstLine != 0) css.append("text-indent:").append(num(p.firstLine / 20f)).append("pt;")
        p.lineHeight?.let { css.append("line-height:").append(it).append(';') }
        fontName(chars.font)?.let { css.append("font-family:").append(fontStack(it)).append(';') }
        css.append("font-size:").append(num(chars.halfPoints / 2f)).append("pt;")
        chars.color?.let { css.append("color:").append(hex(it)).append(';') }
        css.append("font-weight:").append(if (chars.bold) "bold" else "normal").append(';')
        css.append("font-style:").append(if (chars.italic) "italic" else "normal").append(';')
        return css.toString()
    }

    private fun runCss(r: CharProps, base: CharProps): String {
        val css = StringBuilder()
        if (r.font != base.font) fontName(r.font)?.let { css.append("font-family:").append(fontStack(it)).append(';') }
        if (r.halfPoints != base.halfPoints) css.append("font-size:").append(num(r.halfPoints / 2f)).append("pt;")
        if (r.color != base.color) css.append("color:").append(r.color?.let(::hex) ?: "inherit").append(';')
        if (r.bold != base.bold) css.append("font-weight:").append(if (r.bold) "bold" else "normal").append(';')
        if (r.italic != base.italic) css.append("font-style:").append(if (r.italic) "italic" else "normal").append(';')
        val deco = decoration(r)
        if (deco != decoration(base)) css.append("text-decoration:").append(deco ?: "none").append(';')
        val bg = r.highlight ?: r.shading
        if (bg != (base.highlight ?: base.shading) && bg != null) css.append("background-color:").append(hex(bg)).append(';')
        if (r.caps != base.caps) css.append("text-transform:").append(if (r.caps) "uppercase" else "none").append(';')
        if (r.smallCaps != base.smallCaps) css.append("font-variant:").append(if (r.smallCaps) "small-caps" else "normal").append(';')
        return css.toString()
    }

    private fun decoration(r: CharProps): String? {
        val lines = mutableListOf<String>()
        if (r.underline != 0) lines += "underline"
        if (r.strike) lines += "line-through"
        if (lines.isEmpty()) return null
        val style = when (r.underline) {
            3 -> " double"
            4, 20 -> " dotted"
            7, 9, 10, 23, 25, 26, 39, 55 -> " dashed"
            11, 27, 43 -> " wavy"
            else -> ""
        }
        return lines.joinToString(" ") + style
    }

    private fun fontName(index: Int): String? = fonts.getOrNull(index)?.takeIf { it.isNotBlank() }

    private fun fontStack(name: String): String {
        val n = name.lowercase(Locale.ROOT)
        val generic = when {
            listOf("courier", "consolas", "mono").any { it in n } -> "monospace"
            "sans" in n -> "sans-serif"
            listOf("times", "georgia", "garamond", "cambria", "book", "palatino", "serif").any { it in n } -> "serif"
            else -> "sans-serif"
        }
        return "'${name.replace("'", "")}',$generic"
    }

    private fun colorRef(data: ByteArray, p: Int): Int? {
        if (p + 4 > data.size) return null
        if ((data[p + 3].toInt() and 0xFF) == 0xFF) return null // automático
        return ((data[p].toInt() and 0xFF) shl 16) or ((data[p + 1].toInt() and 0xFF) shl 8) or (data[p + 2].toInt() and 0xFF)
    }

    private fun tbIntAt(data: ByteArray, p: Int) = ByteBuffer.wrap(data, p, 4).order(ByteOrder.LITTLE_ENDIAN).int

    companion object {
        val CP1252: Charset = Charset.forName("windows-1252")

        /** Paleta de 16 colores de Word (índice ico). null = automático. */
        val ICO: List<Int?> = listOf(
            null, 0x000000, 0x0000FF, 0x00FFFF, 0x00FF00, 0xFF00FF, 0xFF0000, 0xFFFF00, 0xFFFFFF,
            0x000080, 0x008080, 0x008000, 0x800080, 0x800000, 0x808000, 0x808080, 0xC0C0C0,
        )

        fun hex(rgb: Int) = "#%06X".format(rgb and 0xFFFFFF)

        fun num(v: Float): String =
            if (v == v.toInt().toFloat()) v.toInt().toString() else String.format(Locale.ROOT, "%.2f", v).trimEnd('0').trimEnd('.')
    }
}

private fun u16(data: ByteArray, p: Int) = (data[p].toInt() and 0xFF) or ((data[p + 1].toInt() and 0xFF) shl 8)
private fun s16(data: ByteArray, p: Int) = u16(data, p).toShort().toInt()

/** Lector mínimo de "Compound File Binary" (OLE2), el contenedor de .doc/.xls/.ppt antiguos. */
private class CompoundFile(private val data: ByteArray) {
    private val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    private val sectorSize: Int
    private val miniSectorSize: Int
    private val miniCutoff: Int
    private val fat: IntArray
    private val miniFat: IntArray
    private val entries: List<Entry>
    private val miniStream: ByteArray

    private class Entry(val name: String, val type: Int, val start: Int, val size: Long)

    init {
        val magic = buf.getLong(0)
        if (magic != 0xE11AB1A1E011CFD0uL.toLong()) error("No es un documento de Word 97-2003")
        sectorSize = 1 shl buf.getShort(0x1E).toInt()
        miniSectorSize = 1 shl buf.getShort(0x20).toInt()
        val numFatSectors = buf.getInt(0x2C)
        val firstDir = buf.getInt(0x30)
        miniCutoff = buf.getInt(0x38)
        val firstMiniFat = buf.getInt(0x3C)
        var difatSector = buf.getInt(0x44)

        // Lista de sectores de la FAT: 109 en la cabecera y el resto encadenados (DIFAT)
        val fatSectors = ArrayList<Int>()
        for (i in 0 until 109) {
            val s = buf.getInt(0x4C + i * 4)
            if (s >= 0) fatSectors += s
        }
        val perSector = sectorSize / 4
        var guard = 0
        while (difatSector >= 0 && fatSectors.size < numFatSectors && guard++ < 10_000) {
            val off = offsetOf(difatSector)
            for (i in 0 until perSector - 1) {
                val s = buf.getInt(off + i * 4)
                if (s >= 0) fatSectors += s
            }
            difatSector = buf.getInt(off + (perSector - 1) * 4)
        }

        fat = IntArray(fatSectors.size * perSector)
        fatSectors.forEachIndexed { idx, sector ->
            val off = offsetOf(sector)
            for (i in 0 until perSector) {
                if (off + i * 4 + 4 <= data.size) fat[idx * perSector + i] = buf.getInt(off + i * 4)
            }
        }

        val dir = readChain(firstDir, Long.MAX_VALUE)
        val dirBuf = ByteBuffer.wrap(dir).order(ByteOrder.LITTLE_ENDIAN)
        entries = (0 until dir.size / 128).map { i ->
            val off = i * 128
            val nameLen = (dirBuf.getShort(off + 0x40).toInt() and 0xFFFF).coerceIn(0, 64)
            Entry(
                name = String(dir, off, (nameLen - 2).coerceAtLeast(0), Charsets.UTF_16LE),
                type = dir[off + 0x42].toInt(),
                start = dirBuf.getInt(off + 0x74),
                size = dirBuf.getInt(off + 0x78).toLong() and 0xFFFFFFFFL,
            )
        }

        val root = entries.firstOrNull { it.type == 5 }
        miniStream = root?.let { readChain(it.start, it.size) } ?: ByteArray(0)
        val miniFatBytes = if (firstMiniFat >= 0) readChain(firstMiniFat, Long.MAX_VALUE) else ByteArray(0)
        val mfb = ByteBuffer.wrap(miniFatBytes).order(ByteOrder.LITTLE_ENDIAN)
        miniFat = IntArray(miniFatBytes.size / 4) { mfb.getInt(it * 4) }
    }

    fun stream(name: String): ByteArray? {
        val entry = entries.firstOrNull { it.type == 2 && it.name == name } ?: return null
        return if (entry.size < miniCutoff) readMiniChain(entry.start, entry.size) else readChain(entry.start, entry.size)
    }

    private fun offsetOf(sector: Int) = (sector + 1) * sectorSize

    private fun readChain(start: Int, size: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var sector = start
        var guard = 0
        while (sector >= 0 && sector < fat.size && out.size() < size && guard++ < 1_000_000) {
            val off = offsetOf(sector)
            if (off >= data.size) break
            out.write(data, off, minOf(sectorSize, data.size - off))
            sector = fat[sector]
        }
        val bytes = out.toByteArray()
        return if (size < bytes.size) bytes.copyOf(size.toInt()) else bytes
    }

    private fun readMiniChain(start: Int, size: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var sector = start
        var guard = 0
        while (sector >= 0 && sector < miniFat.size && out.size() < size && guard++ < 1_000_000) {
            val off = sector * miniSectorSize
            if (off >= miniStream.size) break
            out.write(miniStream, off, minOf(miniSectorSize, miniStream.size - off))
            sector = miniFat[sector]
        }
        val bytes = out.toByteArray()
        return if (size < bytes.size) bytes.copyOf(size.toInt()) else bytes
    }
}
