package com.estudia.app.importers

import android.content.Context
import com.estudia.app.data.PdfStroke
import com.estudia.app.data.smoothCurves
import com.estudia.app.data.widthSegments
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import java.io.File
import java.io.OutputStream

/** Dibuja las anotaciones dentro del PDF: el destacador en modo "multiplicar", como un marcador real, y el lápiz opaco. */
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
                                blendMode = if (s.pen) BlendMode.NORMAL else BlendMode.MULTIPLY
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
                        if (s.points.size >= 4 && s.widths.size == s.points.size / 2) {
                            val segments = widthSegments(s.points, s.widths)
                            for (i in segments.indices step 5) {
                                val (ax, ay) = toPdf(segments[i], segments[i + 1], box, rotation)
                                val (bx, by) = toPdf(segments[i + 2], segments[i + 3], box, rotation)
                                cs.setLineWidth(s.width * displayWidth * segments[i + 4])
                                cs.moveTo(ax, ay)
                                cs.lineTo(bx, by)
                                cs.stroke()
                            }
                            continue
                        }
                        var (x, y) = toPdf(s.points[0], s.points[1], box, rotation)
                        cs.moveTo(x, y)
                        if (s.points.size < 4) cs.lineTo(x, y)
                        val curves = smoothCurves(s.points)
                        for (i in curves.indices step 4) {
                            val (cx, cy) = toPdf(curves[i], curves[i + 1], box, rotation)
                            val (ex, ey) = toPdf(curves[i + 2], curves[i + 3], box, rotation)
                            // PDF solo tiene curvas cubicas, esta equivale a la cuadratica de la pantalla
                            cs.curveTo(
                                x + (cx - x) * 2 / 3, y + (cy - y) * 2 / 3,
                                ex + (cx - ex) * 2 / 3, ey + (cy - ey) * 2 / 3,
                                ex, ey,
                            )
                            x = ex
                            y = ey
                        }
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
