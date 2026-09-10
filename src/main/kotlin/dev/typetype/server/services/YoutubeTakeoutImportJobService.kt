package dev.typetype.server.services

import dev.typetype.server.models.YoutubeTakeoutImportJobStatus
import dev.typetype.server.models.YoutubeTakeoutImportReportItem
import dev.typetype.server.models.YoutubeTakeoutPreviewItem
import dev.typetype.server.models.YoutubeTakeoutCommitRequest
import dev.typetype.server.models.YoutubeTakeoutCommitPlan
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path

class YoutubeTakeoutImportJobService(
    private val parser: YoutubeTakeoutParserService,
    private val previewService: YoutubeTakeoutPreviewService,
    private val importerService: YoutubeTakeoutImporterService,
    private val store: YoutubeTakeoutImportJobStore = YoutubeTakeoutImportJobStore(),
    private val statusStore: YoutubeTakeoutImportJobStatusStore = YoutubeTakeoutImportJobStatusStore(),
    private val archiveStore: YoutubeTakeoutImportJobArchiveStore = YoutubeTakeoutImportJobArchiveStore(),
    private val reportStore: YoutubeTakeoutImportJobReportStore = YoutubeTakeoutImportJobReportStore(),
    private val previewStore: YoutubeTakeoutImportJobPreviewStore = YoutubeTakeoutImportJobPreviewStore(),
    private val flagsStore: YoutubeTakeoutImportJobFlagsStore = YoutubeTakeoutImportJobFlagsStore(),
    private val privacyService: YoutubeTakeoutPrivacyService = YoutubeTakeoutPrivacyService(),
    private val cache: YoutubeTakeoutImportCache = YoutubeTakeoutImportCache(),
    private val engine: YoutubeTakeoutImportJobEngine = YoutubeTakeoutImportJobEngine(),
) : AutoCloseable {
    suspend fun create(userId: String, archivePath: Path): YoutubeTakeoutImportJobStatus {
        val jobId = store.create(userId, archivePath)
        return statusStore.getStatus(userId, jobId) ?: error("Failed to create job")
    }

    suspend fun get(userId: String, jobId: String): YoutubeTakeoutImportJobStatus? = statusStore.getStatus(userId, jobId)

    suspend fun preview(userId: String, jobId: String): YoutubeTakeoutPreviewItem {
        cache.getPreview(jobId)?.let { return it }
        previewStore.getPreview(userId, jobId)?.let { persisted ->
            val preview = Json.decodeFromString(YoutubeTakeoutPreviewItem.serializer(), persisted)
            cache.setPreview(jobId, preview)
            return preview
        }
        statusStore.updateStatus(jobId, "running", "parsing", 10)
        return try {
            val archive = archiveStore.getArchivePath(userId, jobId)
            val parsed = parser.parse(Path.of(archive))
            cache.setParsed(jobId, parsed)
            val preview = previewService.build(userId, parsed)
            cache.setPreview(jobId, preview)
            previewStore.persistPreview(jobId, Json.encodeToString(preview))
            flagsStore.setParseCompleted(jobId)
            statusStore.updateStatus(jobId, "completed", "preview_ready", 100)
            preview
        } catch (e: Exception) {
            statusStore.failStatus(jobId, "parsing_failed", e.importErrorMessage())
            throw e
        }
    }

    suspend fun commit(userId: String, jobId: String, request: YoutubeTakeoutCommitRequest?): YoutubeTakeoutImportJobStatus {
        val flags = flagsStore.getFlags(userId, jobId)
        if (flags.importCompleted) return statusStore.getStatus(userId, jobId) ?: error("Missing job")
        if (flags.importStarted) return statusStore.getStatus(userId, jobId) ?: error("Missing job")
        val plan = YoutubeTakeoutCommitPlanner.fromRequest(request)
        flagsStore.setImportStarted(jobId)
        statusStore.updateStatus(jobId, "running", "importing", 75)
        engine.startCommit(jobId, plan) { runCommit(userId, jobId, it) }
        return statusStore.getStatus(userId, jobId) ?: error("Missing job")
    }

    private suspend fun runCommit(userId: String, jobId: String, plan: YoutubeTakeoutCommitPlan): Unit {
        try {
            val flags = flagsStore.getFlags(userId, jobId)
            if (!flags.parseCompleted) preview(userId, jobId)
            statusStore.updateStatus(jobId, "running", "importing", 75)
            val parsed = cache.getParsed(jobId) ?: parser.parse(Path.of(archiveStore.getArchivePath(userId, jobId))).also {
                cache.setParsed(jobId, it)
                val preview = previewService.build(userId, it)
                previewStore.persistPreview(jobId, Json.encodeToString(preview))
                cache.setPreview(jobId, preview)
                flagsStore.setParseCompleted(jobId)
            }
            val report = importerService.commit(userId, parsed, plan)
            reportStore.persistReport(jobId, Json.encodeToString(report))
            flagsStore.setImportCompleted(jobId)
            statusStore.updateStatus(jobId, "completed", "completed", 100)
            privacyService.deleteArchive(archiveStore.getArchivePath(userId, jobId))
        } catch (e: Exception) {
            statusStore.failStatus(jobId, "import_failed", e.importErrorMessage())
        } finally {
            cache.remove(jobId)
        }
    }

    private fun Exception.importErrorMessage(): String {
        val name = this::class.simpleName ?: "ImportException"
        val detail = message?.takeIf { it.isNotBlank() } ?: "Import failed"
        return "$name: $detail"
    }

    suspend fun report(userId: String, jobId: String): YoutubeTakeoutImportReportItem? {
        val reportJson = reportStore.getReport(userId, jobId) ?: return null
        return Json.decodeFromString(YoutubeTakeoutImportReportItem.serializer(), reportJson)
    }

    suspend fun purgeExpired() = YoutubeTakeoutImportCleanupService(privacyService).purgeExpiredJobs()

    override fun close() {
        engine.close()
    }
}
