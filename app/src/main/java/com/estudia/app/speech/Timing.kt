package com.estudia.app.speech

/**
 * oraciones del texto, cortadas tambien en comas o espacios si son muy largas
 * la voz natural genera el audio de a una, y una oracion corta empieza a sonar antes
 */
fun sentenceSpans(text: String, max: Int = 220): List<IntRange> {
    val spans = mutableListOf<IntRange>()
    var start = text.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return spans
    val ends = Regex("[.!?…;:](?=\\s|$)").findAll(text).map { it.range.last + 1 }.toList() + text.length
    var e = 0
    while (start < text.length) {
        while (e < ends.size && ends[e] <= start) e++
        var end = ends.getOrElse(e) { text.length }
        if (end - start > max) {
            // muy larga: se corta en la ultima coma o espacio antes del maximo
            val window = text.substring(start, start + max)
            val cut = maxOf(window.lastIndexOf(", "), window.lastIndexOf("; "))
                .takeIf { it > max / 3 }?.plus(1)
                ?: window.lastIndexOf(' ').takeIf { it > 0 }
                ?: max
            end = start + cut
        }
        val trimmed = text.substring(start, end).trimEnd()
        if (trimmed.isNotEmpty()) spans += start until start + trimmed.length
        start = end
        while (start < text.length && text[start].isWhitespace()) start++
    }
    return spans
}

/** palabras de un pedazo de texto, con sus posiciones */
fun wordSpans(text: String, offset: Int = 0): List<IntRange> =
    Regex("\\S+").findAll(text).map { (it.range.first + offset)..(it.range.last + offset) }.toList()

/**
 * cuanto dura cada palabra, en proporcion: las letras y las pausas de la puntuacion
 * sirve para estimar que palabra suena cuando la voz no lo avisa
 */
fun wordWeights(text: String, words: List<IntRange>, offset: Int = 0): FloatArray = FloatArray(words.size) { i ->
    val word = text.substring(words[i].first - offset, words[i].last - offset + 1)
    val pause = when (word.last()) {
        '.', '!', '?', '…', ':', ';' -> 4f
        ',' -> 2f
        else -> 0f
    }
    word.count { it.isLetterOrDigit() }.coerceAtLeast(1) + 1f + pause
}

/** palabra que suena cuando va progress (de 0 a 1) del audio de una oracion */
fun wordAt(weights: FloatArray, progress: Float): Int {
    if (weights.isEmpty()) return -1
    val target = progress.coerceIn(0f, 1f) * weights.sum()
    var sum = 0f
    for (i in weights.indices) {
        sum += weights[i]
        if (target < sum) return i
    }
    return weights.lastIndex
}
