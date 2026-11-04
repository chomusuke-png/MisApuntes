package com.estudia.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE parentId IS :parentId ORDER BY name COLLATE NOCASE")
    fun foldersIn(parentId: Long?): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun allFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun get(id: Long): FolderEntity?

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE folders SET parentId = :parentId WHERE id = :id")
    suspend fun move(id: Long, parentId: Long?)

    @Query("UPDATE folders SET parentId = :newParent WHERE parentId = :oldParent")
    suspend fun reparentChildren(oldParent: Long, newParent: Long?)

    @Query("UPDATE documents SET folderId = :newFolder WHERE folderId = :oldFolder")
    suspend fun reparentDocuments(oldFolder: Long, newFolder: Long?)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteRow(id: Long)

    /** Borra la carpeta y sube su contenido al nivel superior (no se pierde nada). */
    @Transaction
    suspend fun deleteKeepingContents(folder: FolderEntity) {
        reparentChildren(folder.id, folder.parentId)
        reparentDocuments(folder.id, folder.parentId)
        deleteRow(folder.id)
    }
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE folderId IS :folderId ORDER BY updatedAt DESC")
    fun documentsIn(folderId: Long?): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE pinned = 1 ORDER BY pinnedAt DESC")
    fun pinned(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun search(query: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observe(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: Long): DocumentEntity?

    @Insert
    suspend fun insert(doc: DocumentEntity): Long

    @Query("UPDATE documents SET contentPath = :contentPath WHERE id = :id")
    suspend fun setContentPath(id: Long, contentPath: String)

    @Query("UPDATE documents SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE documents SET folderId = :folderId WHERE id = :id")
    suspend fun move(id: Long, folderId: Long?)

    @Query("UPDATE documents SET pinned = :pinned, pinnedAt = :at WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, at: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET updatedAt = :at WHERE id = :id")
    suspend fun touchUpdated(id: Long, at: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET lastOpenedAt = :at WHERE id = :id")
    suspend fun touchOpened(id: Long, at: Long = System.currentTimeMillis())

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RecentColorDao {
    @Query("SELECT color FROM recent_colors WHERE kind = :kind ORDER BY usedAt DESC LIMIT 12")
    fun recent(kind: ColorKind): Flow<List<Int>>

    @Upsert
    suspend fun upsert(entity: RecentColorEntity)

    @Query(
        """DELETE FROM recent_colors WHERE kind = :kind AND color NOT IN
           (SELECT color FROM recent_colors WHERE kind = :kind ORDER BY usedAt DESC LIMIT 12)"""
    )
    suspend fun trim(kind: ColorKind)
}

@Dao
interface StickyNoteDao {
    @Query("SELECT * FROM sticky_notes WHERE docId = :docId ORDER BY page, docY, y")
    fun forDocument(docId: Long): Flow<List<StickyNoteEntity>>

    @Query("SELECT * FROM sticky_notes WHERE id = :id")
    fun observe(id: Long): Flow<StickyNoteEntity?>

    @Query("SELECT * FROM sticky_notes WHERE id = :id")
    suspend fun get(id: Long): StickyNoteEntity?

    @Query("SELECT * FROM sticky_notes WHERE docId = :docId")
    suspend fun allFor(docId: Long): List<StickyNoteEntity>

    @Insert
    suspend fun insert(note: StickyNoteEntity): Long

    @Query("UPDATE sticky_notes SET title = :title, updatedAt = :at WHERE id = :id")
    suspend fun rename(id: Long, title: String, at: Long = System.currentTimeMillis())

    @Query("UPDATE sticky_notes SET icon = :icon, color = :color, size = :size WHERE id = :id")
    suspend fun setLook(id: Long, icon: String, color: Int, size: Int)

    @Query("UPDATE sticky_notes SET page = :page, x = :x, y = :y, block = :block, docY = :docY WHERE id = :id")
    suspend fun move(id: Long, page: Int, x: Float, y: Float, block: String?, docY: Float)

    @Query("UPDATE sticky_notes SET plainText = :plainText, updatedAt = :at WHERE id = :id")
    suspend fun setPlainText(id: Long, plainText: String, at: Long = System.currentTimeMillis())

    @Query("DELETE FROM sticky_notes WHERE id = :id")
    suspend fun delete(id: Long)
}
