package com.estudia.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.estudia.app.data.ThemeMode
import com.estudia.app.ui.ACTION_OPEN_DOCUMENT
import com.estudia.app.ui.AppNavigation
import com.estudia.app.ui.EXTRA_DOCUMENT_ID
import com.estudia.app.ui.theme.EstudiaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Documento a abrir pedido desde fuera (acceso directo o "Abrir con"). */
    private val openRequest = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        val settings = (application as EstudiaApp).settings
        setContent {
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Iconos de la barra de estado acordes al tema elegido, no al del sistema
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            EstudiaTheme(dark = dark) {
                AppNavigation(
                    darkTheme = dark,
                    openRequest = openRequest.value,
                    onOpenRequestHandled = { openRequest.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_DOCUMENT -> {
                val id = intent.getLongExtra(EXTRA_DOCUMENT_ID, -1)
                if (id > 0) openRequest.value = id
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                val repo = (application as EstudiaApp).repository
                lifecycleScope.launch {
                    runCatching { repo.importUri(uri, null) }
                        .onSuccess { openRequest.value = it }
                        .onFailure { Toast.makeText(this@MainActivity, it.message, Toast.LENGTH_LONG).show() }
                }
            }
        }
    }
}
