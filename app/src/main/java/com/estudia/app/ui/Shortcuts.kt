package com.estudia.app.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.estudia.app.MainActivity
import com.estudia.app.R
import com.estudia.app.data.DocumentEntity

const val ACTION_OPEN_DOCUMENT = "com.estudia.app.OPEN_DOCUMENT"
const val EXTRA_DOCUMENT_ID = "documentId"

/** Pide al launcher crear un acceso directo que abre el documento. */
fun pinDocumentShortcut(context: Context, doc: DocumentEntity): Boolean {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
    val intent = Intent(context, MainActivity::class.java)
        .setAction(ACTION_OPEN_DOCUMENT)
        .putExtra(EXTRA_DOCUMENT_ID, doc.id)
    val info = ShortcutInfoCompat.Builder(context, "doc_${doc.id}")
        .setShortLabel(doc.title.take(25))
        .setLongLabel(doc.title)
        .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
        .setIntent(intent)
        .build()
    return ShortcutManagerCompat.requestPinShortcut(context, info, null)
}
