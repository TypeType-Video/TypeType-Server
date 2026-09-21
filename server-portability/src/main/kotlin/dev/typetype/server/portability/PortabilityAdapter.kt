package dev.typetype.server.portability

import java.io.OutputStream
import java.nio.file.Path
import kotlinx.serialization.Serializable

data class PortabilityInput(
    val path: Path,
    val filename: String,
    val contentType: String?,
    val size: Long,
    val probe: ByteArray,
    val archive: PortabilityArchiveInventory?,
)

data class PortabilityDetection(
    val format: PortabilityFormat,
    val formatVersion: String?,
    val confidence: Int,
    val evidence: String,
)

interface PortabilityRecordSink {
    fun markCategory(category: PortabilityCategory)
    fun write(record: PortabilityRecord): PortabilityWriteResult
    fun issue(issue: PortabilityIssue)
    fun putLookup(namespace: String, key: String, value: String)
    fun lookup(namespace: String, key: String): String?
}

interface PortabilityRecordSource {
    fun categories(): Set<PortabilityCategory>
    fun counts(): Map<PortabilityCategory, Long>
    fun forEach(category: PortabilityCategory, block: (PortabilityRecord) -> Unit)
    fun forEachChild(category: PortabilityCategory, parentKey: String, block: (PortabilityRecord) -> Unit) {
        forEach(category) { record ->
            if (record.parentKey() == parentKey.trim().lowercase()) block(record)
        }
    }
}

interface PortabilityAdapter {
    val descriptor: PortabilityAdapterDescriptor
    val autoDetect: Boolean get() = true
    fun detect(input: PortabilityInput): PortabilityDetection?
    fun decode(input: PortabilityInput, sink: PortabilityRecordSink)
    fun assessExport(
        source: PortabilityRecordSource,
        categories: Set<PortabilityCategory>,
    ): List<PortabilityIssue> = unsupportedExportCategories(categories)
    fun encode(source: PortabilityRecordSource, output: OutputStream, categories: Set<PortabilityCategory>)

    private fun unsupportedExportCategories(categories: Set<PortabilityCategory>): List<PortabilityIssue> {
        val supported = descriptor.capabilities
            .filter { PortabilityDirection.EXPORT in it.directions }
            .mapTo(hashSetOf()) { it.category }
        return (categories - supported).map { category ->
            PortabilityIssue(category, "unsupported_export_category", "${category.wireName} cannot be exported")
        }
    }
}

data class PortabilityWriteResult(
    val inserted: Boolean,
)

@Serializable
data class PortabilityIssue(
    val category: PortabilityCategory?,
    val code: String,
    val message: String,
    val count: Long = 1L,
)
