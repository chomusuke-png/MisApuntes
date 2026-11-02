package com.estudia.app.importers

import com.estudia.app.rich.escapeHtml
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Convierte .docx a HTML conservando lo máximo posible: estilos del documento (con herencia),
 * fuentes, tamaños, colores, resaltados, sombreados, alineación, sangrías, interlineado,
 * listas numeradas y con viñetas, tablas (celdas combinadas, bordes, fondos), imágenes,
 * cuadros de texto, hipervínculos, notas al pie, saltos de página y tamaño/márgenes de la hoja.
 * Las imágenes se guardan en [imagesDir] y el HTML las referencia por nombre.
 */
object DocxImporter {
    fun import(file: File, imagesDir: File): String {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) entries[entry.name.removePrefix("/")] = zip.readBytes()
            }
        }
        if ("word/document.xml" !in entries) error("El archivo no es un documento de Word válido")
        return DocxConverter(entries, imagesDir).convert()
    }
}

// ---------- Propiedades ----------

private data class RunProps(
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: String? = null,
    val strike: Boolean? = null,
    val color: String? = null,
    val sizePt: Float? = null,
    val font: String? = null,
    val highlight: String? = null,
    val shading: String? = null,
    val vertAlign: String? = null,
    val caps: Boolean? = null,
    val smallCaps: Boolean? = null,
    val hidden: Boolean? = null,
) {
    fun merge(o: RunProps?): RunProps = if (o == null) this else RunProps(
        bold = o.bold ?: bold,
        italic = o.italic ?: italic,
        underline = o.underline ?: underline,
        strike = o.strike ?: strike,
        color = o.color ?: color,
        sizePt = o.sizePt ?: sizePt,
        font = o.font ?: font,
        highlight = o.highlight ?: highlight,
        shading = o.shading ?: shading,
        vertAlign = o.vertAlign ?: vertAlign,
        caps = o.caps ?: caps,
        smallCaps = o.smallCaps ?: smallCaps,
        hidden = o.hidden ?: hidden,
    )
}

private data class ParaProps(
    val align: String? = null,
    val indLeft: Float? = null,
    val indRight: Float? = null,
    val firstLine: Float? = null,
    val before: Float? = null,
    val after: Float? = null,
    val lineHeight: String? = null,
    val shading: String? = null,
    val numId: String? = null,
    val ilvl: Int? = null,
    val outline: Int? = null,
    val pageBreakBefore: Boolean? = null,
    val borderBottom: Boolean? = null,
) {
    fun merge(o: ParaProps?): ParaProps = if (o == null) this else ParaProps(
        align = o.align ?: align,
        indLeft = o.indLeft ?: indLeft,
        indRight = o.indRight ?: indRight,
        firstLine = o.firstLine ?: firstLine,
        before = o.before ?: before,
        after = o.after ?: after,
        lineHeight = o.lineHeight ?: lineHeight,
        shading = o.shading ?: shading,
        numId = o.numId ?: numId,
        ilvl = o.ilvl ?: ilvl,
        outline = o.outline ?: outline,
        pageBreakBefore = o.pageBreakBefore ?: pageBreakBefore,
        borderBottom = o.borderBottom ?: borderBottom,
    )
}

private class StyleDef(
    val name: String,
    val basedOn: String?,
    val para: ParaProps,
    val run: RunProps,
    val tableBorders: Boolean?,
)

private class Resolved(val para: ParaProps, val run: RunProps, val heading: Int?, val tableBorders: Boolean?)

private class Level(val start: Int, val format: String, val text: String, val para: ParaProps, val run: RunProps)

private class Rel(val target: String, val external: Boolean)

// ---------- Conversor ----------

private class DocxConverter(private val zip: Map<String, ByteArray>, private val imagesDir: File) {

    private val rels: Map<String, Rel> = parseRels("word/_rels/document.xml.rels")
    private val footnoteRels: Map<String, Rel> = parseRels("word/_rels/footnotes.xml.rels")
    private var activeRels = rels

    private val minorFont: String?
    private val majorFont: String?
    private val styles = HashMap<String, StyleDef>()
    private var defaultPara = ParaProps()
    private var defaultRun = RunProps(sizePt = 10f)
    private var defaultParaStyle: String? = null
    private val resolvedCache = HashMap<String, Resolved>()

    private val abstractLevels = HashMap<String, Map<Int, Level>>()
    private val numToAbstract = HashMap<String, String>()
    private val startOverrides = HashMap<Pair<String, Int>, Int>()
    private val counters = HashMap<String, IntArray>()

