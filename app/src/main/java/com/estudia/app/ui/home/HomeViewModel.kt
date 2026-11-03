package com.estudia.app.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.estudia.app.EstudiaApp
import com.estudia.app.data.DocumentEntity
import com.estudia.app.data.FolderEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as EstudiaApp).repository

    private val _currentFolderId = MutableStateFlow<Long?>(null)
    val currentFolderId: StateFlow<Long?> = _currentFolderId

    val query = MutableStateFlow("")

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** Abre el documento recién creado/importado. */
    private val _openDocument = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val openDocument: SharedFlow<Long> = _openDocument

    val busy = MutableStateFlow(false)

    val allFolders = repo.folders.allFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders = _currentFolderId.flatMapLatest { repo.folders.foldersIn(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val documents = combine(_currentFolderId, query) { f, q -> f to q.trim() }
        .flatMapLatest { (folder, q) -> if (q.isEmpty()) repo.documents.documentsIn(folder) else repo.documents.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pinned = repo.documents.pinned()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Ruta desde la raíz hasta la carpeta actual, para las "migas de pan". */
    val path: StateFlow<List<FolderEntity>> = combine(_currentFolderId, allFolders) { id, all ->
        val byId = all.associateBy { it.id }
        generateSequence(id?.let(byId::get)) { it.parentId?.let(byId::get) }.toList().reversed()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openFolder(id: Long?) {
        query.value = ""
        _currentFolderId.value = id
    }

    /** @return false si ya estamos en la raíz. */
    fun goUp(): Boolean {
        val current = _currentFolderId.value ?: return false
        _currentFolderId.value = allFolders.value.firstOrNull { it.id == current }?.parentId
        return true
    }

    fun import(uris: List<Uri>) = viewModelScope.launch {
        busy.value = true
        var lastId: Long? = null
        val errors = mutableListOf<String>()
        for (uri in uris) {
            runCatching { repo.importUri(uri, _currentFolderId.value) }
                .onSuccess { lastId = it }
                .onFailure { errors += it.message ?: "Error desconocido" }
        }
        busy.value = false
        if (errors.isNotEmpty()) _messages.emit(errors.joinToString("\n"))
        if (uris.size == 1) lastId?.let { _openDocument.emit(it) }
        else if (lastId != null) _messages.emit("${uris.size - errors.size} archivos importados")
    }

    fun createNote(title: String) = viewModelScope.launch {
        _openDocument.emit(repo.createNote(title, _currentFolderId.value))
    }

    fun createDocument(title: String) = viewModelScope.launch {
        runCatching { repo.createWordDocument(title, _currentFolderId.value) }
            .onSuccess { _openDocument.emit(it) }
            .onFailure { _messages.emit(it.message ?: "No se pudo crear el documento") }
    }

    fun createFolder(name: String) = viewModelScope.launch {
        repo.folders.insert(FolderEntity(name = name, parentId = _currentFolderId.value))
    }

    fun renameFolder(folder: FolderEntity, name: String) = viewModelScope.launch { repo.folders.rename(folder.id, name) }
    fun moveFolder(folder: FolderEntity, parent: Long?) = viewModelScope.launch { repo.folders.move(folder.id, parent) }
    fun deleteFolder(folder: FolderEntity) = viewModelScope.launch { repo.folders.deleteKeepingContents(folder) }

    fun togglePin(doc: DocumentEntity) = viewModelScope.launch { repo.documents.setPinned(doc.id, !doc.pinned) }
    fun renameDocument(doc: DocumentEntity, title: String) = viewModelScope.launch { repo.documents.rename(doc.id, title) }
    fun moveDocument(doc: DocumentEntity, folder: Long?) = viewModelScope.launch { repo.documents.move(doc.id, folder) }
    fun deleteDocument(doc: DocumentEntity) = viewModelScope.launch { repo.deleteDocument(doc) }

    fun folderName(id: Long?): String? = id?.let { fid -> allFolders.value.firstOrNull { it.id == fid }?.name }
}
