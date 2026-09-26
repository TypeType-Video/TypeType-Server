package dev.typetype.server.portability

import java.util.PriorityQueue

internal class YoutubeTakeoutImportWorkflow(
    private val dataPort: PortabilityDataPort,
) {
    suspend fun apply(
        userId: String,
        source: PortabilityRecordSource,
        request: PortabilityImportRequest,
        progress: PortabilityProgressReporter,
        onCommitted: (Map<String, Long>) -> Unit,
    ): Map<String, Long> {
        val counts = source.counts()
        val selected = request.categories
        val result = linkedMapOf<String, Long>()
        val started = mutableSetOf<PortabilityCategory>()
        val recentHistoryKeys = hashSetOf<String>()
        var remainingHistory = 0L

        suspend fun commit(
            category: PortabilityCategory,
            records: List<PortabilityRecord>,
            stage: PortabilityImportStage,
            stageTotal: Long?,
        ) {
            progress.setStage(stage, category, stageTotal)
            val policy = if (started.add(category)) request.duplicatePolicy else PortabilityDuplicatePolicy.SKIP
            val imported = dataPort.import(
                userId,
                PortabilityBatchRecordSource(category, records),
                PortabilityImportRequest(setOf(category), policy),
                onCategoryProgress = { _, count -> progress.add(count) },
            )[category.wireName] ?: 0L
            result[category.wireName] = Math.addExact(result[category.wireName] ?: 0L, imported)
            onCommitted(result.toMap())
            progress.checkpoint()
        }

        if (PortabilityCategory.SUBSCRIPTIONS in selected) {
            val total = counts[PortabilityCategory.SUBSCRIPTIONS] ?: 0L
            val processed = processPaged(
                source,
                PortabilityCategory.SUBSCRIPTIONS,
                progress,
                SUBSCRIPTION_BATCH_SIZE,
                { true },
            ) { records -> commit(PortabilityCategory.SUBSCRIPTIONS, records, PortabilityImportStage.SUBSCRIPTIONS, total) }
            if (processed == 0L) {
                commit(PortabilityCategory.SUBSCRIPTIONS, emptyList(), PortabilityImportStage.SUBSCRIPTIONS, total)
            }
        }

        if (PortabilityCategory.HISTORY in selected) {
            val total = counts[PortabilityCategory.HISTORY] ?: 0L
            val recent = latestHistory(source, progress)
            recentHistoryKeys += recent.map { it.stableKey() }
            commit(PortabilityCategory.HISTORY, recent, PortabilityImportStage.RECENT_HISTORY, recent.size.toLong())
            remainingHistory = (total - recent.size).coerceAtLeast(0L)
        }

        if (PortabilityCategory.PLAYLISTS in selected) {
            val total = counts[PortabilityCategory.PLAYLISTS] ?: 0L
            var processed = processPaged(
                source,
                PortabilityCategory.PLAYLISTS,
                progress,
                PLAYLIST_BATCH_SIZE,
                { it is PortabilityPlaylist },
            ) { records -> commit(PortabilityCategory.PLAYLISTS, records, PortabilityImportStage.PLAYLISTS, total) }
            processed += processPaged(
                source,
                PortabilityCategory.PLAYLISTS,
                progress,
                PLAYLIST_VIDEO_BATCH_SIZE,
                { it is PortabilityPlaylistVideo },
            ) { records -> commit(PortabilityCategory.PLAYLISTS, records, PortabilityImportStage.PLAYLISTS, total) }
            processed += processPaged(
                source,
                PortabilityCategory.PLAYLISTS,
                progress,
                PLAYLIST_VIDEO_BATCH_SIZE,
                { it !is PortabilityPlaylist && it !is PortabilityPlaylistVideo },
            ) { records -> commit(PortabilityCategory.PLAYLISTS, records, PortabilityImportStage.PLAYLISTS, total) }
            if (processed == 0L) {
                commit(PortabilityCategory.PLAYLISTS, emptyList(), PortabilityImportStage.PLAYLISTS, total)
            }
        }

        if (PortabilityCategory.HISTORY in selected && remainingHistory > 0L) {
            processPaged(
                source,
                PortabilityCategory.HISTORY,
                progress,
                HISTORY_BATCH_SIZE,
                { it !is PortabilityHistory || it.stableKey() !in recentHistoryKeys },
            ) { records ->
                commit(PortabilityCategory.HISTORY, records, PortabilityImportStage.HISTORY, remainingHistory)
            }
        }

        selected
            .filterNot {
                it == PortabilityCategory.SUBSCRIPTIONS ||
                    it == PortabilityCategory.HISTORY ||
                    it == PortabilityCategory.PLAYLISTS
            }
            .sortedBy(PortabilityCategory::wireName)
            .forEach { category ->
                progress.setStage(PortabilityImportStage.REMAINING, category, counts[category])
                dataPort.import(
                    userId,
                    source,
                    PortabilityImportRequest(setOf(category), request.duplicatePolicy),
                    onCategoryProgress = { _, count -> progress.add(count) },
                    onCategoryComplete = { _, imported ->
                        result[category.wireName] = imported
                        onCommitted(result.toMap())
                        progress.checkpoint()
                    },
                )
            }
        return result
    }

    private suspend fun processPaged(
        source: PortabilityRecordSource,
        category: PortabilityCategory,
        progress: PortabilityProgressReporter,
        batchSize: Int,
        include: (PortabilityRecord) -> Boolean,
        commit: suspend (List<PortabilityRecord>) -> Unit,
    ): Long {
        var cursor: Long? = null
        var matched = 0L
        val pending = ArrayList<PortabilityRecord>(batchSize)
        while (true) {
            progress.ensureActive()
            val page = source.readBatch(category, cursor, READ_PAGE_SIZE)
            if (page.records.isEmpty()) break
            check(page.nextCursor != null && page.nextCursor != cursor) { "Portability source cursor did not advance" }
            cursor = page.nextCursor
            page.records.forEach { record ->
                if (include(record)) {
                    matched++
                    pending += record
                    if (pending.size == batchSize) {
                        commit(pending.toList())
                        pending.clear()
                    }
                }
            }
        }
        if (pending.isNotEmpty()) commit(pending)
        return matched
    }

    private fun latestHistory(
        source: PortabilityRecordSource,
        progress: PortabilityProgressReporter,
    ): List<PortabilityHistory> {
        val oldestFirst = compareBy<PortabilityHistory>({ it.watchedAt }, { it.stableKey() })
        val recent = PriorityQueue(RECENT_HISTORY_LIMIT, oldestFirst)
        source.forEach(PortabilityCategory.HISTORY) { record ->
            progress.ensureActive()
            if (record !is PortabilityHistory) return@forEach
            if (recent.size < RECENT_HISTORY_LIMIT) recent += record
            else if (oldestFirst.compare(record, recent.peek()) > 0) {
                recent.remove()
                recent += record
            }
        }
        return recent.sortedWith(compareByDescending<PortabilityHistory> { it.watchedAt }.thenBy { it.stableKey() })
    }

    private companion object {
        const val READ_PAGE_SIZE = 500
        const val SUBSCRIPTION_BATCH_SIZE = 50
        const val RECENT_HISTORY_LIMIT = 20
        const val HISTORY_BATCH_SIZE = 500
        const val PLAYLIST_BATCH_SIZE = 5
        const val PLAYLIST_VIDEO_BATCH_SIZE = 50
    }
}
