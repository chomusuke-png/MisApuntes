package com.estudia.app.importers

import android.content.Context
import com.estudia.app.data.PdfStroke
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import java.io.File
import java.io.OutputStream

/** Dibuja los resaltados dentro del PDF (modo "multiplicar", como un marcador real). */
object PdfExporter {

    fun export(context: Context, source: File, strokes: List<PdfStroke>, out: OutputStream) {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(source).use { doc ->
            for ((index, pageStrokes) in strokes.groupBy { it.page }) {
                if (index !in 0 until doc.numberOfPages) continue
                val page = doc.getPage(index)
                val box = page.cropBox
                val rotation = ((page.rotation % 360) + 360) % 360
                val displayWidth = if (rotation == 90 || rotation == 270) box.height else box.width

                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    for (s in pageStrokes) {
                        if (s.points.size < 2) continue
                        cs.setGraphicsStateParameters(
                            PDExtendedGraphicsState().apply {
                                blendMode = BlendMode.MULTIPLY
                                strokingAlphaConstant = s.alpha.coerceIn(0f, 1f)
                            }
                        )
                        cs.setStrokingColor(
                            ((s.color shr 16) and 0xFF) / 255f,
                            ((s.color shr 8) and 0xFF) / 255f,
                            (s.color and 0xFF) / 255f,
                        )
                        cs.setLineWidth(s.width * displayWidth)
                        cs.setLineCapStyle(if (s.straight) 2 else 1)
                        cs.setLineJoinStyle(1)
                        val pts = s.points.chunked(2).map { (x, y) -> toPdf(x, y, box, rotation) }
                        cs.moveTo(pts[0].first, pts[0].second)
                        if (pts.size == 1) cs.lineTo(pts[0].first, pts[0].second)
                        for (p in pts.drop(1)) cs.lineTo(p.first, p.second)
                        cs.stroke()
                    }
                }
            }
            doc.save(out)
        }
    }

    /**
     * Pasa coordenadas normalizadas de la página tal como se ve (origen arriba a la izquierda,
     * ya rotada) al espacio del PDF (origen abajo a la izquierda, sin rotar).
     */
    private fun toPdf(nx: Float, ny: Float, box: PDRectangle, rotation: Int): Pair<Float, Float> {
        val x0 = box.lowerLeftX
        val y0 = box.lowerLeftY
        val w = box.width
        val h = box.height
        return when (rotation) {
            90 -> (x0 + ny * w) to (y0 + nx * h)
            180 -> (x0 + (1 - nx) * w) to (y0 + ny * h)
            270 -> (x0 + (1 - ny) * w) to (y0 + (1 - nx) * h)
            else -> (x0 + nx * w) to (y0 + (1 - ny) * h)
        }
    }
}
