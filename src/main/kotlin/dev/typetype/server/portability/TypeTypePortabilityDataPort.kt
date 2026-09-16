package dev.typetype.server.portability

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.services.SubscriptionFeedCacheInvalidation

class TypeTypePortabilityDataPort : PortabilityDataPort {
    override suspend fun import(
        userId: String,
        source: PortabilityRecordSource,
        request: PortabilityImportRequest,
        onCategoryComplete: (PortabilityCategory, Long) -> Unit,
        onCategoryProgress: (PortabilityCategory, Long) -> Unit,
    ): Map<String, Long> {
        val result = linkedMapOf<String, Long>()
        request.categories.sortedBy(PortabilityCategory::wireName).forEach { category ->
            val imported = DatabaseFactory.query {
                TypeTypePortabilityImport.write(userId, category, source, request.duplicatePolicy) {
                    onCategoryProgress(category, 1L)
                }
            }
            result[category.wireName] = imported
            onCategoryComplete(category, imported)
        }
        if (PortabilityCategory.SUBSCRIPTIONS in request.categories) {
            SubscriptionFeedCacheInvalidation.invalidate(userId)
        }
        return result
    }

    override suspend fun export(
        userId: String,
        categories: Set<PortabilityCategory>,
        sink: PortabilityRecordSink,
        onCategoryComplete: (PortabilityCategory, Long) -> Unit,
    ) {
        categories.sortedBy(PortabilityCategory::wireName).forEach { category ->
            sink.markCategory(category)
            DatabaseFactory.query { TypeTypePortabilityExport.write(userId, category, sink) }
            onCategoryComplete(category, sinkCount(sink, category))
        }
    }
}

private fun sinkCount(sink: PortabilityRecordSink, category: PortabilityCategory): Long = when (sink) {
    is ProgressRecordSink -> sink.count(category)
    is PortabilityRecordSource -> sink.counts()[category] ?: 0L
    else -> 0L
}
