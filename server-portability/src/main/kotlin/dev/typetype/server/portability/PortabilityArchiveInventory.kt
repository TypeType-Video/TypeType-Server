package dev.typetype.server.portability

import java.nio.file.Path
import java.util.zip.ZipFile

data class PortabilityArchiveEntry(
    val name: String,
    val compressedSize: Long,
    val expandedSize: Long,
)

data class PortabilityArchiveInventory(
    val entries: List<PortabilityArchiveEntry>,
    val expandedBytes: Long,
) {
    val names: Set<String> = entries.mapTo(linkedSetOf()) { it.name }
}

object PortabilityArchiveInspector {
    fun inspect(path: Path): PortabilityArchiveInventory? {
        if (!hasZipSignature(path)) return null
        ZipFile(path.toFile()).use { zip ->
            val entries = ArrayList<PortabilityArchiveEntry>()
            var expandedTotal = 0L
            val iterator = zip.entries().asIterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                require(entries.size < PortabilityLimits.MAX_ARCHIVE_ENTRIES) { "Archive contains too many entries" }
                validateName(entry.name)
                if (entry.isDirectory) continue
                val expanded = entry.size
                val compressed = entry.compressedSize
                require(expanded in 0..PortabilityLimits.MAX_ARCHIVE_ENTRY_BYTES) { "Archive entry is too large" }
                expandedTotal = Math.addExact(expandedTotal, expanded)
                require(expandedTotal <= PortabilityLimits.MAX_ARCHIVE_EXPANDED_BYTES) { "Archive expands beyond the limit" }
                if (expanded > 0L && compressed == 0L) error("Archive entry has an invalid compression size")
                if (expanded > 0L && compressed > 0L) {
                    require(expanded / compressed <= PortabilityLimits.MAX_COMPRESSION_RATIO) {
                        "Archive entry exceeds the compression ratio limit"
                    }
                }
                entries += PortabilityArchiveEntry(entry.name, compressed, expanded)
            }
            return PortabilityArchiveInventory(entries, expandedTotal)
        }
    }

    private fun hasZipSignature(path: Path): Boolean {
        if (path.toFile().length() < 4L) return false
        return path.toFile().inputStream().use { input ->
            input.read() == 0x50 && input.read() == 0x4b
        }
    }

    private fun validateName(name: String) {
        require(name.isNotBlank() && '\u0000' !in name) { "Archive entry has an invalid name" }
        val normalized = Path.of(name.replace('\\', '/')).normalize()
        require(!normalized.isAbsolute && normalized.none { it.toString() == ".." }) {
            "Archive entry escapes its root"
        }
    }
}
