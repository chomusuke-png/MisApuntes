package com.estudia.app.data

import kotlin.math.exp
import kotlin.math.hypot

/**
 * suaviza un trazo con curvas cuadraticas que pasan por los puntos medios
 * devuelve cx, cy, x, y por cada tramo, partiendo desde el primer punto del trazo
 */
fun smoothCurves(points: List<Float>): FloatArray {
    val count = points.size / 2
    if (count < 2) return FloatArray(0)
    val curves = FloatArray((count - 1) * 4)
    for (i in 1 until count) {
        val x = points[i * 2]
        val y = points[i * 2 + 1]
        val last = i == count - 1
        val o = (i - 1) * 4
        curves[o] = x
        curves[o + 1] = y
        curves[o + 2] = if (last) x else (x + points[i * 2 + 2]) / 2
        curves[o + 3] = if (last) y else (y + points[i * 2 + 3]) / 2
    }
    return curves
}

/**
 * corta el trazo suavizado en tramos rectos cortos, cada uno con su grosor
 * devuelve x0, y0, x1, y1 y el factor de grosor por cada tramo
 * widths trae un factor por punto del trazo
 */
fun widthSegments(points: List<Float>, widths: List<Float>, steps: Int = 4): FloatArray {
    val curves = smoothCurves(points)
    val count = curves.size / 4
    val segments = FloatArray(count * steps * 5)
    var sx = points[0]
    var sy = points[1]
    var sw = widths[0]
    var o = 0
    for (c in 0 until count) {
        val cx = curves[c * 4]
        val cy = curves[c * 4 + 1]
        val ex = curves[c * 4 + 2]
        val ey = curves[c * 4 + 3]
        // el punto de control de la curva c es el punto c + 1 del trazo
        val ew = if (c == count - 1) widths[c + 1] else (widths[c + 1] + widths[c + 2]) / 2
        var px = sx
        var py = sy
        for (s in 1..steps) {
            val t = s.toFloat() / steps
            val u = 1 - t
            val x = u * u * sx + 2 * u * t * cx + t * t * ex
            val y = u * u * sy + 2 * u * t * cy + t * t * ey
            segments[o] = px
            segments[o + 1] = py
            segments[o + 2] = x
            segments[o + 3] = y
            segments[o + 4] = sw + (ew - sw) * (t - 0.5f / steps)
            o += 5
            px = x
            py = y
        }
        sx = ex
        sy = ey
        sw = ew
    }
    return segments
}

/**
 * borra la parte del trazo que cae dentro de un circulo y devuelve los pedazos que quedan
 * devuelve null si el circulo no toca el trazo. Las medidas van en px sobre una pagina de w por h
 */
fun PdfStroke.erase(x: Float, y: Float, radius: Float, w: Float, h: Float): List<PdfStroke>? {
    val count = points.size / 2
    if (count == 0) return null
    val reach = radius + width * w / 2
    val varies = widths.size == count
    // puntos seguidos a no mas de medio alcance, para que el corte quede justo en el borde del circulo
    val step = (reach / 2).coerceAtLeast(1f)
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    val ws = ArrayList<Float>()
    for (i in 0 until count) {
        val px = points[i * 2] * w
        val py = points[i * 2 + 1] * h
        if (i > 0) {
            val gap = hypot(px - xs.last(), py - ys.last())
            val extra = (gap / step).toInt()
            val fromX = xs.last()
            val fromY = ys.last()
            val fromW = ws.last()
            for (k in 1..extra) {
                val t = k / (extra + 1f)
                xs += fromX + (px - fromX) * t
                ys += fromY + (py - fromY) * t
                ws += fromW + ((if (varies) widths[i] else 1f) - fromW) * t
            }
        }
        xs += px
        ys += py
        ws += if (varies) widths[i] else 1f
    }
    val inside = BooleanArray(xs.size) { hypot(xs[it] - x, ys[it] - y) <= reach }
    if (inside.none { it }) return null

    val pieces = ArrayList<PdfStroke>()
    var from = 0
    for (i in 0..xs.size) {
        if (i < xs.size && !inside[i]) continue
        // un punto suelto no alcanza a ser un trazo
        if (i - from >= 2) {
            val keep = if (straight) listOf(from, i - 1) else (from until i).toList()
            pieces += copy(
                points = keep.flatMap { listOf(xs[it] / w, ys[it] / h) },
                widths = if (varies) keep.map { ws[it] } else emptyList(),
            )
        }
        from = i + 1
    }
    return pieces
}

