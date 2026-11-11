package com.estudia.app.ui.pdf

/**
 * donde esta parada la vista del PDF, como la guarda la lista de paginas
 * index y offset son la pagina de arriba y cuanto se bajo dentro de ella, x es el desplazamiento horizontal
 */
data class PdfView(val index: Int, val offset: Float, val scale: Float, val x: Float)

/**
 * medidas de la lista de paginas, para acercar con el pellizco justo donde estan los dedos
 * ratios es alto / ancho de cada pagina, todo lo demas va en px
 */
class PdfLayout(
    ratios: List<Float>,
    private val viewportWidth: Float,
    private val viewportHeight: Float,
    private val padding: Float,
    private val gap: Float,
) {
    private val ratios = ratios.toFloatArray()

    // alto acumulado de las paginas anteriores a cada una, en anchos de pagina
    private val before = FloatArray(ratios.size + 1).also { sums ->
        for (i in ratios.indices) sums[i + 1] = sums[i] + ratios[i]
    }

    fun pageWidth(scale: Float) = viewportWidth * scale - 2 * padding

    /** donde empieza la pagina dentro del contenido completo */
    fun pageTop(index: Int, scale: Float) = padding + pageWidth(scale) * before[index] + gap * index

    private fun contentHeight(scale: Float) = 2 * padding + pageWidth(scale) * before.last() + gap * (ratios.size - 1)

    private fun scrollOf(view: PdfView) = pageTop(view.index, view.scale) - padding + view.offset

    /** pagina bajo un punto de la pantalla, y en que fraccion de su ancho y alto cae */
    fun pointAt(view: PdfView, x: Float, y: Float): Triple<Int, Float, Float> {
        val contentY = scrollOf(view) + y
        var page = 0
        while (page + 1 < ratios.size && pageTop(page + 1, view.scale) <= contentY) page++
        val width = pageWidth(view.scale)
        return Triple(page, (view.x + x - padding) / width, (contentY - pageTop(page, view.scale)) / (width * ratios[page]))
    }

    /**
     * multiplica el zoom por factor dejando quieto el punto de la pagina que esta en x, y
     * y despues mueve la vista lo que se arrastraron los dedos
     */
    fun zoom(view: PdfView, factor: Float, x: Float, y: Float, panX: Float, panY: Float): PdfView {
        val scale = (view.scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val (page, fx, fy) = pointAt(view, x, y)
        val width = pageWidth(scale)
        val scroll = (pageTop(page, scale) + fy * width * ratios[page] - y - panY)
            .coerceIn(0f, (contentHeight(scale) - viewportHeight).coerceAtLeast(0f))
        val newX = (padding + fx * width - x - panX)
            .coerceIn(0f, (viewportWidth * scale - viewportWidth).coerceAtLeast(0f))
        var index = 0
        while (index + 1 < ratios.size && pageTop(index + 1, scale) - padding <= scroll) index++
        return PdfView(index, scroll - (pageTop(index, scale) - padding), scale, newX)
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 4f
    }
}
