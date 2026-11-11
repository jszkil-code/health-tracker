package dev.healthtracker.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local encrypted store (Room + SQLCipher). Phase 1 holds only the debug log; the synced
 * health tables from docs/SPEC.md "Data model" are added in Phase 2 with a migration.
 */
@Database(entities = [LogEntryEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao

    companion object {
        const val NAME = "health.db"
    }
}