/**
 * aprende el rango de presion que entrega el dedo en este dispositivo
 * cada pantalla usa un rango distinto, y muchas entregan siempre el mismo valor
 */
class PressureRange(min: Float = 1f, max: Float = 0f) {
    var min = min
        private set
    var max = max
        private set

    private var strokeMin = 1f
    private var strokeMax = 0f

    /** devuelve la presion de 0 a 1, o null mientras el dispositivo no muestre que la distingue */
    fun normalize(pressure: Float): Float? {
        val p = pressure.coerceIn(0f, 1f)
        if (p < min) min = p
        if (p > max) max = p
        if (p < strokeMin) strokeMin = p
        if (p > strokeMax) strokeMax = p
        return if (max - min < MIN_SPREAD) null else (p - min) / (max - min)
    }

    /** acerca el rango al del trazo recien hecho, asi un valor extremo aislado se olvida con el uso */
    fun endStroke() {
        if (strokeMax >= strokeMin) {
            min += (strokeMin - min) * FORGET
            max += (strokeMax - max) * FORGET
        }
        strokeMin = 1f
        strokeMax = 0f
    }

    private companion object {
        const val MIN_SPREAD = 0.1f
        const val FORGET = 0.05f
    }
}

/**
 * sigue al dedo con un punto estabilizado y calcula el grosor del lapiz
 * stabilizer va de 0 (sin estabilizar) a 1, las posiciones en px y el tiempo en ms
 * pressure va de 0 a 1 y solo se pasa si el dispositivo la mide
 */
class PenInput(
    private val stabilizer: Float,
    private val pxPerDp: Float,
    var x: Float,
    var y: Float,
    private var time: Long,
    pressure: Float? = null,
) {
    /** factor de grosor: con presion, mas grueso al apretar. Sin ella, mas fino al ir rapido */
    var width = pressure?.let(::pressureWidth) ?: START_WIDTH
        private set

    fun move(toX: Float, toY: Float, at: Long, pressure: Float? = null) {
        val dt = (at - time).coerceAtLeast(1).toFloat()
        val follow = if (stabilizer <= 0f) 1f else 1f - exp(-dt / (stabilizer * MAX_LAG_MS))
        val nx = x + (toX - x) * follow
        val ny = y + (toY - y) * follow
        val speed = hypot(nx - x, ny - y) / pxPerDp / dt
        val target = pressure?.let(::pressureWidth)
            ?: (MAX_WIDTH - (MAX_WIDTH - MIN_WIDTH) * (speed / FAST_SPEED).coerceIn(0f, 1f))
        // el grosor cambia de a poco para que no se vean saltos
        width += (target - width) * (1f - exp(-dt / WIDTH_LAG_MS))
        x = nx
        y = ny
        time = at
    }

    private fun pressureWidth(pressure: Float) = MIN_WIDTH + (MAX_WIDTH - MIN_WIDTH) * pressure.coerceIn(0f, 1f)

    private companion object {
        const val START_WIDTH = 0.9f
        const val MIN_WIDTH = 0.45f
        const val MAX_WIDTH = 1.35f
        const val FAST_SPEED = 1.5f // dp por ms
        const val MAX_LAG_MS = 60f
        const val WIDTH_LAG_MS = 50f
    }
}
