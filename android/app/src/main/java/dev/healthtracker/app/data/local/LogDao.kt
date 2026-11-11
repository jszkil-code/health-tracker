package dev.healthtracker.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Insert
    suspend fun insert(entry: LogEntryEntity)

    @Query("SELECT * FROM debug_log ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<LogEntryEntity>>

    @Query("DELETE FROM debug_log WHERE id NOT IN (SELECT id FROM debug_log ORDER BY timestamp DESC, id DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM debug_log")
    suspend fun clear()
}
