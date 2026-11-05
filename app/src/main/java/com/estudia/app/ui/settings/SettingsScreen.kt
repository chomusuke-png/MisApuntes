package com.estudia.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.estudia.app.BuildConfig
import com.estudia.app.EstudiaApp
import com.estudia.app.update.UpdateState
import com.estudia.app.data.ThemeMode
import com.estudia.app.data.ToolbarPosition
import com.estudia.app.ui.theme.appBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as EstudiaApp
    val settings = app.settings
    val updater = app.updater
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                colors = appBarColors(),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                "Tema",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
            )
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    val selected = settings.themeMode == mode
                    ListItem(
                        headlineContent = { Text(mode.label) },
                        leadingContent = {
                            Icon(
                                when (mode) {
                                    ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                    ThemeMode.LIGHT -> Icons.Default.LightMode
                                    ThemeMode.DARK -> Icons.Default.DarkMode
                                },
                                contentDescription = null,
                            )
                        },
                        trailingContent = { RadioButton(selected = selected, onClick = null) },
                        modifier = Modifier.selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { settings.updateTheme(mode) },
                        ),
                    )
                }
            }
            Text(
                "Barra de herramientas",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
            )
            Column(Modifier.selectableGroup()) {
                ToolbarPosition.entries.forEach { position ->
                    val selected = settings.toolbarPosition == position
                    ListItem(
                        headlineContent = { Text(position.label) },
                        leadingContent = {
                            Icon(
                                when (position) {
                                    ToolbarPosition.TOP -> Icons.Default.VerticalAlignTop
                                    ToolbarPosition.BOTTOM -> Icons.Default.VerticalAlignBottom
                                },
                                contentDescription = null,
                            )
                        },
                        trailingContent = { RadioButton(selected = selected, onClick = null) },
                        modifier = Modifier.selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { settings.updateToolbarPosition(position) },
                        ),
                    )
                }
            }
            Text(
                "Se aplica a documentos, notas y PDF.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                "Actualizaciones",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
            )
            ListItem(
                headlineContent = { Text("Buscar al abrir la app") },
                leadingContent = { Icon(Icons.Default.Autorenew, contentDescription = null) },
                trailingContent = { Switch(checked = settings.autoUpdateCheck, onCheckedChange = null) },
                modifier = Modifier.toggleable(
                    value = settings.autoUpdateCheck,
                    role = Role.Switch,
                    onValueChange = settings::updateAutoUpdateCheck,
                ),
            )
            ListItem(
                headlineContent = { Text("Buscar actualizaciones") },
                supportingContent = {
                    Text(
                        when (val state = updater.state) {
                            UpdateState.Checking -> "Buscando…"
                            UpdateState.UpToDate -> "Tienes la última versión (${BuildConfig.VERSION_NAME})"
                            is UpdateState.Failed -> state.message
                            else -> "Versión ${BuildConfig.VERSION_NAME}"
                        }
                    )
                },
                leadingContent = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
                modifier = Modifier.clickable { updater.check(manual = true) },
            )
        }
    }
}
