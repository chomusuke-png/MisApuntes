package com.estudia.app.importers

import com.estudia.app.rich.RichDocument
import com.estudia.app.rich.SpanData
import com.estudia.app.rich.SpanType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImportersTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun resource(name: String) = File(javaClass.classLoader!!.getResource(name).toURI())

    /** Deja el HTML generado en build/ para poder revisarlo a mano. */
    private fun dump(name: String, html: String) {
        File("build/test-html").apply { mkdirs() }.resolve(name).writeText(html)
    }

    private fun assertContains(html: String, vararg fragments: String) {
        for (f in fragments) assertTrue("Falta «$f» en:\n$html", html.contains(f))
    }

    @Test
    fun docx_keepsFormattingTablesListsAndImages() {
        val images = tmp.newFolder("img")
        val html = DocxImporter.import(resource("sample.docx"), images)
        dump("sample-docx.html", html)

        assertTrue(html.startsWith("<article id=\"page\" data-w="))
        assertTrue(Regex("<h1[^>]*>Capitulo uno</h1>").containsMatchIn(html))
        assertContains(html, "canción ñandú")
        assertTrue(Regex("<span style=\"[^\"]*font-weight:bold[^\"]*\">negrita</span>").containsMatchIn(html))
        assertTrue(Regex("<span style=\"[^\"]*background-color:#FFFF00[^\"]*\">resaltado</span>").containsMatchIn(html))
        assertTrue(Regex("color:#FF0000[^\"]*font-size:16pt|font-size:16pt[^\"]*color:#FF0000").containsMatchIn(html))
        assertTrue(Regex("<p style=\"text-align:center;[^\"]*\">Centrado</p>").containsMatchIn(html))
        assertContains(html, "•</span>\tPrimera viñeta", "1.</span>\tPaso uno")
        assertContains(html, "<table", "border:0.5pt solid #000", ">A1<", ">B2<")
        assertContains(html, "<img src=\"img1.png\"")
        assertTrue(File(images, "img1.png").exists())
    }

    @Test
    fun doc_keepsFormattingAndTables() {
        val html = DocImporter.import(resource("sample.doc"))
        dump("sample-doc.html", html)

        assertTrue(Regex("<h1[^>]*>Capitulo uno</h1>").containsMatchIn(html))
        assertContains(html, "canción ñandú")
        assertTrue(Regex("<span style=\"[^\"]*font-weight:bold[^\"]*\">negrita</span>").containsMatchIn(html))
        assertTrue(Regex("<span style=\"[^\"]*background-color:#FFFF00[^\"]*\">resaltado</span>").containsMatchIn(html))
        assertTrue(Regex("<p style=\"text-align:center;[^\"]*\">Centrado</p>").containsMatchIn(html))
        assertContains(html, "font-size:16pt", "#FF0000", "font-family:'Aptos',sans-serif")
        assertContains(html, "<table class=\"doc\"><tr><td>", ">A1<", ">B2<", "</td></tr></table>")
    }

    @Test
    fun markdown_toHtml() {
        val html = MarkdownImporter.parse(
            """
            # Título
            Texto con **negrita**, *cursiva*, ~~tachado~~ y ==clave==.
            - item
            """.trimIndent()
        ).toHtml()
        assertEquals(
            "<article id=\"page\"><h1>Título</h1><p>Texto con <span style=\"font-weight:bold\">negrita</span>, " +
                "<span style=\"font-style:italic\">cursiva</span>, <span style=\"text-decoration:line-through\">tachado</span> " +
                "y <span style=\"background-color:#FFF59D\">clave</span>.</p><p>• item</p></article>",
            html,
        )
    }

    @Test
    fun plainText_isEscaped() {
        assertEquals(
            "<article id=\"page\"><p>a &lt;b&gt; &amp; c</p><p><br></p><p>fin</p></article>",
            RichDocument.plain("a <b> & c\n\nfin").toHtml(),
        )
    }

    @Test
    fun noteBody_plainTextForSearch() {
        assertEquals(
            "Título\nUno & dos <tres>\nfin",
            com.estudia.app.data.htmlToPlainText(
                "<article id=\"page\"><h1>Título</h1><p>Uno &amp; <b>dos</b> &lt;tres&gt;</p><p><img src=\"a.jpg\"><br></p><p>fin</p></article>"
            ),
        )
    }

    @Test
    fun richDocument_jsonRoundTripMergesAdjacentSpans() {
        val doc = RichDocument(
            text = "hola mundo",
            spans = listOf(SpanData(SpanType.BOLD, 0, 2), SpanData(SpanType.BOLD, 2, 4)),
        )
        val back = RichDocument.fromJson(doc.toJson())
        assertEquals(listOf(SpanData(SpanType.BOLD, 0, 4)), back.spans)
    }
}
