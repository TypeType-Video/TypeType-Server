package dev.typetype.server.portability

import java.io.OutputStream
import java.util.zip.ZipFile

class GrayjayPortabilityAdapter : PortabilityAdapter {
    override val descriptor = PortabilityAdapterDescriptor(
        PortabilityFormat.GRAYJAY,
        1,
        setOf(
            grayjayCapability(PortabilityCategory.SUBSCRIPTIONS, BOTH, PortabilityFidelity.PARTIAL),
            grayjayCapability(PortabilityCategory.SUBSCRIPTION_GROUPS, BOTH, PortabilityFidelity.COMPLETE),
            grayjayCapability(PortabilityCategory.HISTORY, BOTH, PortabilityFidelity.PARTIAL),
            grayjayCapability(PortabilityCategory.PROGRESS, IMPORT, PortabilityFidelity.COMPLETE),
            grayjayCapability(PortabilityCategory.PLAYLISTS, BOTH, PortabilityFidelity.PARTIAL),
            grayjayCapability(PortabilityCategory.WATCH_LATER, BOTH, PortabilityFidelity.PARTIAL),
        ),
        "zip",
        "application/zip",
    )

    override fun detect(input: PortabilityInput): PortabilityDetection? {
        val archive = input.archive ?: return null
        if ("exportInfo" !in archive.names || archive.names.none { it.startsWith("stores/") }) return null
        val version = ZipFile(input.path.toFile()).use { zip ->
            val entry = zip.getEntry("exportInfo") ?: return null
            zip.getInputStream(entry).buffered().use { stream ->
                Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")
                    .find(stream.readNBytes(PortabilityLimits.PROBE_BYTES).decodeToString())
                    ?.groupValues?.get(1)
            }
        }
        if (version != "1") return null
        return PortabilityDetection(PortabilityFormat.GRAYJAY, version, 99, "Grayjay exportInfo and stores")
    }

    override fun decode(input: PortabilityInput, sink: PortabilityRecordSink) {
        requireNotNull(detect(input)) { "Unsupported or encrypted Grayjay backup" }
        ZipFile(input.path.toFile()).use { zip -> GrayjayPortabilityReader.read(zip, sink) }
    }

    override fun assessExport(
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
    ): List<PortabilityIssue> = super.assessExport(source, categories) + categories.mapNotNull { category ->
        val fidelity = descriptor.capabilities.firstOrNull { it.category == category }?.fidelity
        if (fidelity != PortabilityFidelity.PARTIAL) return@mapNotNull null
        PortabilityIssue(category, "grayjay_partial_metadata", "Grayjay reconstructs this category from URLs, so some TypeType metadata is not represented")
    }

    override fun encode(
        source: PortabilityRecordSource,
        output: OutputStream,
        categories: Set<PortabilityCategory>,
    ) = GrayjayPortabilityWriter.write(source, output, categories)
}

private val IMPORT = setOf(PortabilityDirection.IMPORT)
private val BOTH = setOf(PortabilityDirection.IMPORT, PortabilityDirection.EXPORT)

private fun grayjayCapability(
    category: PortabilityCategory,
    directions: Set<PortabilityDirection>,
    fidelity: PortabilityFidelity,
) = PortabilityCapability(category, directions, fidelity)
