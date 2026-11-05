package com.estudia.app.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val LATEST_RELEASE_URL = "https://api.github.com/repos/chomusuke-png/MisApuntes/releases/latest"

data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long)

/** devuelve null si la release no trae un APK */
fun parseRelease(json: String): Release? {
    val root = Json.parseToJsonElement(json).jsonObject
    val apk = root["assets"]?.jsonArray.orEmpty()
        .map { it.jsonObject }
        .firstOrNull { it["name"]?.jsonPrimitive?.content.orEmpty().endsWith(".apk") }
        ?: return null
    return Release(
        version = root["tag_name"]?.jsonPrimitive?.content?.removePrefix("v") ?: return null,
        notes = root["body"]?.jsonPrimitive?.content.orEmpty().trim(),
        apkUrl = apk["browser_download_url"]?.jsonPrimitive?.content ?: return null,
        size = apk["size"]?.jsonPrimitive?.longOrNull ?: return null,
    )
}

/** compara por numeros, asi 1.10.0 es mayor que 1.9.0 y 1.0 es igual a 1.0.0 */
fun isNewer(latest: String, current: String): Boolean {
    val a = versionParts(latest) ?: return false
    val b = versionParts(current) ?: return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (diff != 0) return diff > 0
    }
    return false
}

private fun versionParts(version: String): List<Int>? =
    version.removePrefix("v").split(".").map { it.toIntOrNull() ?: return null }

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState

    /** con `release` el fallo fue al descargar y se puede reintentar */
    data class Failed(val message: String, val release: Release? = null) : UpdateState
}

/** busca versiones nuevas en las releases de GitHub. Android siempre pide confirmar la instalacion */
class AppUpdater(private val context: Context, private val currentVersion: String) {
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val apkDir = File(context.cacheDir, "update")
    private var download: Job? = null
    private var checkedOnStart = false
    private var reportResult = false

    /** solo busca una vez por cada apertura de la app */
    fun checkOnStart() {
        if (checkedOnStart) return
        checkedOnStart = true
        check(manual = false)
    }

    /** una busqueda automatica no avisa si falla o si no hay nada nuevo */
    fun check(manual: Boolean) {
        if (manual) reportResult = true
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        state = UpdateState.Checking
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { fetchLatest() } }
            state = result.fold(
                onSuccess = { release ->
                    when {
                        release != null && isNewer(release.version, currentVersion) -> UpdateState.Available(release)
                        reportResult -> UpdateState.UpToDate
                        else -> UpdateState.Idle
                    }
                },
                onFailure = {
                    if (reportResult) UpdateState.Failed("No se pudo buscar. Revisa tu conexión.") else UpdateState.Idle
                },
            )
            reportResult = false
        }
    }

    fun download(release: Release) {
        state = UpdateState.Downloading(release, 0f)
        download = scope.launch {
            try {
                val apk = withContext(Dispatchers.IO) { fetchApk(release) }
                install(apk)
                state = UpdateState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                state = UpdateState.Failed("La descarga falló. Revisa tu conexión.", release)
            } catch (e: ActivityNotFoundException) {
                state = UpdateState.Failed("No se pudo abrir el instalador.", release)
            }
        }
    }

    fun dismiss() {
        download?.cancel()
        download = null
        state = UpdateState.Idle
    }

    private fun fetchLatest(): Release? {
        apkDir.deleteRecursively()
        val connection = open(LATEST_RELEASE_URL)
        try {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            return when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> parseRelease(connection.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("GitHub respondió $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun fetchApk(release: Release): File {
        apkDir.deleteRecursively()
        if (!apkDir.mkdirs()) throw IOException("No se pudo crear la carpeta de descarga")
        val apk = File(apkDir, "MisApuntes.apk")
        val connection = open(release.apkUrl)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("GitHub respondió ${connection.responseCode}")
            var written = 0L
            var shownPercent = 0
            connection.inputStream.use { input ->
                apk.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, read)
                        written += read
                        val percent = (written * 100 / release.size.coerceAtLeast(1)).toInt()
                        if (percent != shownPercent) {
                            shownPercent = percent
                            withContext(Dispatchers.Main) {
                                state = UpdateState.Downloading(release, (percent / 100f).coerceAtMost(1f))
                            }
                        }
                    }
                }
            }
            if (written != release.size) throw IOException("Descarga incompleta")
            return apk
        } finally {
            connection.disconnect()
        }
    }

    private fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            // la API de GitHub rechaza las peticiones sin este encabezado
            setRequestProperty("User-Agent", "MisApuntes")
        }
}
