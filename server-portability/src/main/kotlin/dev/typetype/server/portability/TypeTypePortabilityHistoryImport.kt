package dev.typetype.server.portability

import dev.typetype.server.db.tables.HistoryTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import java.util.UUID

object TypeTypePortabilityHistoryImport {
    private const val BATCH_SIZE = 500

    fun write(
        userId: String,
        source: PortabilityRecordSource,
        policy: PortabilityDuplicatePolicy,
        onRecord: () -> Unit,
    ): Long {
        if (policy == PortabilityDuplicatePolicy.REPLACE) {
            HistoryTable.deleteWhere { HistoryTable.userId eq userId }
        }
        val knownKeys = existingKeys(userId, policy)
        val pending = ArrayList<PortabilityHistory>(BATCH_SIZE)
        var count = 0L
        source.forEach(PortabilityCategory.HISTORY) { record ->
            if (record is PortabilityHistory && knownKeys.add(HistoryKey(record.video.url, record.watchedAt))) {
                pending += record
            }
            onRecord()
            if (pending.size >= BATCH_SIZE) {
                count += insertBatch(userId, pending)
                pending.clear()
            }
        }
        return count + insertBatch(userId, pending)
    }

    private fun existingKeys(userId: String, policy: PortabilityDuplicatePolicy): MutableSet<HistoryKey> {
        if (policy == PortabilityDuplicatePolicy.REPLACE) return HashSet()
        return HistoryTable.select(HistoryTable.url, HistoryTable.watchedAt)
            .where { HistoryTable.userId eq userId }
            .mapTo(HashSet()) { HistoryKey(it[HistoryTable.url], it[HistoryTable.watchedAt]) }
    }

    private fun insertBatch(userId: String, records: List<PortabilityHistory>): Long {
        if (records.isEmpty()) return 0L
        HistoryTable.batchInsert(records, shouldReturnGeneratedValues = false) { record ->
            this[HistoryTable.id] = UUID.randomUUID().toString()
            this[HistoryTable.userId] = userId
            this[HistoryTable.url] = record.video.url
            this[HistoryTable.title] = record.video.title
            this[HistoryTable.thumbnail] = record.video.thumbnailUrl
            this[HistoryTable.channelName] = record.video.channelName
            this[HistoryTable.channelUrl] = record.video.channelUrl
            this[HistoryTable.channelAvatar] = record.video.channelAvatarUrl
            this[HistoryTable.duration] = record.video.durationSeconds
            this[HistoryTable.progress] = record.positionSeconds
            this[HistoryTable.watchedAt] = record.watchedAt
        }
        return records.size.toLong()
    }

    private data class HistoryKey(val url: String, val watchedAt: Long)
}
