package com.estudia.app.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
        if (SystemClock.uptimeMillis() < quietUntil) return
        if (position <= 0f) return show()
        if (delta == 0f) return
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
