package dev.healthtracker.app.core.sync

/**
 * Fields every synced row carries (see docs/SPEC.md, "Sync rules").
 * Timestamps are epoch milliseconds, UTC.
 */
interface SyncRecord {
    val id: String
    val source: String
    val sourceRecordId: String
    val updatedAt: Long
    val deletedAt: Long?
}

/** `(source, source_record_id)` is the natural key that keeps re-syncs from duplicating rows. */
data class SyncKey(val source: String, val sourceRecordId: String)

val SyncRecord.syncKey: SyncKey get() = SyncKey(source, sourceRecordId)

object SyncMerge {

    /**
     * Last-write-wins: returns the record that should be kept when [local] and [incoming]
     * describe the same row. A tie keeps [local] so a re-sync of unchanged data is a no-op.
     */
    fun <T : SyncRecord> resolve(local: T?, incoming: T): T {
        if (local == null) return incoming
        require(local.syncKey == incoming.syncKey) { "Records have different sync keys" }
        return if (incoming.updatedAt > local.updatedAt) incoming else local
    }

    /** Collapses a batch to one record per sync key, keeping the latest `updatedAt`. */
    fun <T : SyncRecord> dedupe(records: Iterable<T>): List<T> {
        val byKey = LinkedHashMap<SyncKey, T>()
        for (r in records) {
            byKey[r.syncKey] = resolve(byKey[r.syncKey], r)
        }
        return byKey.values.toList()
    }

    /**
     * Merges [incoming] into [existing] and returns only the records that need to be written
     * (new keys, or newer versions of existing keys). Unchanged rows are skipped.
     */
    fun <T : SyncRecord> changesToApply(existing: Iterable<T>, incoming: Iterable<T>): List<T> {
        val current = existing.associateBy { it.syncKey }
        return dedupe(incoming).filter { candidate ->
            val have = current[candidate.syncKey]
            have == null || resolve(have, candidate) !== have
        }
    }
}
