package com.estudia.app.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** algo que convierte texto en voz. Lee un trozo a la vez y avisa en el hilo principal */
interface VoiceEngine {
    /** arranca el motor y avisa null si quedo listo, o el mensaje para mostrar si no se puede usar */
    fun init(onResult: (String?) -> Unit)

    /** lee el texto. Las posiciones de onWord son del texto recibido. Cortar con stop evita mas avisos */
    fun speak(text: String, speed: Float, listener: Listener)

    fun stop()

    fun shutdown()

    interface Listener {
        fun onWord(start: Int, end: Int)

        fun onDone()

        /** la voz no pudo leer: se muestra el mensaje y se deja de leer */
        fun onError(message: String) {}
    }
}

/** el motor de voz del telefono, el que este elegido en los ajustes de Android */
class SystemVoice(context: Context) : VoiceEngine {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null

    // cada lectura tiene su numero, asi los avisos de una lectura cortada se ignoran
    private var session = 0
    private var offsets = IntArray(0)
    private var listener: VoiceEngine.Listener? = null

    override fun init(onResult: (String?) -> Unit) {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status -> main.post { onResult(ready(engine, status)) } }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}

            override fun onDone(utteranceId: String) {
                main.post { chunkDone(utteranceId) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                main.post { chunkDone(utteranceId) }
            }

            // no todas las voces avisan por palabra; si no lo hacen, queda marcado solo el trozo
            override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
                main.post { wordStarted(utteranceId, start, end) }
            }
        })
        tts = engine
    }

    private fun ready(engine: TextToSpeech, status: Int): String? {
        if (status != TextToSpeech.SUCCESS) return "Este teléfono no tiene un motor de voz para leer."
        val locale = Locale.getDefault().takeIf { it.language == "es" } ?: Locale.forLanguageTag("es")
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            return "Faltan las voces en español. Puedes instalarlas en Ajustes → Voz del sistema."
        }
        return null
    }

    override fun speak(text: String, speed: Float, listener: VoiceEngine.Listener) {
        val engine = tts ?: return
        session++
        engine.stop()
        this.listener = listener
        val chunks = splitForSpeech(text)
        if (chunks.isEmpty()) {
            val s = session
            main.post { if (s == session) listener.onDone() }
            return
        }
        engine.setSpeechRate(speed)
        offsets = chunkOffsets(text, chunks)
        chunks.forEachIndexed { k, chunk ->
            engine.speak(chunk, TextToSpeech.QUEUE_ADD, null, "$session/$k/${chunks.lastIndex}")
        }
    }

    override fun stop() {
        session++
        listener = null
        tts?.stop()
    }

    override fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }

    private fun parse(utteranceId: String): List<Int>? =
        utteranceId.split('/').map { it.toIntOrNull() ?: return null }.takeIf { it.size == 3 }

    private fun wordStarted(utteranceId: String, start: Int, end: Int) {
        val (s, k) = parse(utteranceId) ?: return
        if (s != session) return
        val base = offsets.getOrNull(k) ?: return
        listener?.onWord(base + start, base + end)
    }

    private fun chunkDone(utteranceId: String) {
        val (s, k, last) = parse(utteranceId) ?: return
        if (s != session || k != last) return
        listener?.onDone()
    }
}
