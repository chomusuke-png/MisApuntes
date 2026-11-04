package com.estudia.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FolderEntity::class, DocumentEntity::class, RecentColorEntity::class, StickyNoteEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folders(): FolderDao
    abstract fun documents(): DocumentDao
    abstract fun recentColors(): RecentColorDao
    abstract fun stickyNotes(): StickyNoteDao

    companion object {
        /** v2: notas adhesivas. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `sticky_notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `docId` INTEGER NOT NULL, `title` TEXT NOT NULL, `icon` TEXT NOT NULL,
                        `color` INTEGER NOT NULL, `page` INTEGER NOT NULL, `x` REAL NOT NULL, `y` REAL NOT NULL,
                        `block` TEXT, `docY` REAL NOT NULL, `plainText` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sticky_notes_docId` ON `sticky_notes` (`docId`)")
            }
        }

        /** v3: tamaño del pin de cada nota. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sticky_notes` ADD COLUMN `size` INTEGER NOT NULL DEFAULT 30")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "estudia.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
