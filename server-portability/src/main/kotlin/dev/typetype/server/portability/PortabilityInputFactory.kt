package dev.typetype.server.portability

import java.nio.file.Files
import java.nio.file.Path

object PortabilityInputFactory {
    fun create(path: Path, filename: String, contentType: String?): PortabilityInput {
        val size = Files.size(path)
        require(size in 1..PortabilityLimits.MAX_UPLOAD_BYTES) { "Backup file size is outside the allowed range" }
        val probe = Files.newInputStream(path).use { it.readNBytes(PortabilityLimits.PROBE_BYTES) }
        return PortabilityInput(
            path = path,
            filename = filename,
            contentType = contentType,
            size = size,
            probe = probe,
            archive = PortabilityArchiveInspector.inspect(path),
        )
    }
}
