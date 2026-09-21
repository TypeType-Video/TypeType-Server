package dev.typetype.server.portability

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class PortabilityJobKind {
    @SerialName("import")
    IMPORT,
    @SerialName("export")
    EXPORT,
}

@Serializable
enum class PortabilityJobState {
    @SerialName("queued")
    QUEUED,
    @SerialName("analyzing")
    ANALYZING,
    @SerialName("ready")
    READY,
    @SerialName("applying")
    APPLYING,
    @SerialName("encoding")
    ENCODING,
    @SerialName("completed")
    COMPLETED,
    @SerialName("failed")
    FAILED,
    @SerialName("cancelled")
    CANCELLED,
}

@Serializable
enum class PortabilityProgressPhase {
    @SerialName("analyzing")
    ANALYZING,
    @SerialName("collecting")
    COLLECTING,
    @SerialName("applying")
    APPLYING,
    @SerialName("encoding")
    ENCODING,
}

@Serializable
enum class PortabilityProgressUnit {
    @SerialName("records")
    RECORDS,
    @SerialName("categories")
    CATEGORIES,
    @SerialName("bytes")
    BYTES,
}

@Serializable
data class PortabilityJobProgress(
    val phase: PortabilityProgressPhase,
    val unit: PortabilityProgressUnit,
    val processed: Long,
    val total: Long? = null,
)

@Serializable
data class PortabilityPreview(
    val detection: PortabilityDetectionItem,
    val counts: Map<String, Long>,
    val duplicates: Long,
    val issues: List<PortabilityIssue>,
)

@Serializable
data class PortabilityDetectionItem(
    val format: PortabilityFormat,
    val formatVersion: String?,
    val adapterVersion: Int,
    val confidence: Int,
    val evidence: String,
)

@Serializable
data class PortabilityJobSnapshot(
    val id: String,
    val kind: PortabilityJobKind,
    val state: PortabilityJobState,
    val createdAt: Long,
    val updatedAt: Long,
    val requestId: String? = null,
    val preview: PortabilityPreview? = null,
    val result: Map<String, Long>? = null,
    val progress: PortabilityJobProgress? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

@Serializable
data class PortabilityJobReport(
    val id: String,
    val state: PortabilityJobState,
    val requestId: String? = null,
    val preview: PortabilityPreview? = null,
    val result: Map<String, Long>? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

@Serializable
data class PortabilityImportRequest(
    val categories: Set<PortabilityCategory>,
    val duplicatePolicy: PortabilityDuplicatePolicy = PortabilityDuplicatePolicy.SKIP,
)

@Serializable
data class PortabilityExportRequest(
    val format: PortabilityFormat,
    val categories: Set<PortabilityCategory>,
)

@Serializable
enum class PortabilityDuplicatePolicy {
    @SerialName("skip")
    SKIP,
    @SerialName("replace")
    REPLACE,
}
