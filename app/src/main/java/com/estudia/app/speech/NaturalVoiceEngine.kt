package com.estudia.app.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * lee con una voz natural descargada. Genera el audio de cada oracion en el telefono mientras suena la anterior
 * la voz no avisa que palabra dice, asi que se estima por el largo de cada palabra
 */
class NaturalVoiceEngine(private val voices: NaturalVoices, private val voice: NaturalVoice) : VoiceEngine {
    private val main = Handler(Looper.getMainLooper())
    private var playback: Playback? = null

    override fun init(onResult: (String?) -> Unit) {
        voices.worker.execute {
            val loaded = runCatching { voices.load(voice) }
            main.post {
                onResult(if (loaded.isSuccess) null else "No se pudo cargar la voz natural. Prueba borrarla y descargarla de nuevo.")
            }
        }
    }

    override fun speak(text: String, speed: Float, listener: VoiceEngine.Listener) {
        stop()
        val p = Playback(text, sentenceSpans(text), speed, listener)
        playback = p
        p.start()
    }

    override fun stop() {
        playback?.cancel()
        playback = null
    }

    // la voz queda cargada en NaturalVoices para la proxima pantalla
    override fun shutdown() = stop()

    /** una oracion lista para sonar */
    private class Piece(val span: IntRange, val samples: FloatArray, val rate: Int)

    /** cuando suena cada oracion, en cuadros de audio desde el inicio del trozo */
    private class Timed(val start: Long, val frames: Int, val words: List<IntRange>, val weights: FloatArray)

    private inner class Playback(
        private val text: String,
        private val spans: List<IntRange>,
        private val speed: Float,
        private val listener: VoiceEngine.Listener,
    ) {
        @Volatile private var cancelled = false
        @Volatile private var writerDone = false
        @Volatile private var written = 0L
        @Volatile private var track: AudioTrack? = null
        private val queue = LinkedBlockingQueue<Piece>(2)
        private val timeline = mutableListOf<Timed>()
        private var lastWord = -1 to -1
        private var lastHead = -1L
        private var stalledSince = 0L

        fun start() {
            if (spans.isEmpty()) {
                main.post { if (!cancelled) listener.onDone() }
                return
            }
            voices.worker.execute(::generate)
            Thread(::write, "voz-natural").start()
            main.postDelayed(::poll, POLL_MS)
        }

        fun cancel() {
            cancelled = true
            // sin sonido de inmediato; vaciar el buffer libera al hilo que escribe para que termine
            track?.let { t -> runCatching { t.pause(); t.flush() } }
        }

        private fun generate() {
            val tts = runCatching { voices.load(voice) }.getOrNull() ?: return
            for (span in spans) {
                if (cancelled) return
                val sentence = text.substring(span.first, span.last + 1)
                val audio = runCatching { tts.generate(sentence, 0, speed) }.getOrNull()
                // sin audio para algo que tiene letras: la voz no sabe pronunciar, y saltarlo en silencio esconderia el problema
                if ((audio == null || audio.samples.isEmpty()) && sentence.any { it.isLetterOrDigit() }) {
                    main.post { if (!cancelled) listener.onError("La voz natural no pudo pronunciar el texto. Prueba otra voz o la del teléfono.") }
                    cancel()
                    return
                }
                val piece = Piece(span, audio?.samples ?: FloatArray(0), audio?.sampleRate ?: tts.sampleRate())
                while (!queue.offer(piece, 100, TimeUnit.MILLISECONDS)) if (cancelled) return
            }
            while (!queue.offer(END, 100, TimeUnit.MILLISECONDS)) if (cancelled) return
        }

        private fun write() {
            try {
                while (!cancelled) {
                    val piece = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                    if (piece === END) break
                    if (piece.samples.isEmpty()) continue
                    val t = track ?: newTrack(piece.rate).also {
                        track = it
                        it.play()
                    }
                    val words = wordSpans(text.substring(piece.span.first, piece.span.last + 1), piece.span.first)
                    synchronized(timeline) {
                        timeline += Timed(written, piece.samples.size, words, wordWeights(text, words))
                    }
                    var at = 0
                    while (at < piece.samples.size && !cancelled) {
                        val n = t.write(piece.samples, at, minOf(CHUNK, piece.samples.size - at), AudioTrack.WRITE_BLOCKING)
                        if (n <= 0) break
                        at += n
                        written += n
                    }
                }
                // en modo continuo, stop deja sonar lo que ya esta en el buffer
                if (!cancelled) track?.stop()
            } finally {
                writerDone = true
                if (cancelled) track?.release()
            }
        }

        private fun newTrack(rate: Int): AudioTrack {
            val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            return AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                // medio segundo de audio de margen
                .setBufferSizeInBytes(maxOf(min, rate * 4 / 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }

        /** mira por donde va el audio: marca la palabra y avisa cuando termina */
        private fun poll() {
            if (cancelled) return
            val t = track
            val head = t?.let { runCatching { it.playbackHeadPosition.toLong() and 0xFFFFFFFFL }.getOrDefault(0L) } ?: 0L
            val entries = synchronized(timeline) { timeline.toList() }
            val i = entries.indexOfLast { it.start <= head }
            if (i >= 0) {
                val e = entries[i]
                val w = wordAt(e.weights, (head - e.start).toFloat() / e.frames.coerceAtLeast(1))
                if (w >= 0 && (i to w) != lastWord) {
                    lastWord = i to w
                    listener.onWord(e.words[w].first, e.words[w].last + 1)
                }
            }
            if (writerDone && finished(head)) {
                track?.release()
                track = null
                listener.onDone()
                return
            }
            main.postDelayed(::poll, POLL_MS)
        }

        // algunos telefonos no llegan exacto al final: tambien termina si el audio deja de avanzar un rato
        private fun finished(head: Long): Boolean {
            if (track == null || head >= written) return true
            val now = System.currentTimeMillis()
            if (head != lastHead) {
                lastHead = head
                stalledSince = now
                return false
            }
            return now - stalledSince > STALL_MS
        }
    }

    private companion object {
        val END = Piece(IntRange.EMPTY, FloatArray(0), 0)
        const val CHUNK = 2048
        const val POLL_MS = 40L
        const val STALL_MS = 600L
    }
}