    private val footnoteDefs = HashMap<String, Element>()
    private val footnotesOut = StringBuilder()
    private var footnoteCount = 0
    private var currentFootnote = 0

    private var imageCount = 0
    /** pPr del estilo de la tabla en curso (p. ej. "Tabla con cuadrícula" quita el espacio entre párrafos). */
    private var tableParaOverride: ParaProps? = null

    init {
        val theme = xml("word/theme/theme1.xml")
        minorFont = theme?.deep("a:minorFont")?.kid("a:latin")?.attr("typeface")
        majorFont = theme?.deep("a:majorFont")?.kid("a:latin")?.attr("typeface")
        parseStyles()
        parseNumbering()
        xml("word/footnotes.xml")?.kids("w:footnote")?.forEach { fn ->
            fn.attr("w:id")?.let { footnoteDefs[it] = fn }
        }
    }

    fun convert(): String {
        val body = xml("word/document.xml")?.kid("w:body") ?: error("Documento vacío")
        val sect = body.kid("w:sectPr")
        val pgSz = sect?.kid("w:pgSz")
        val pgMar = sect?.kid("w:pgMar")
        val width = pgSz?.twips("w:w") ?: 612f
        val top = pgMar?.twips("w:top") ?: 72f
        val right = pgMar?.twips("w:right") ?: 72f
        val bottom = pgMar?.twips("w:bottom") ?: 72f
        val left = pgMar?.twips("w:left") ?: 72f

        val out = StringBuilder()
        out.append("<article id=\"page\" data-w=\"").append(num(width)).append("\" style=\"width:")
            .append(num(width)).append("pt;padding:").append(num(top)).append("pt ").append(num(right))
            .append("pt ").append(num(bottom)).append("pt ").append(num(left)).append("pt\">")
        renderBlocks(body.kids(), out)
        if (footnotesOut.isNotEmpty()) {
            out.append("<hr class=\"fn-sep\">").append(footnotesOut)
        }
        if (out.endsWith("\">")) out.append("<p><br></p>")
        return out.append("</article>").toString()
    }

    // ---------- Bloques ----------

    private fun renderBlocks(elements: List<Element>, out: StringBuilder) {
        for (el in elements) when (el.tagName) {
            "w:p" -> renderParagraph(el, out)
            "w:tbl" -> renderTable(el, out)
            "w:sdt" -> el.kid("w:sdtContent")?.let { renderBlocks(it.kids(), out) }
            "w:customXml", "w:ins", "w:smartTag" -> renderBlocks(el.kids(), out)
            "mc:AlternateContent" -> alternate(el)?.let { renderBlocks(it.kids(), out) }
        }
    }

    private fun renderParagraph(p: Element, out: StringBuilder) {
        val pPr = p.kid("w:pPr")
        val style = resolve(pPr?.kid("w:pStyle")?.attr("w:val") ?: defaultParaStyle)
        val direct = parseParaProps(pPr)
        val base = defaultPara.merge(tableParaOverride).merge(style.para)
        var para = base.merge(direct)

        var prefix: String? = null
        var prefixRun: RunProps? = null
        val numId = para.numId
        if (numId != null && numId != "0") {
            val ilvl = para.ilvl ?: 0
            val level = numToAbstract[numId]?.let { abstractLevels[it] }?.get(ilvl)
            if (level != null) {
                prefix = nextNumber(numId, ilvl)
                prefixRun = level.run
                para = base.merge(level.para).merge(direct)
            }
        }

        val baseRun = defaultRun.merge(style.run)
        val heading = style.heading ?: para.outline
        val tag = heading?.let { "h${it.coerceIn(1, 6)}" } ?: "p"

        val inner = StringBuilder()
        val after = StringBuilder()
        if (prefix != null) {
            val css = runCss(baseRun.merge(prefixRun), baseRun)
            inner.append(if (css.isEmpty()) "<span>" else "<span style=\"$css\">").append(escapeHtml(prefix)).append("</span>\t")
        }
        renderInline(p.kids(), baseRun, inner, after)

        if (para.pageBreakBefore == true) out.append(PAGE_BREAK)
        out.append('<').append(tag).append(" style=\"").append(paraCss(para, baseRun)).append("\">")
        if (inner.isEmpty()) inner.append("<br>")
        else if (inner.endsWith("<br>")) inner.append("<br>") // un <br> final no ocupa línea en HTML
        out.append(inner).append("</").append(tag).append('>').append(after)
    }

