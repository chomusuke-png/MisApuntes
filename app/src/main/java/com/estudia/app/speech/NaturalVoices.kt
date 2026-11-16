package com.estudia.app.speech

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * voz natural que se puede descargar. Son voces Piper empaquetadas por el proyecto sherpa-onnx
 * size y sha256 son del archivo que se descarga, para comprobar que llego entero
 */
data class NaturalVoice(
    val id: String,
    val name: String,
    val country: String,
    val size: Long,
    val sha256: String,
    val license: String,
) {
    val url get() = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-$id.tar.bz2"
    val sizeLabel get() = "${size / 1_000_000} MB"
}

val NATURAL_VOICES = listOf(
    NaturalVoice("es_MX-claude-high", "Claude", "México", 67_207_890, "ec33fb689c248fe64810aab564cba97babf0f506672cfd404928d46e751a4721", "Apache 2.0"),
    NaturalVoice("es_MX-ald-medium", "Ald", "México", 67_196_497, "c60cf7bc853eb01a875fba0756523fecf9d9f46c763b19785d223fc981fb4fb6", "Unlicense"),
    NaturalVoice("es_AR-daniela-high", "Daniela", "Argentina", 115_562_134, "71cbf6b7f646ab74f3c51336151abef41e6c54467ac929ffdb19ed706a07dd7b", "CC BY-SA 4.0"),
    NaturalVoice("es_ES-davefx-medium", "Davefx", "España", 67_184_952, "a3f6beb54a9cb893279f72978a22f807a4d9fc9c7848157b524d5cc7b7f58b22", "CC0"),
)

/** descarga, guarda y carga las voces naturales. Hay una sola por app, compartida entre pantallas */
class NaturalVoices(context: Context) {
    private val context = context.applicationContext
    private val root = File(this.context.filesDir, "voices")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    var installed by mutableStateOf(NATURAL_VOICES.filter { File(dirOf(it), MARKER).exists() }.map { it.id }.toSet())
        private set

    /** voz que se esta descargando y cuanto va, de 0 a 1. Despues de bajarla hay que descomprimirla, que tambien tarda */
    var downloading by mutableStateOf<String?>(null)
        private set
    var installing by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // generar audio usa mucha memoria y no se puede hacer en paralelo: todo pasa por este hilo
    val worker: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * datos de pronunciacion (espeak-ng), iguales en todas las voces, en una sola carpeta
     * el motor los toma de la carpeta de la primera voz que carga y sigue leyendolos de ahi hasta cerrar la app,
     * asi que no pueden vivir dentro de una voz que se puede borrar: las demas quedaban pronunciando mal
     */
    private val sharedData = File(root, "espeak-ng-data")

    init {
        // antes de cargar cualquier voz: las instaladas con su propia copia pasan a usar la compartida
        worker.execute(::shareData)
    }

    private fun shareData() {
        val dirs = NATURAL_VOICES.map(::dirOf)
        if (!File(sharedData, "phontab").exists()) {
            dirs.map { File(it, "espeak-ng-data") }.firstOrNull { File(it, "phontab").exists() }?.let { copy ->
                sharedData.deleteRecursively()
                copy.renameTo(sharedData)
            }
        }
        if (File(sharedData, "phontab").exists()) dirs.forEach { File(it, "espeak-ng-data").deleteRecursively() }
    }

    // solo se usa desde worker
    private var loaded: Pair<String, OfflineTts>? = null

    fun isInstalled(voice: NaturalVoice) = voice.id in installed

    private fun dirOf(voice: NaturalVoice) = File(root, voice.id)

