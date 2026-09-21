package dev.typetype.server.services

import dev.typetype.server.db.tables.HistoryTable
import dev.typetype.server.db.tables.ProgressTable
import dev.typetype.server.models.HistoryItem
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll

object HistoryProgressMapper {
    fun toHistoryItems(userId: String, rows: List<ResultRow>): List<HistoryItem> {
        val items = rows.map { it.toHistoryItem() }
        val savedProgress = savedProgressSeconds(userId, items.map { it.url })
        return mergeSavedProgress(items, savedProgress)
    }

    fun toHistoryItemsForExport(userId: String, rows: List<ResultRow>): List<HistoryItem> {
        val items = rows.map { it.toHistoryItem() }
        return mergeSavedProgress(items, savedProgressSeconds(userId))
    }

    fun savedProgressSeconds(userId: String, videoUrl: String): Long? = savedProgressSeconds(userId, listOf(videoUrl))[videoUrl]

    fun savedProgressSeconds(userId: String, videoUrls: List<String>): Map<String, Long> {
        val urls = videoUrls.distinct()
        if (urls.isEmpty()) return emptyMap()
        return progressLookupBatches(urls)
            .flatMap { batch -> progressRows(userId, batch) }
            .associate { it[ProgressTable.videoUrl] to it[ProgressTable.position].toSeconds() }
    }

    fun progressLookupBatches(videoUrls: List<String>): List<List<String>> =
        videoUrls.distinct().chunked(PROGRESS_LOOKUP_BATCH_SIZE)

    private fun savedProgressSeconds(userId: String): Map<String, Long> =
        ProgressTable.selectAll()
            .where { ProgressTable.userId eq userId }
            .associate { it[ProgressTable.videoUrl] to it[ProgressTable.position].toSeconds() }

    private fun progressRows(userId: String, videoUrls: List<String>) = ProgressTable.selectAll()
        .where { (ProgressTable.userId eq userId) and (ProgressTable.videoUrl inList videoUrls) }
        .toList()

    private fun mergeSavedProgress(
        items: List<HistoryItem>,
        savedProgress: Map<String, Long>,
    ) = items.map { YoutubeTypeTypeMapper.historyItem(it).withSavedProgress(savedProgress[it.url]) }

    private fun HistoryItem.withSavedProgress(savedProgress: Long?): HistoryItem =
        copy(progress = maxOf(progress, savedProgress ?: 0L))

    private fun Long.toSeconds(): Long = coerceAtLeast(0L) / 1_000L

    private fun ResultRow.toHistoryItem(): HistoryItem = HistoryItem(
        id = this[HistoryTable.id],
        url = this[HistoryTable.url],
        title = this[HistoryTable.title],
        thumbnail = this[HistoryTable.thumbnail],
        channelName = this[HistoryTable.channelName],
        channelUrl = this[HistoryTable.channelUrl],
        channelAvatar = this[HistoryTable.channelAvatar],
        duration = this[HistoryTable.duration],
        progress = this[HistoryTable.progress],
        watchedAt = this[HistoryTable.watchedAt],
    )
}

private const val PROGRESS_LOOKUP_BATCH_SIZE = 1_000
