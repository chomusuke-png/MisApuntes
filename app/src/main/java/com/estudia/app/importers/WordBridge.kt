package com.estudia.app.importers

import android.util.Log

/**
 * Núcleo nativo de lectura y escritura de Word (adaptado de WordCraft, MIT/Apache-2.0).
 * Si la biblioteca no está para la arquitectura del equipo, la app usa los conversores en Kotlin.
 */
object WordBridge {
    val available: Boolean by lazy {
        runCatching { System.loadLibrary("wordbridge") }
            .onFailure { Log.w("WordBridge", "Biblioteca nativa no disponible", it) }
            .isSuccess
    }

    /** HTML del editor para un .docx/.doc; las imágenes quedan en [mediaDir]. */
    @JvmStatic external fun toHtml(path: String, ext: String, mediaDir: String): String

    /**
     * Escribe en [out] el .docx con los cambios del [model] del editor aplicados sobre el original
     * ([path] vacío = documento en blanco; [model] vacío = el original sin cambios).
     */
    @JvmStatic external fun toDocx(path: String, ext: String, model: String, mediaDir: String, out: String)
}