    private fun renderTable(tbl: Element, out: StringBuilder) {
        val tblPr = tbl.kid("w:tblPr")
        val tableStyle = tblPr?.kid("w:tblStyle")?.attr("w:val")?.let { resolve(it) }
        val borders = bordersOn(tblPr?.kid("w:tblBorders")) ?: tableStyle?.tableBorders ?: false
        val grid = tbl.kid("w:tblGrid")?.kids("w:gridCol")?.map { it.twips("w:w") ?: 0f }.orEmpty()

        class Cell(val el: Element, val col: Int, val span: Int, val restart: Boolean, val merged: Boolean) {
            var rowspan = 1
        }

        val rows = tbl.kids("w:tr").map { tr ->
            var col = tr.kid("w:trPr")?.kid("w:gridBefore")?.attr("w:val")?.toIntOrNull() ?: 0
            tr.kids("w:tc").map { tc ->
                val tcPr = tc.kid("w:tcPr")
                val span = tcPr?.kid("w:gridSpan")?.attr("w:val")?.toIntOrNull() ?: 1
                val vMerge = tcPr?.kid("w:vMerge")
                val restart = vMerge?.attr("w:val") == "restart"
                Cell(tc, col, span, restart, merged = vMerge != null && !restart).also { col += span }
            }
        }
        // Celdas combinadas verticalmente: la primera crece y las siguientes no se dibujan
        val open = HashMap<Int, Cell>()
        for (row in rows) for (cell in row) {
            when {
                cell.merged -> open[cell.col]?.let { it.rowspan++ }
                cell.restart -> open[cell.col] = cell
                else -> open.remove(cell.col)
            }
        }

        val align = tblPr?.kid("w:jc")?.attr("w:val")
        out.append("<table style=\"")
        if (grid.isNotEmpty()) out.append("width:").append(num(grid.sum())).append("pt;")
        when (align) {
            "center" -> out.append("margin-left:auto;margin-right:auto;")
            "right", "end" -> out.append("margin-left:auto;")
        }
        tblPr?.kid("w:tblInd")?.twips("w:w")?.let { out.append("margin-left:").append(num(it)).append("pt;") }
        out.append("\">")
        if (grid.isNotEmpty()) {
            out.append("<colgroup>")
            grid.forEach { out.append("<col style=\"width:").append(num(it)).append("pt\">") }
            out.append("</colgroup>")
        }

        val previous = tableParaOverride
        tableParaOverride = tableStyle?.para
        for (row in rows) {
            out.append("<tr>")
            for (cell in row) {
                if (cell.merged) continue
                val tcPr = cell.el.kid("w:tcPr")
                out.append("<td")
                if (cell.span > 1) out.append(" colspan=\"").append(cell.span).append('"')
                if (cell.rowspan > 1) out.append(" rowspan=\"").append(cell.rowspan).append('"')
                out.append(" style=\"")
                val cellBorders = bordersOn(tcPr?.kid("w:tcBorders")) ?: borders
                if (cellBorders) out.append("border:0.5pt solid #000;")
                tcPr?.kid("w:shd")?.attr("w:fill")?.let(::hexColor)?.let { out.append("background-color:").append(it).append(';') }
                when (tcPr?.kid("w:vAlign")?.attr("w:val")) {
                    "center" -> out.append("vertical-align:middle;")
                    "bottom" -> out.append("vertical-align:bottom;")
                }
                out.append("\">")
                val start = out.length
                renderBlocks(cell.el.kids(), out)
                if (out.length == start) out.append("<p><br></p>")
                out.append("</td>")
            }
            out.append("</tr>")
        }
        tableParaOverride = previous
        out.append("</table>")
    }

    // ---------- Texto ----------

    private fun renderInline(elements: List<Element>, baseRun: RunProps, out: StringBuilder, after: StringBuilder) {
        for (el in elements) when (el.tagName) {
            "w:r" -> renderRun(el, baseRun, out, after)
            "w:hyperlink" -> {
                val href = el.attr("r:id")?.let { activeRels[it] }?.takeIf { it.external }?.target?.takeIf(::isSafeUrl)
                if (href != null) out.append("<a href=\"").append(escapeHtml(href)).append("\">")
                renderInline(el.kids(), baseRun, out, after)
                if (href != null) out.append("</a>")
            }
            "w:ins", "w:smartTag", "w:customXml", "w:fldSimple", "w:dir", "w:bdo", "w:moveTo" ->
                renderInline(el.kids(), baseRun, out, after)
            "w:sdt" -> el.kid("w:sdtContent")?.let { renderInline(it.kids(), baseRun, out, after) }
            "mc:AlternateContent" -> alternate(el)?.let { renderInline(it.kids(), baseRun, out, after) }
        }
    }

