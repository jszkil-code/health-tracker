package dev.healthtracker.app.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private data class Row(
        override val id: String,
        override val source: String,
        override val sourceRecordId: String,
        override val updatedAt: Long,
        override val deletedAt: Long? = null,
        val value: Int = 0,
    ) : SyncRecord

    private fun row(rid: String, updated: Long, value: Int = 0, source: String = "health_connect") =
        Row(id = "id-$rid-$updated", source = source, sourceRecordId = rid, updatedAt = updated, value = value)

    @Test fun newRecordWinsWhenNothingLocal() {
        val incoming = row("a", 10)
        assertSame(incoming, SyncMerge.resolve(null, incoming))
    }

    @Test fun newerUpdatedAtWins() {
        val local = row("a", 10, value = 1)
        val incoming = row("a", 20, value = 2)
        assertSame(incoming, SyncMerge.resolve(local, incoming))
        assertSame(incoming, SyncMerge.resolve(incoming, local))
    }

    @Test fun olderIncomingIsIgnored() {
        val local = row("a", 20)
        assertSame(local, SyncMerge.resolve(local, row("a", 10)))
    }

    @Test fun tieKeepsLocal() {
        val local = row("a", 10, value = 1)
        assertSame(local, SyncMerge.resolve(local, row("a", 10, value = 2)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun differentKeysAreRejected() {
        SyncMerge.resolve(row("a", 10), row("b", 20))
    }

    @Test fun sameRecordIdFromDifferentSourcesIsNotADuplicate() {
        val out = SyncMerge.dedupe(listOf(row("a", 1, source = "health_connect"), row("a", 1, source = "polar")))
        assertEquals(2, out.size)
    }

    @Test fun dedupeKeepsLatestPerKeyAndPreservesOrder() {
        val out = SyncMerge.dedupe(
            listOf(row("a", 1, 1), row("b", 5, 5), row("a", 3, 3), row("a", 2, 2)),
        )
        assertEquals(listOf("a" to 3, "b" to 5), out.map { it.sourceRecordId to it.value })
    }

    @Test fun resyncOfUnchangedDataProducesNoWrites() {
        val existing = listOf(row("a", 10), row("b", 10))
        assertTrue(SyncMerge.changesToApply(existing, existing.map { it.copy() }).isEmpty())
    }

    @Test fun changesToApplyReturnsOnlyNewAndNewer() {
        val existing = listOf(row("a", 10), row("b", 10))
        val incoming = listOf(row("a", 5), row("b", 11, 7), row("c", 1, 9), row("c", 2, 10))
        val changes = SyncMerge.changesToApply(existing, incoming)
        assertEquals(listOf("b" to 7, "c" to 10), changes.map { it.sourceRecordId to it.value })
    }

    @Test fun softDeleteWithNewerTimestampPropagates() {
        val local = row("a", 10)
        val deleted = row("a", 11).copy(deletedAt = 11)
        assertEquals(11L, SyncMerge.resolve(local, deleted).deletedAt)
    }
}
