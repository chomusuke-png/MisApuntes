package com.estudia.app.speech

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** lo que devuelve reader.start() del editor: los parrafos y desde cual empezar. null si no hay texto */
fun parseEditorReading(json: String): Pair<List<String>, Int>? {
    val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
    val parts = root["parts"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty().filter { it.isNotBlank() }
    if (parts.isEmpty()) return null
    val start = root["start"]?.jsonPrimitive?.intOrNull ?: 0
    return parts to start.coerceIn(0, parts.lastIndex)
}

/** velocidades que se ofrecen para leer */
val READ_SPEEDS = listOf(0.5f, 0.6f, 0.75f, 0.9f, 1f, 1.25f, 1.5f, 2f)

/** el motor de voz acepta textos de largo limitado, asi que lo largo se corta en oraciones */
fun splitForSpeech(text: String, max: Int = 3000): List<String> {
    val clean = text.replace(Regex("\\s+"), " ").trim()
    if (clean.isEmpty()) return emptyList()
    if (clean.length <= max) return listOf(clean)
    val chunks = mutableListOf<String>()
    val current = StringBuilder()
    fun flush() {
        if (current.isNotBlank()) chunks += current.toString().trim()
        current.clear()
    }
    for (sentence in clean.split(Regex("(?<=[.!?…;:])\\s+"))) {
        var rest = sentence
        // una oracion sin pausas mas larga que el limite se corta en el ultimo espacio posible
        while (rest.length > max) {
            flush()
            val cut = rest.lastIndexOf(' ', max).takeIf { it > 0 } ?: max
            chunks += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (current.length + rest.length + 1 > max) flush()
        if (current.isNotEmpty()) current.append(' ')
        current.append(rest)
    }
    flush()
    return chunks
}

/**
 * donde empieza cada pedazo de splitForSpeech dentro del texto, para ubicar la palabra que se lee
 * los textos de documentos y PDF ya llegan con los espacios juntos, asi que las posiciones valen para ellos
 */
fun chunkOffsets(text: String, chunks: List<String>): IntArray {
    val clean = text.replace(Regex("\\s+"), " ").trim()
    var from = 0
    return IntArray(chunks.size) { k ->
        val at = clean.indexOf(chunks[k], from).coerceAtLeast(from)
        from = at + chunks[k].length
        at
    }
}

/**
 * lee en voz alta una lista de trozos, uno tras otro, con la voz que se le pase
 * onSegment avisa cual trozo empieza, onWord que parte de el se esta diciendo y onFinish cuando se deja de leer
 */
class ReadAloud(private val engine: VoiceEngine, speed: Float) {
    var segments by mutableStateOf<List<String>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
        private set

    /** hay una lectura en curso o en pausa: se muestra el reproductor */
    var active by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** null mientras la voz arranca, que en la voz natural tarda unos segundos */
    private var ready by mutableStateOf<Boolean?>(null)

    /** se pidio leer pero la voz todavia no esta lista */
    val preparing get() = active && ready == null

    var onSegment: (Int) -> Unit = {}

    /** trozo, y desde y hasta que caracter de ese trozo va la palabra que se dice */
    var onWord: (segment: Int, start: Int, end: Int) -> Unit = { _, _, _ -> }
    var onFinish: () -> Unit = {}

    var speed = speed

    // cada vez que se corta la lectura cambia, asi los avisos de lo que se estaba leyendo se ignoran
    private var session = 0

    init {
        engine.init { problem ->
            if (problem != null) {
                ready = false
                error = problem
                playing = false
            } else {
                ready = true
                if (active && playing) speakCurrent()
            }
        }
    }

    fun start(segments: List<String>, from: Int) {
        if (segments.isEmpty()) return
        this.segments = segments
        index = from.coerceIn(0, segments.lastIndex)
        active = true
        playing = ready != false
        if (playing) speakCurrent()
    }

    fun toggle() {
        if (playing) pause() else resume()
    }

    fun pause() {
        playing = false
        session++
        engine.stop()
    }

    /** la voz no puede seguir desde la mitad, asi que se retoma desde el inicio del trozo */
    fun resume() {
        if (!active || ready == false) return
        playing = true
        speakCurrent()
    }

    fun next() = jump(index + 1)

    fun previous() = jump(index - 1)

    private fun jump(to: Int) {
        if (to !in segments.indices) return
        index = to
        if (playing) speakCurrent() else onSegment(index)
    }

    fun stop() {
        if (!active) return
        session++
        engine.stop()
        active = false
        playing = false
        segments = emptyList()
        onFinish()
    }

    fun shutdown() {
        stop()
        engine.shutdown()
    }

    private fun speakCurrent() {
        session++
        engine.stop()
        onSegment(index)
        if (ready != true) return
        val s = session
        val i = index
        engine.speak(segments[i], speed, object : VoiceEngine.Listener {
            override fun onWord(start: Int, end: Int) {
                if (s == session && playing) onWord(i, start, end)
            }

            override fun onDone() {
                if (s == session && playing) advance()
            }

            override fun onError(message: String) {
                if (s != session) return
                error = message
                playing = false
                engine.stop()
            }
        })
    }

    private fun advance() {
        if (index < segments.lastIndex) {
            index++
            speakCurrent()
        } else {
            stop()
        }
    }
}