    private fun renderRun(r: Element, baseRun: RunProps, out: StringBuilder, after: StringBuilder) {
        val rPr = r.kid("w:rPr")
        val charStyle = rPr?.kid("w:rStyle")?.attr("w:val")?.let { resolve(it).run }
        val run = baseRun.merge(charStyle).merge(parseRunProps(rPr))
        if (run.hidden == true) return

        val text = StringBuilder()
        fun flush() {
            if (text.isEmpty()) return
            out.append(wrapRun(escapeHtml(text), run, baseRun))
            text.setLength(0)
        }

        for (k in r.kids()) when (k.tagName) {
            "w:t" -> text.append(k.textContent)
            "w:tab", "w:ptab" -> text.append('\t')
            "w:noBreakHyphen" -> text.append('‑')
            "w:sym" -> text.append(symbol(k.attr("w:char")))
            "w:br", "w:cr" -> {
                flush()
                if (k.attr("w:type") == "page") after.append(PAGE_BREAK) else out.append("<br>")
            }
            "w:drawing" -> { flush(); renderDrawing(k, out, after) }
            "w:pict", "w:object" -> { flush(); renderVml(k, out) }
            "mc:AlternateContent" -> {
                flush()
                alternate(k)?.kids()?.forEach { c ->
                    when (c.tagName) {
                        "w:drawing" -> renderDrawing(c, out, after)
                        "w:pict" -> renderVml(c, out)
                    }
                }
            }
            "w:footnoteReference" -> {
                flush()
                val n = ++footnoteCount
                out.append("<sup>").append(n).append("</sup>")
                k.attr("w:id")?.let { renderFootnote(it, n) }
            }
            "w:footnoteRef" -> text.append(currentFootnote.toString())
        }
        flush()
    }

    private fun renderFootnote(id: String, number: Int) {
        val def = footnoteDefs[id] ?: return
        val previousRels = activeRels
        activeRels = footnoteRels
        currentFootnote = number
        footnotesOut.append("<div class=\"fn\">")
        renderBlocks(def.kids(), footnotesOut)
        footnotesOut.append("</div>")
        activeRels = previousRels
    }

    private fun renderDrawing(drawing: Element, out: StringBuilder, after: StringBuilder) {
        val container = drawing.kid("wp:inline") ?: drawing.kid("wp:anchor") ?: return
        val extent = container.kid("wp:extent")
        val w = extent?.attr("cx")?.toFloatOrNull()?.div(EMU_PER_PT)
        val h = extent?.attr("cy")?.toFloatOrNull()?.div(EMU_PER_PT)

        container.deep("w:txbxContent")?.let { box ->
            after.append("<div class=\"textbox\"")
            if (w != null) after.append(" style=\"width:").append(num(w)).append("pt\"")
            after.append('>')
            renderBlocks(box.kids(), after)
            after.append("</div>")
            return
        }
        val rid = container.deep("a:blip")?.attr("r:embed") ?: return
        appendImage(rid, w, h, out)
    }

    private fun renderVml(pict: Element, out: StringBuilder) {
        val image = pict.deep("v:imagedata") ?: return
        val rid = image.attr("r:id") ?: return
        val style = (image.parentNode as? Element)?.attr("style").orEmpty()
        fun dim(name: String) = Regex("""$name:\s*([\d.]+)pt""").find(style)?.groupValues?.get(1)?.toFloatOrNull()
        appendImage(rid, dim("width"), dim("height"), out)
    }

