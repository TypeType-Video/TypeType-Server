package dev.typetype.server.portability

import java.io.OutputStream

class PipePipePortabilityAdapter : PortabilityAdapter {
    override val descriptor = PortabilityAdapterDescriptor(
        PortabilityFormat.PIPE_PIPE,
        2,
        newPipeArchiveCapabilities(),
        "zip",
        "application/zip",
    )

    override fun detect(input: PortabilityInput): PortabilityDetection? {
        val version = NewPipeArchiveDatabase.userVersion(input) ?: return null
        if (version < PIPE_PIPE_DATABASE_VERSION) return null
        return PortabilityDetection(PortabilityFormat.PIPE_PIPE, version.toString(), 100, "PipePipe SQLite schema version")
    }

    override fun decode(input: PortabilityInput, sink: PortabilityRecordSink) {
        requireNotNull(detect(input)) { "Unsupported PipePipe backup" }
        NewPipeArchiveDatabase.read(input) { NewPipeDatabasePortabilityReader.read(it, sink) }
    }

    override fun assessExport(
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
    ): List<PortabilityIssue> = super.assessExport(source, categories) +
        newPipeArchiveExportIssues(source, categories, NewPipeArchiveTarget.PIPE_PIPE)

    override fun encode(source: PortabilityRecordSource, output: OutputStream, categories: Set<PortabilityCategory>) =
        NewPipeArchivePortabilityWriter.write(source, output, categories, NewPipeArchiveTarget.PIPE_PIPE)

    private companion object {
        const val PIPE_PIPE_DATABASE_VERSION = 900
    }
}

fun archiveImportCapabilities(): Set<PortabilityCapability> = setOf(
    PortabilityCategory.SUBSCRIPTIONS,
    PortabilityCategory.SUBSCRIPTION_GROUPS,
    PortabilityCategory.HISTORY,
    PortabilityCategory.PLAYLISTS,
    PortabilityCategory.PROGRESS,
    PortabilityCategory.SEARCH_HISTORY,
    PortabilityCategory.SAVED_PLAYLISTS,
).mapTo(linkedSetOf()) { category ->
    PortabilityCapability(category, setOf(PortabilityDirection.IMPORT), PortabilityFidelity.COMPLETE)
}
