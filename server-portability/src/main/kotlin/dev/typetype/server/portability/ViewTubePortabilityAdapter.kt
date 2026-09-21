package dev.typetype.server.portability

import java.io.OutputStream
import java.util.zip.ZipFile

class ViewTubePortabilityAdapter : PortabilityAdapter {
    override val descriptor = PortabilityAdapterDescriptor(
        PortabilityFormat.VIEW_TUBE,
        1,
        setOf(
            viewTubeCapability(PortabilityCategory.SUBSCRIPTIONS, PortabilityFidelity.COMPLETE),
            viewTubeCapability(PortabilityCategory.HISTORY, PortabilityFidelity.COMPLETE),
            viewTubeCapability(PortabilityCategory.PROGRESS, PortabilityFidelity.COMPLETE),
        ),
        "zip",
        "application/zip",
    )

    override fun detect(input: PortabilityInput): PortabilityDetection? {
        val archive = input.archive ?: return null
        if ("user.json" !in archive.names) return null
        ZipFile(input.path.toFile()).use { zip ->
            val entry = zip.getEntry("user.json") ?: return null
            zip.getInputStream(entry).buffered().use { stream ->
                val probe = stream.readNBytes(PortabilityLimits.PROBE_BYTES).decodeToString()
                if (!probe.contains("\"username\"") || !probe.contains("\"subscriptions\"")) return null
                if (!probe.contains("\"history\"") || !probe.contains("\"settings\"")) return null
            }
        }
        return PortabilityDetection(PortabilityFormat.VIEW_TUBE, null, 99, "ViewTube user.json export")
    }

    override fun decode(input: PortabilityInput, sink: PortabilityRecordSink) {
        requireNotNull(detect(input)) { "Unsupported ViewTube backup" }
        ZipFile(input.path.toFile()).use { zip ->
            val entry = requireNotNull(zip.getEntry("user.json"))
            zip.getInputStream(entry).buffered().use { inputStream ->
                PortabilityJsonFactory.createParser(inputStream).use { parser ->
                    ViewTubePortabilityReader.read(parser, sink)
                }
            }
        }
    }

    override fun encode(
        source: PortabilityRecordSource,
        output: OutputStream,
        categories: Set<PortabilityCategory>,
    ): Unit = error("ViewTube does not provide a compatible full-backup import")
}

private fun viewTubeCapability(category: PortabilityCategory, fidelity: PortabilityFidelity) = PortabilityCapability(
    category,
    setOf(PortabilityDirection.IMPORT),
    fidelity,
)