    private fun appendImage(rid: String, w: Float?, h: Float?, out: StringBuilder) {
        val rel = activeRels[rid]?.takeIf { !it.external } ?: return
        val path = resolvePath(rel.target)
        val bytes = zip[path] ?: return
        val ext = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext !in WEB_IMAGES) {
            out.append("<span class=\"noimg\">[imagen ").append(escapeHtml(ext)).append("]</span>")
            return
        }
        val name = "img${++imageCount}.$ext"
        File(imagesDir, name).writeBytes(bytes)
        out.append("<img src=\"").append(name).append('"')
        if (w != null && h != null) out.append(" style=\"width:").append(num(w)).append("pt;height:").append(num(h)).append("pt\"")
        out.append('>')
    }

    // ---------- CSS ----------

    private fun paraCss(p: ParaProps, run: RunProps): String {
        val css = StringBuilder()
        p.align?.let { css.append("text-align:").append(it).append(';') }
        css.append("margin:").append(num(p.before ?: 0f)).append("pt ").append(num(p.indRight ?: 0f))
            .append("pt ").append(num(p.after ?: 0f)).append("pt ").append(num(p.indLeft ?: 0f)).append("pt;")
        p.firstLine?.let { css.append("text-indent:").append(num(it)).append("pt;") }
        p.lineHeight?.let { css.append("line-height:").append(it).append(';') }
        p.shading?.let { css.append("background-color:").append(it).append(';') }
        if (p.borderBottom == true) css.append("border-bottom:0.75pt solid #000;padding-bottom:1pt;")
        css.append(fullRunCss(run))
        return css.toString()
    }

    /** Todas las propiedades del texto base del párrafo. */
    private fun fullRunCss(r: RunProps): String {
        val css = StringBuilder()
        r.font?.let { css.append("font-family:").append(fontStack(it)).append(';') }
        r.sizePt?.let { css.append("font-size:").append(num(it)).append("pt;") }
        r.color?.let { css.append("color:").append(it).append(';') }
        css.append("font-weight:").append(if (r.bold == true) "bold" else "normal").append(';')
        css.append("font-style:").append(if (r.italic == true) "italic" else "normal").append(';')
        decoration(r)?.let { css.append("text-decoration:").append(it).append(';') }
        background(r)?.let { css.append("background-color:").append(it).append(';') }
        if (r.caps == true) css.append("text-transform:uppercase;")
        if (r.smallCaps == true) css.append("font-variant:small-caps;")
        return css.toString()
    }

    /** Solo lo que cambia respecto al texto base del párrafo. */
    private fun runCss(r: RunProps, base: RunProps): String {
        val css = StringBuilder()
        if (r.font != base.font && r.font != null) css.append("font-family:").append(fontStack(r.font)).append(';')
        if (r.sizePt != base.sizePt && r.sizePt != null) css.append("font-size:").append(num(r.sizePt)).append("pt;")
        if (r.color != base.color) css.append("color:").append(r.color ?: "inherit").append(';')
        if ((r.bold == true) != (base.bold == true)) css.append("font-weight:").append(if (r.bold == true) "bold" else "normal").append(';')
        if ((r.italic == true) != (base.italic == true)) css.append("font-style:").append(if (r.italic == true) "italic" else "normal").append(';')
        if (decoration(r) != decoration(base)) css.append("text-decoration:").append(decoration(r) ?: "none").append(';')
        if (background(r) != background(base)) background(r)?.let { css.append("background-color:").append(it).append(';') }
        if ((r.caps == true) != (base.caps == true)) css.append("text-transform:").append(if (r.caps == true) "uppercase" else "none").append(';')
        if ((r.smallCaps == true) != (base.smallCaps == true)) css.append("font-variant:").append(if (r.smallCaps == true) "small-caps" else "normal").append(';')
        return css.toString()
    }

    private fun wrapRun(html: String, run: RunProps, base: RunProps): String {
        val css = runCss(run, base)
        var result = if (css.isEmpty()) html else "<span style=\"$css\">$html</span>"
        when (run.vertAlign) {
            "superscript" -> result = "<sup>$result</sup>"
            "subscript" -> result = "<sub>$result</sub>"
        }
        return result
    }

    private fun decoration(r: RunProps): String? {
        val lines = mutableListOf<String>()
        val u = r.underline?.takeIf { it != "none" }
        if (u != null) lines += "underline"
        if (r.strike == true) lines += "line-through"
        if (lines.isEmpty()) return null
        val style = when (u) {
            "double" -> " double"
            "dotted", "dottedHeavy" -> " dotted"
            "dash", "dashLong", "dashedHeavy", "dotDash", "dotDotDash" -> " dashed"
            "wave", "wavyHeavy", "wavyDouble" -> " wavy"
            else -> ""
        }
        return lines.joinToString(" ") + style
    }

    private fun background(r: RunProps) = r.highlight ?: r.shading

    private fun fontStack(name: String): String {
        val n = name.lowercase(Locale.ROOT)
        val generic = when {
            listOf("courier", "consolas", "mono", "menlo", "lucida console").any { it in n } -> "monospace"
            "sans" in n -> "sans-serif"
            listOf("times", "georgia", "garamond", "cambria", "book antiqua", "palatino", "serif", "bookman", "century schoolbook").any { it in n } -> "serif"
            listOf("script", "brush", "comic", "hand", "segoe print").any { it in n } -> "cursive"
            else -> "sans-serif"
        }
        return "'${name.replace("'", "").replace("\"", "")}',$generic"
    }

    // ---------- Estilos ----------

    private fun parseStyles() {
        val root = xml("word/styles.xml") ?: return
        root.kid("w:docDefaults")?.let { dd ->
            defaultPara = parseParaProps(dd.kid("w:pPrDefault")?.kid("w:pPr"))
            defaultRun = RunProps(sizePt = 10f).merge(parseRunProps(dd.kid("w:rPrDefault")?.kid("w:rPr")))
        }
        for (s in root.kids("w:style")) {
            val id = s.attr("w:styleId") ?: continue
            if (s.attr("w:type") == "paragraph" && s.attr("w:default")?.let(::isOn) == true) defaultParaStyle = id
            styles[id] = StyleDef(
                name = s.kid("w:name")?.attr("w:val").orEmpty(),
                basedOn = s.kid("w:basedOn")?.attr("w:val"),
                para = parseParaProps(s.kid("w:pPr")),
                run = parseRunProps(s.kid("w:rPr")),
                tableBorders = bordersOn(s.kid("w:tblPr")?.kid("w:tblBorders")),
            )
        }
    }

    private fun resolve(id: String?, depth: Int = 0): Resolved {
        if (id == null) return EMPTY_STYLE
        resolvedCache[id]?.let { return it }
        val def = styles[id] ?: return EMPTY_STYLE
        val base = if (def.basedOn != null && depth < 20) resolve(def.basedOn, depth + 1) else EMPTY_STYLE
        val resolved = Resolved(
            para = base.para.merge(def.para),
            run = base.run.merge(def.run),
            heading = headingLevel(def.name) ?: base.heading,
            tableBorders = def.tableBorders ?: base.tableBorders,
        )
        resolvedCache[id] = resolved
        return resolved
    }

    private fun headingLevel(name: String): Int? {
        val n = name.lowercase(Locale.ROOT)
        Regex("""^heading (\d)$""").find(n)?.let { return it.groupValues[1].toInt() }
        return if (n == "title") 1 else null
    }

    private fun parseParaProps(el: Element?): ParaProps {
        if (el == null) return ParaProps()
        var p = ParaProps()
        for (k in el.kids()) when (k.tagName) {
            "w:jc" -> p = p.copy(
                align = when (k.attr("w:val")) {
                    "center" -> "center"
                    "right", "end" -> "right"
                    "both", "distribute", "thaiDistribute", "lowKashida", "mediumKashida", "highKashida" -> "justify"
                    "left", "start" -> "left"
                    else -> null
                }
            )
            "w:ind" -> {
                val hanging = k.twips("w:hanging")
                p = p.copy(
                    indLeft = k.twips("w:left") ?: k.twips("w:start") ?: p.indLeft,
                    indRight = k.twips("w:right") ?: k.twips("w:end") ?: p.indRight,
                    firstLine = hanging?.let { -it } ?: k.twips("w:firstLine") ?: p.firstLine,
                )
            }
            "w:spacing" -> {
                val line = k.attr("w:line")?.toFloatOrNull()
                val lineHeight = line?.let {
                    when (k.attr("w:lineRule")) {
                        "exact", "atLeast" -> num(it / 20f) + "pt"
                        else -> num(it / 240f)
                    }
                }
                p = p.copy(
                    before = if (k.attr("w:beforeAutospacing")?.let(::isOn) == true) 14f else k.twips("w:before") ?: p.before,
                    after = if (k.attr("w:afterAutospacing")?.let(::isOn) == true) 14f else k.twips("w:after") ?: p.after,
                    lineHeight = lineHeight ?: p.lineHeight,
                )
            }
            "w:shd" -> p = p.copy(shading = hexColor(k.attr("w:fill")))
            "w:numPr" -> p = p.copy(
                numId = k.kid("w:numId")?.attr("w:val") ?: p.numId,
                ilvl = k.kid("w:ilvl")?.attr("w:val")?.toIntOrNull() ?: p.ilvl,
            )
            "w:outlineLvl" -> k.attr("w:val")?.toIntOrNull()?.let { if (it in 0..5) p = p.copy(outline = it + 1) }
            "w:pageBreakBefore" -> p = p.copy(pageBreakBefore = isOn(k.attr("w:val")))
            "w:pBdr" -> p = p.copy(borderBottom = bordersOn(k.kid("w:bottom")?.let { listOf(it) }))
        }
        return p
    }

    private fun parseRunProps(el: Element?): RunProps {
        if (el == null) return RunProps()
        var r = RunProps()
        for (k in el.kids()) when (k.tagName) {
            "w:b" -> r = r.copy(bold = isOn(k.attr("w:val")))
            "w:i" -> r = r.copy(italic = isOn(k.attr("w:val")))
            "w:u" -> r = r.copy(underline = k.attr("w:val") ?: "single")
            "w:strike", "w:dstrike" -> r = r.copy(strike = isOn(k.attr("w:val")))
            "w:color" -> r = r.copy(color = hexColor(k.attr("w:val")))
            "w:sz" -> k.attr("w:val")?.toFloatOrNull()?.let { r = r.copy(sizePt = it / 2f) }
            "w:rFonts" -> {
                val font = k.attr("w:ascii") ?: k.attr("w:hAnsi")
                    ?: when (k.attr("w:asciiTheme") ?: k.attr("w:hAnsiTheme")) {
                        null -> null
                        "majorHAnsi", "majorAscii", "majorBidi", "majorEastAsia" -> majorFont
                        else -> minorFont
                    }
                if (font != null) r = r.copy(font = font)
            }
            "w:highlight" -> r = r.copy(highlight = HIGHLIGHTS[k.attr("w:val")] ?: if (k.attr("w:val") == "none") "transparent" else null)
            "w:shd" -> r = r.copy(shading = hexColor(k.attr("w:fill")))
            "w:vertAlign" -> r = r.copy(vertAlign = k.attr("w:val"))
            "w:caps" -> r = r.copy(caps = isOn(k.attr("w:val")))
            "w:smallCaps" -> r = r.copy(smallCaps = isOn(k.attr("w:val")))
            "w:vanish" -> r = r.copy(hidden = isOn(k.attr("w:val")))
        }
        return r
    }

    /** true si algún borde es visible, false si todos son "none", null si no se indica nada. */
    private fun bordersOn(el: Element?): Boolean? = el?.let { bordersOn(it.kids()) }

    private fun bordersOn(sides: List<Element>?): Boolean? {
        if (sides.isNullOrEmpty()) return null
        return sides.any { it.attr("w:val") !in setOf(null, "none", "nil") }
    }

    // ---------- Numeración ----------

    private fun parseNumbering() {
        val root = xml("word/numbering.xml") ?: return
        for (abs in root.kids("w:abstractNum")) {
            val id = abs.attr("w:abstractNumId") ?: continue
            abstractLevels[id] = abs.kids("w:lvl").associate { lvl ->
                (lvl.attr("w:ilvl")?.toIntOrNull() ?: 0) to Level(
                    start = lvl.kid("w:start")?.attr("w:val")?.toIntOrNull() ?: 1,
                    format = lvl.kid("w:numFmt")?.attr("w:val") ?: "decimal",
                    text = lvl.kid("w:lvlText")?.attr("w:val") ?: "",
                    para = parseParaProps(lvl.kid("w:pPr")),
                    run = parseRunProps(lvl.kid("w:rPr")),
                )
            }
        }
        for (n in root.kids("w:num")) {
            val id = n.attr("w:numId") ?: continue
            numToAbstract[id] = n.kid("w:abstractNumId")?.attr("w:val") ?: continue
            for (o in n.kids("w:lvlOverride")) {
                val ilvl = o.attr("w:ilvl")?.toIntOrNull() ?: continue
                o.kid("w:startOverride")?.attr("w:val")?.toIntOrNull()?.let { startOverrides[id to ilvl] = it }
            }
        }
    }

    private fun nextNumber(numId: String, ilvl: Int): String {
        val levels = numToAbstract[numId]?.let { abstractLevels[it] } ?: return ""
        fun start(l: Int) = startOverrides[numId to l] ?: levels[l]?.start ?: 1
        val values = counters.getOrPut(numId) { IntArray(9) }
        val i = ilvl.coerceIn(0, 8)
        values[i] = if (values[i] == 0) start(i) else values[i] + 1
        for (j in i + 1 until 9) values[j] = 0

        val level = levels[i] ?: return ""
        if (level.format == "bullet") return bullet(level.text)
        if (level.format == "none") return ""
        return Regex("%(\\d)").replace(level.text) { m ->
            val l = m.groupValues[1].toInt() - 1
            val v = if (l in 0..8) values[l].takeIf { it > 0 } ?: start(l) else 1
            formatNumber(v, levels[l]?.format ?: "decimal")
        }
    }

    private fun formatNumber(n: Int, format: String): String = when (format) {
        "decimalZero" -> n.toString().padStart(2, '0')
        "lowerLetter" -> letters(n).lowercase(Locale.ROOT)
        "upperLetter" -> letters(n)
        "lowerRoman" -> roman(n).lowercase(Locale.ROOT)
        "upperRoman" -> roman(n)
        "ordinal" -> "$n.º"
        else -> n.toString()
    }

    private fun letters(n: Int): String {
        if (n <= 0) return ""
        val c = 'A' + (n - 1) % 26
        return c.toString().repeat((n - 1) / 26 + 1)
    }

    private fun roman(n: Int): String {
        var v = n
        val out = StringBuilder()
        for ((value, sym) in ROMAN) while (v >= value) { out.append(sym); v -= value }
        return out.toString()
    }

    // ---------- XML ----------

    private fun xml(name: String): Element? {
        val bytes = zip[name] ?: return null
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        return runCatching { factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement }.getOrNull()
    }

    private fun parseRels(name: String): Map<String, Rel> =
        xml(name)?.kids("Relationship")?.mapNotNull { r ->
            val id = r.attr("Id") ?: return@mapNotNull null
            id to Rel(r.attr("Target") ?: return@mapNotNull null, r.attr("TargetMode") == "External")
        }?.toMap().orEmpty()

    private fun resolvePath(target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val parts = ArrayDeque(listOf("word"))
        for (seg in target.split('/')) when (seg) {
            "..", "" -> if (seg == "..") parts.removeLastOrNull()
            "." -> {}
            else -> parts.addLast(seg)
        }
        return parts.joinToString("/")
    }

    /** mc:AlternateContent: se usa la versión moderna si existe. */
    private fun alternate(el: Element): Element? = el.kid("mc:Choice") ?: el.kid("mc:Fallback")

    companion object {
        const val PAGE_BREAK = "<hr class=\"pb\">"
        const val EMU_PER_PT = 12700f
        val EMPTY_STYLE = Resolved(ParaProps(), RunProps(), null, null)
        val WEB_IMAGES = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "svg")
        val ROMAN = listOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
            50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
        )
        val HIGHLIGHTS = mapOf(
            "yellow" to "#FFFF00", "green" to "#00FF00", "cyan" to "#00FFFF", "magenta" to "#FF00FF",
            "blue" to "#0000FF", "red" to "#FF0000", "darkBlue" to "#000080", "darkCyan" to "#008080",
            "darkGreen" to "#008000", "darkMagenta" to "#800080", "darkRed" to "#800000",
            "darkYellow" to "#808000", "darkGray" to "#808080", "lightGray" to "#C0C0C0", "black" to "#000000",
        )
    }
}

