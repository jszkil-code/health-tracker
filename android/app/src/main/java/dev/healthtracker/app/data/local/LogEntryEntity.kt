package dev.healthtracker.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/** One debug-log line: sync and API events only (counts, timings, errors). */
@Entity(tableName = "debug_log", indices = [Index("timestamp")])
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: LogLevel,
    val category: String,
    val message: String,
    val durationMs: Long? = null,
)
