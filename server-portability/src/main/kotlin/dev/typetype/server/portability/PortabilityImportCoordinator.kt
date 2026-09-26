package dev.typetype.server.portability

internal class PortabilityImportCoordinator(
    private val dataPort: PortabilityDataPort,
) {
    suspend fun apply(
        userId: String,
        source: PortabilityRecordSource,
        request: PortabilityImportRequest,
        format: PortabilityFormat?,
        counts: Map<PortabilityCategory, Long>,
        progress: PortabilityProgressReporter,
        onCommitted: (Map<String, Long>) -> Unit,
    ): Map<String, Long> {
        if (format == PortabilityFormat.YOUTUBE_TAKEOUT) {
            return YoutubeTakeoutImportWorkflow(dataPort).apply(
                userId,
                source,
                request,
                progress,
                onCommitted,
            )
        }
        val completed = linkedMapOf<String, Long>()
        return dataPort.import(
            userId,
            source,
            request,
            onCategoryProgress = { category, count ->
                progress.setStage(PortabilityImportStage.REMAINING, category, counts[category])
                progress.add(count)
            },
            onCategoryComplete = { category, count ->
                progress.setStage(PortabilityImportStage.REMAINING, category, counts[category])
                completed[category.wireName] = count
                onCommitted(completed.toMap())
                progress.checkpoint()
            },
        )
    }
}
