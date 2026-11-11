package dev.healthtracker.app.data.log

import dev.healthtracker.app.data.local.LogDao
import dev.healthtracker.app.data.local.LogEntryEntity
import dev.healthtracker.app.data.local.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes sync and API events to the on-device debug log. Log counts, timings and error
 * types only; never raw health values, photos or keys. Messages pass through [LogSanitizer].
 */
@Singleton
class AppLogger @Inject constructor(private val dao: LogDao) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun info(category: String, message: String, durationMs: Long? = null) = log(LogLevel.INFO, category, message, durationMs)
    fun warn(category: String, message: String, durationMs: Long? = null) = log(LogLevel.WARN, category, message, durationMs)

    /** Logs the error type and a sanitized message; never the payload that caused it. */
    fun error(category: String, message: String, error: Throwable? = null) {
        val detail = error?.let { " (${it::class.simpleName}: ${it.message.orEmpty()})" }.orEmpty()
        log(LogLevel.ERROR, category, message + detail, null)
    }

    fun log(level: LogLevel, category: String, message: String, durationMs: Long?) {
        val entry = LogEntryEntity(
            timestamp = System.currentTimeMillis(),
            level = level,
            category = category,
            message = LogSanitizer.sanitize(message),
            durationMs = durationMs,
        )
        scope.launch {
            dao.insert(entry)
            dao.trimTo(MAX_ENTRIES)
        }
    }

    fun recent(limit: Int = 500): Flow<List<LogEntryEntity>> = dao.observeRecent(limit)

    suspend fun clear() = dao.clear()

    private companion object {
        const val MAX_ENTRIES = 2000
    }
}
