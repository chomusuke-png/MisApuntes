package com.estudia.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** decide cuando se ve el appbar de un editor: se esconde al bajar o trabajar y vuelve al subir */
@Stable
class AppBarVisibility {
    var visible by mutableStateOf(true)
        private set

    private var last: Float? = null
    private var travelled = 0f
    private var quietUntil = 0L

    /** mientras es true, desplazarse no trae de vuelta el appbar. El boton para mostrarlo sigue funcionando */
    var keepHidden = false

    fun show() = set(true)

    fun hide() = set(false)

    private fun set(value: Boolean) {
        // al cambiar el alto del contenido se mueve solo, y eso no cuenta como desplazarse
        if (value != visible) quietUntil = SystemClock.uptimeMillis() + SETTLE_MS
        visible = value
        travelled = 0f
    }

    /** position crece al bajar por el documento. Un recorrido corto no cambia nada, asi no parpadea */
    fun scrolledTo(position: Float) {
        val delta = position - (last ?: position)
        last = position
        // el contenido avisa su posicion tambien al cambiar de alto, por ejemplo al abrir el teclado
        if (delta == 0f || keepHidden || SystemClock.uptimeMillis() < quietUntil) return
        if (position <= 0f) return show()
        travelled = if (delta > 0 == travelled > 0) travelled + delta else delta
        if (travelled > THRESHOLD) hide() else if (travelled < -THRESHOLD) show()
    }

    private companion object {
        const val THRESHOLD = 48f
        const val SETTLE_MS = 400L
    }
}

@Composable
fun rememberAppBarVisibility() = remember { AppBarVisibility() }

/** pantalla baja, como un telefono en horizontal: las barras se achican para dejar lugar al texto */
@Composable
fun isShortScreen() = LocalConfiguration.current.screenHeightDp < SHORT_SCREEN_DP

private const val SHORT_SCREEN_DP = 480

/** el teclado esta abierto en una pantalla baja, donde casi no queda lugar para el texto */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun isCrampedByKeyboard() = WindowInsets.isImeVisible && isShortScreen()

/** con el teclado en una pantalla baja se esconden el appbar y la barra de estado, para dejar lugar al texto */
@Composable
fun HideAppBarWithKeyboard(bar: AppBarVisibility) {
    val cramped = isCrampedByKeyboard()
    val view = LocalView.current
    DisposableEffect(cramped) {
        bar.keepHidden = cramped
        if (cramped) bar.hide()
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (cramped) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            bar.keepHidden = false
            if (cramped) controller?.show(WindowInsetsCompat.Type.statusBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * parte superior de un editor: el appbar, que se puede esconder, y debajo lo que siempre se ve
 * appBar debe ir sin margen para la barra de estado, que ya se reserva aqui
 */
@Composable
fun HidingTopBar(visible: Boolean, appBar: @Composable () -> Unit, below: @Composable () -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.primaryContainer).statusBarsPadding()) {
        AnimatedVisibility(visible, enter = expandVertically(), exit = shrinkVertically()) { appBar() }
        below()
    }
}

/** boton para traer de vuelta el appbar, por si no hay hacia donde desplazarse */
@Composable
fun ShowAppBarButton(bar: AppBarVisibility, modifier: Modifier = Modifier) {
    AnimatedVisibility(!bar.visible, modifier, enter = fadeIn(), exit = fadeOut()) {
        FilledTonalIconButton(onClick = bar::show, modifier = Modifier.padding(6.dp).size(32.dp)) {
            Icon(Icons.Default.ExpandMore, "Mostrar barra superior")
        }
    }
}