// ---------- Utilidades ----------

private fun Element.kids(): List<Element> {
    val nodes = childNodes
    val out = ArrayList<Element>(nodes.length)
    for (i in 0 until nodes.length) (nodes.item(i) as? Element)?.let(out::add)
    return out
}

private fun Element.kids(name: String) = kids().filter { it.tagName == name }
private fun Element.kid(name: String) = kids().firstOrNull { it.tagName == name }
private fun Element.deep(name: String) = getElementsByTagName(name).item(0) as? Element
private fun Element.attr(name: String): String? = getAttribute(name).ifEmpty { null }
private fun Element.twips(name: String): Float? = attr(name)?.toFloatOrNull()?.div(20f)

/** <w:b/> está activo; w:val="0", "false" u "off" lo desactiva. */
private fun isOn(value: String?) = value == null || value.lowercase(Locale.ROOT) !in setOf("0", "false", "off", "none")

private fun hexColor(value: String?): String? =
    value?.takeIf { it.length == 6 && it.all { c -> c.isLetterOrDigit() } && it.lowercase(Locale.ROOT) != "auto" }?.let { "#$it" }

private fun num(v: Float): String =
    if (v == v.toInt().toFloat()) v.toInt().toString() else String.format(Locale.ROOT, "%.2f", v).trimEnd('0').trimEnd('.')

private fun isSafeUrl(url: String) = url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")

/** Viñetas de Word en fuentes Symbol/Wingdings (área privada de Unicode) → caracteres normales. */
private fun bullet(text: String): String = when (text.firstOrNull()) {
    null -> "•"
    '', '·' -> "•"
    'o' -> "◦"
    '', '§' -> "▪"
    '' -> "➢"
    '' -> "❖"
    '' -> "✓"
    '' -> "□"
    else -> if (text[0] in ''..'') "•" else text
}

private fun symbol(hex: String?): String {
    val code = hex?.toIntOrNull(16) ?: return ""
    return bullet(code.toChar().toString())
}