    fun download(voice: NaturalVoice, onDone: () -> Unit = {}) {
        if (downloading != null) return
        downloading = voice.id
        installing = false
        progress = 0f
        error = null
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) { fetch(voice) }
                installed = installed + voice.id
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                error = e.message ?: "No se pudo descargar la voz. Revisa tu conexión."
            } finally {
                downloading = null
                installing = false
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun delete(voice: NaturalVoice) {
        installed = installed - voice.id
        // sin voces, los datos de pronunciacion sobran; si despues se descarga otra, vuelven a la misma carpeta
        val last = installed.isEmpty() && downloading == null
        worker.execute {
            if (loaded?.first == voice.id) {
                loaded?.second?.release()
                loaded = null
            }
            dirOf(voice).deleteRecursively()
            if (last) sharedData.deleteRecursively()
        }
    }

    /** carga la voz para generar audio. Solo desde worker: tarda unos segundos y queda en memoria */
    fun load(voice: NaturalVoice): OfflineTts {
        loaded?.let { (id, tts) -> if (id == voice.id) return tts else tts.release() }
        loaded = null
        val dir = dirOf(voice)
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(dir, "${voice.id}.onnx").absolutePath,
                    tokens = File(dir, "tokens.txt").absolutePath,
                    dataDir = sharedData.absolutePath,
                ),
                numThreads = THREADS,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
        )
        return OfflineTts(config = config).also { loaded = voice.id to it }
    }

    private suspend fun fetch(voice: NaturalVoice) {
        // se pide el triple: lo descargado y lo descomprimido conviven un momento
        if (context.filesDir.usableSpace < voice.size * 3) {
            throw IOException("No hay espacio suficiente: la voz necesita unos ${voice.size * 3 / 1_000_000} MB libres.")
        }
        val archive = File(context.cacheDir, "voice-${voice.id}.tar.bz2")
        val partial = File(root, "${voice.id}.tmp")
        try {
            download(voice, archive)
            withContext(Dispatchers.Main) {
                installing = true
                progress = 0f
            }
            partial.deleteRecursively()
            extract(archive, partial, "vits-piper-${voice.id}/")
            if (!File(partial, "${voice.id}.onnx").exists()) throw IOException("La voz descargada viene incompleta.")
            val data = File(partial, "espeak-ng-data")
            if (!File(sharedData, "phontab").exists()) {
                sharedData.deleteRecursively()
                if (!data.renameTo(sharedData)) throw IOException("No se pudo guardar la voz.")
            } else {
                data.deleteRecursively()
            }
            File(partial, MARKER).writeText(voice.sha256)
            dirOf(voice).deleteRecursively()
            if (!partial.renameTo(dirOf(voice))) throw IOException("No se pudo guardar la voz.")
        } finally {
            archive.delete()
            partial.deleteRecursively()
        }
    }

    private suspend fun download(voice: NaturalVoice, to: File) {
        val connection = (URL(voice.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "MisApuntes")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("No se pudo descargar la voz (GitHub respondió ${connection.responseCode}).")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            var shown = -1
            connection.inputStream.use { input ->
                to.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        kotlin.coroutines.coroutineContext.ensureActive()
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        written += read
                        val percent = (written * 100 / voice.size).toInt()
                        if (percent != shown) {
                            shown = percent
                            withContext(Dispatchers.Main) { progress = (percent / 100f).coerceAtMost(1f) }
                        }
                    }
                }
            }
            val sum = digest.digest().joinToString("") { "%02x".format(it) }
            if (written != voice.size || sum != voice.sha256) throw IOException("La descarga llegó dañada. Vuelve a intentarlo.")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * descomprime solo lo que esta dentro de prefix, sin dejar que un archivo se escape de la carpeta
     * el avance se mide por cuanto del archivo comprimido ya se leyo
     */
    private suspend fun extract(archive: File, to: File, prefix: String) {
        val base = to.canonicalFile
        val total = archive.length().coerceAtLeast(1)
        val counted = CountingStream(archive.inputStream())
        var shown = -1
        TarArchiveInputStream(BZip2CompressorInputStream(counted.buffered(256 * 1024))).use { tar ->
            while (true) {
                kotlin.coroutines.coroutineContext.ensureActive()
                val percent = (counted.count * 100 / total).toInt()
                if (percent != shown) {
                    shown = percent
                    withContext(Dispatchers.Main) { progress = (percent / 100f).coerceAtMost(1f) }
                }
                val entry = tar.nextEntry ?: break
                if (!entry.name.startsWith(prefix)) continue
                val target = File(base, entry.name.removePrefix(prefix)).canonicalFile
                if (target != base && !target.path.startsWith(base.path + File.separator)) continue
                if (entry.isDirectory) {
                    target.mkdirs()
                } else if (entry.isFile) {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { tar.copyTo(it) }
                }
            }
        }
    }

    /** cuenta los bytes leidos, para mostrar cuanto falta al descomprimir */
    private class CountingStream(input: InputStream) : FilterInputStream(input) {
        @Volatile var count = 0L

        override fun read(): Int = super.read().also { if (it >= 0) count++ }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }

        override fun skip(n: Long): Long = super.skip(n).also { count += it }
    }

    private companion object {
        const val MARKER = ".installed"
        const val THREADS = 2
    }
}
