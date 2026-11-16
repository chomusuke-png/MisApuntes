package com.estudia.app.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/** lo que devuelve reader.start() del editor: los parrafos y desde cual empezar. null si no hay texto */
fun parseEditorReading(json: String): Pair<List<String>, Int>? {
    val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
    val parts = root["parts"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty().filter { it.isNotBlank() }
    if (parts.isEmpty()) return null
    val start = root["start"]?.jsonPrimitive?.intOrNull ?: 0
    return parts to start.coerceIn(0, parts.lastIndex)
}

/** velocidades que se ofrecen para leer */
val READ_SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

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
 * lee en voz alta una lista de trozos, uno tras otro, con el motor de voz del telefono
 * onSegment avisa cual trozo empieza, onWord que parte de el se esta diciendo y onFinish cuando se deja de leer
 */
class ReadAloud(context: Context, speed: Float) {
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

    var onSegment: (Int) -> Unit = {}

    /** trozo, y desde y hasta que caracter de ese trozo va la palabra que se dice */
    var onWord: (segment: Int, start: Int, end: Int) -> Unit = { _, _, _ -> }
    var onFinish: () -> Unit = {}

    var speed = speed
        set(value) {
            field = value
            if (ready == true) tts.setSpeechRate(value)
        }

    private val main = Handler(Looper.getMainLooper())

    /** null mientras el motor arranca */
    private var ready: Boolean? = null

    // cada vez que se corta la lectura cambia, asi los avisos de lo que se estaba leyendo se ignoran
    private var session = 0
    private var offsets = IntArray(0)

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status -> main.post { onInit(status) } }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}

            override fun onDone(utteranceId: String) {
                main.post { chunkDone(utteranceId) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                main.post { chunkDone(utteranceId) }
            }

            // no todos los motores avisan por palabra; si no lo hacen, queda marcado solo el trozo
            override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
                main.post { wordStarted(utteranceId, start, end) }
            }
        })
    }

    fun start(segments: List<String>, from: Int) {
        if (segments.isEmpty()) return
        this.segments = segments
        index = from.coerceIn(0, segments.lastIndex)
        active = true
        playing = true
        if (ready == false) {
            playing = false
            return
        }
        error = null
        speakCurrent()
    }

    fun toggle() {
        if (playing) pause() else resume()
    }

    fun pause() {
        playing = false
        session++
        tts.stop()
    }

    /** el motor no puede seguir desde la mitad, asi que se retoma desde el inicio del trozo */
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
        tts.stop()
        active = false
        playing = false
        segments = emptyList()
        onFinish()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return fail("Este teléfono no tiene un motor de voz para leer.")
        val locale = Locale.getDefault().takeIf { it.language == "es" } ?: Locale.forLanguageTag("es")
        val result = tts.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            return fail("Faltan las voces en español. Puedes instalarlas en Ajustes → Voz del sistema.")
        }
        ready = true
        tts.setSpeechRate(speed)
        if (active && playing) speakCurrent()
    }

    private fun fail(message: String) {
        ready = false
        error = message
        playing = false
    }

    private fun speakCurrent() {
        session++
        tts.stop()
        onSegment(index)
        if (ready != true) return
        val chunks = splitForSpeech(segments[index])
        if (chunks.isEmpty()) return advance()
        offsets = chunkOffsets(segments[index], chunks)
        chunks.forEachIndexed { k, chunk ->
            tts.speak(chunk, TextToSpeech.QUEUE_ADD, null, "$session/$index/$k/${chunks.lastIndex}")
        }
    }

    private fun parse(utteranceId: String): List<Int>? =
        utteranceId.split('/').map { it.toIntOrNull() ?: return null }.takeIf { it.size == 4 }

    private fun wordStarted(utteranceId: String, start: Int, end: Int) {
        val (s, i, k) = parse(utteranceId) ?: return
        if (s != session || i != index || !playing) return
        val base = offsets.getOrNull(k) ?: return
        onWord(i, base + start, base + end)
    }

    private fun chunkDone(utteranceId: String) {
        val (s, i, k, last) = parse(utteranceId) ?: return
        if (s != session || i != index || k != last || !playing) return
        advance()
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
