package com.estudia.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.estudia.app.EstudiaApp
import com.estudia.app.data.DocType
import com.estudia.app.ui.editor.EditorScreen
import com.estudia.app.ui.home.HomeScreen
import com.estudia.app.ui.notes.NoteEditorScreen
import com.estudia.app.ui.pdf.PdfViewerScreen
import com.estudia.app.ui.settings.SettingsScreen
import com.estudia.app.update.UpdateDialog
import kotlinx.coroutines.launch

private const val HOME = "home"
private const val SETTINGS = "settings"
private const val EDITOR = "editor/{id}"
private const val PDF = "pdf/{id}"
private const val STICKY_NOTE = "note/{id}"

@Composable
fun AppNavigation(darkTheme: Boolean, openRequest: Long?, onOpenRequestHandled: () -> Unit) {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as EstudiaApp
    val repo = app.repository
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (app.settings.autoUpdateCheck) app.updater.checkOnStart()
    }

    /** Los PDF van al visor de páginas; el resto al editor. */
    fun open(id: Long) {
        scope.launch {
            val doc = repo.documents.get(id) ?: return@launch
            nav.openDocument(id, doc.type)
        }
    }

    LaunchedEffect(openRequest) {
        if (openRequest != null) {
            open(openRequest)
            onOpenRequestHandled()
        }
    }

    NavHost(navController = nav, startDestination = HOME) {
        composable(HOME) {
            HomeScreen(onOpenDocument = ::open, onOpenSettings = { nav.navigate(SETTINGS) { launchSingleTop = true } })
            UpdateDialog()
        }
        composable(SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
            UpdateDialog()
        }
        composable(EDITOR, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            EditorScreen(
                darkTheme = darkTheme,
                onBack = { nav.popBackStack() },
                onOpenStickyNote = { nav.navigate("note/$it") { launchSingleTop = true } },
            )
        }
        composable(STICKY_NOTE, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            NoteEditorScreen(darkTheme = darkTheme, onBack = { nav.popBackStack() })
        }
        composable(PDF, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            PdfViewerScreen(
                darkTheme = darkTheme,
                onBack = { nav.popBackStack() },
                onOpenNote = { noteId -> nav.openDocument(noteId, DocType.NOTE) },
                onOpenStickyNote = { nav.navigate("note/$it") { launchSingleTop = true } },
            )
        }
    }
}

private fun NavHostController.openDocument(id: Long, type: DocType) {
    navigate(if (type == DocType.PDF) "pdf/$id" else "editor/$id") { launchSingleTop = true }
}
