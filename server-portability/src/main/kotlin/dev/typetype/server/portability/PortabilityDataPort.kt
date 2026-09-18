package dev.typetype.server.portability

interface PortabilityDataPort {
    suspend fun import(
        userId: String,
        source: PortabilityRecordSource,
        request: PortabilityImportRequest,
        onCategoryComplete: (PortabilityCategory, Long) -> Unit = { _, _ -> },
        onCategoryProgress: (PortabilityCategory, Long) -> Unit = { _, _ -> },
    ): Map<String, Long>

    suspend fun export(
        userId: String,
        categories: Set<PortabilityCategory>,
        sink: PortabilityRecordSink,
        onCategoryComplete: (PortabilityCategory, Long) -> Unit = { _, _ -> },
    )
}
