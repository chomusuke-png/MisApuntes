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
 * sigue al dedo con un punto estabilizado y calcula el grosor del lapiz segun la velocidad
 * stabilizer va de 0 (sin estabilizar) a 1, las posiciones en px y el tiempo en ms
 */
class PenInput(private val stabilizer: Float, private val pxPerDp: Float, var x: Float, var y: Float, private var time: Long) {
    /** factor de grosor: mas fino al ir rapido y mas grueso al ir lento */
    var width = START_WIDTH
        private set

    fun move(toX: Float, toY: Float, at: Long) {
        val dt = (at - time).coerceAtLeast(1).toFloat()
        val follow = if (stabilizer <= 0f) 1f else 1f - exp(-dt / (stabilizer * MAX_LAG_MS))
        val nx = x + (toX - x) * follow
        val ny = y + (toY - y) * follow
        val speed = hypot(nx - x, ny - y) / pxPerDp / dt
        val target = MAX_WIDTH - (MAX_WIDTH - MIN_WIDTH) * (speed / FAST_SPEED).coerceIn(0f, 1f)
        // el grosor cambia de a poco para que no se vean saltos
        width += (target - width) * (1f - exp(-dt / WIDTH_LAG_MS))
        x = nx
        y = ny
        time = at
    }

    private companion object {
        const val START_WIDTH = 0.9f
        const val MIN_WIDTH = 0.45f
        const val MAX_WIDTH = 1.35f
        const val FAST_SPEED = 1.5f // dp por ms
        const val MAX_LAG_MS = 60f
        const val WIDTH_LAG_MS = 50f
    }
}
