package dev.typetype.server.portability

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

class PortabilityEngine internal constructor(
    private val registry: PortabilityRegistry,
    private val dataPort: PortabilityDataPort,
    private val store: PortabilityJobStore,
    private val scope: CoroutineScope,
) : AutoCloseable {
    fun formats(): List<PortabilityAdapterDescriptor> = registry.descriptors()

    fun startImportPreview(
        userId: String,
        upload: Path,
        filename: String,
        contentType: String?,
        formatHint: PortabilityFormat? = null,
        requestId: String? = null,
    ): PortabilityJobSnapshot {
        val job = store.create(userId, PortabilityJobKind.IMPORT, requestId)
        val saved = job.directory.resolve("upload")
        try {
            Files.move(upload, saved)
        } catch (error: Exception) {
            store.remove(userId, job.id)
            throw error
        }
        job.task = scope.launch { analyze(job, saved, filename, contentType, formatHint) }
        return job.snapshot()
    }

    fun startExport(
        userId: String,
        format: PortabilityFormat,
        categories: Set<PortabilityCategory>,
        requestId: String? = null,
    ): PortabilityJobSnapshot {
        require(categories.isNotEmpty()) { "At least one category is required" }
        val job = store.create(userId, PortabilityJobKind.EXPORT, requestId)
        job.task = scope.launch { export(job, format, categories) }
        return job.snapshot()
    }

    fun snapshot(userId: String, id: String): PortabilityJobSnapshot = store.get(userId, id).snapshot()

    fun report(userId: String, id: String): PortabilityJobReport = store.get(userId, id).report()

    fun artifact(userId: String, id: String): Path {
        val job = store.get(userId, id)
        check(job.snapshot().state == PortabilityJobState.COMPLETED) { "Export is not complete" }
        return requireNotNull(job.artifact) { "Export artifact is unavailable" }
    }

    fun applyImport(userId: String, id: String, request: PortabilityImportRequest): PortabilityJobSnapshot {
        require(request.categories.isNotEmpty()) { "At least one category is required" }
        val job = store.get(userId, id)
        val preview = requireNotNull(job.snapshot().preview) { "Import preview is not ready" }
        require(request.categories.all { it.wireName in preview.counts }) { "A selected category is unavailable" }
        job.transition(setOf(PortabilityJobState.READY), PortabilityJobState.APPLYING)
        job.task = scope.launch { apply(job, request) }
        return job.snapshot()
    }

    fun cancel(userId: String, id: String): PortabilityJobSnapshot {
        val job = store.get(userId, id)
        val state = job.snapshot().state
        if (state in TERMINAL_STATES) return job.snapshot()
        if (job.tryTransition(ACTIVE_STATES, PortabilityJobState.CANCELLED)) job.task?.cancel()
        return job.snapshot()
    }

    fun delete(userId: String, id: String) {
        val job = store.get(userId, id)
        check(!job.isCancelled() || job.isTaskComplete()) { "Cancelled job is still stopping" }
        store.remove(userId, id)
    }

    override fun close() {
        scope.cancel()
        store.close()
    }

    private suspend fun analyze(
        job: PortabilityJob,
        upload: Path,
        filename: String,
        contentType: String?,
        formatHint: PortabilityFormat?,
    ) {
        try {
            runJob(job, PortabilityJobState.ANALYZING) {
                val input = withContext(Dispatchers.IO) { PortabilityInputFactory.create(upload, filename, contentType) }
                val (adapter, detection) = registry.detect(input, formatHint)
                val spool = PortabilitySpool.create(job.directory)
                job.spool = spool
                val progress = PortabilityProgressReporter(
                    job,
                    PortabilityProgressPhase.ANALYZING,
                    PortabilityProgressUnit.RECORDS,
                )
                withContext(Dispatchers.IO) { adapter.decode(input, ProgressRecordSink(spool, progress)) }
                progress.finish()
                val preview = PortabilityPreview(
                    detection = detection.toItem(adapter.descriptor.adapterVersion),
                    counts = spool.counts().mapKeys { it.key.wireName },
                    duplicates = spool.duplicateCount(),
                    issues = spool.issues(),
                )
                job.transition(setOf(PortabilityJobState.ANALYZING), PortabilityJobState.READY, preview)
            }
        } finally {
            Files.deleteIfExists(upload)
        }
    }

    private suspend fun apply(job: PortabilityJob, request: PortabilityImportRequest) {
        runJob(job, null) {
            val source = requireNotNull(job.spool)
            val counts = source.counts()
            val total = request.categories.sumOf { counts[it] ?: 0L }
            val progress = PortabilityProgressReporter(
                job,
                PortabilityProgressPhase.APPLYING,
                PortabilityProgressUnit.RECORDS,
                total,
            )
            val result = dataPort.import(
                job.ownerId,
                source,
                request,
                onCategoryProgress = { _, count -> progress.add(count) },
                onCategoryComplete = { _, _ -> },
            )
            progress.finish()
            job.transition(setOf(PortabilityJobState.APPLYING), PortabilityJobState.COMPLETED, result = result)
        }
    }

    private suspend fun export(job: PortabilityJob, format: PortabilityFormat, categories: Set<PortabilityCategory>) {
        runJob(job, PortabilityJobState.ENCODING) {
            val adapter = registry.adapter(format)
            val spool = PortabilitySpool.create(job.directory)
            job.spool = spool
            val collecting = PortabilityProgressReporter(
                job,
                PortabilityProgressPhase.COLLECTING,
                PortabilityProgressUnit.RECORDS,
            )
            dataPort.export(job.ownerId, categories, ProgressRecordSink(spool, collecting))
            collecting.finish()
            val issues = adapter.assessExport(spool, categories)
            require(issues.none { it.code == "unsupported_export_category" }) { "Export category is unsupported" }
            val artifact = job.directory.resolve("export.${adapter.descriptor.defaultExtension}")
            val encoding = PortabilityProgressReporter(
                job,
                PortabilityProgressPhase.ENCODING,
                PortabilityProgressUnit.BYTES,
                interval = 64L * 1024L,
            )
            withContext(Dispatchers.IO) {
                Files.newOutputStream(artifact).use { raw ->
                    ProgressOutputStream(raw, encoding).buffered().use { adapter.encode(spool, it, categories) }
                }
            }
            encoding.finish()
            job.artifact = artifact
            job.transition(
                setOf(PortabilityJobState.ENCODING),
                PortabilityJobState.COMPLETED,
                preview = PortabilityPreview(
                    PortabilityDetectionItem(format, null, adapter.descriptor.adapterVersion, 100, "Requested export format"),
                    spool.counts().mapKeys { it.key.wireName },
                    spool.duplicateCount(),
                    issues,
                ),
            )
        }
    }

    private suspend fun runJob(job: PortabilityJob, initial: PortabilityJobState?, block: suspend () -> Unit) {
        try {
            initial?.let { job.transition(setOf(PortabilityJobState.QUEUED), it) }
            block()
        } catch (error: CancellationException) {
            job.tryTransition(ACTIVE_STATES, PortabilityJobState.CANCELLED)
            throw error
        } catch (error: Exception) {
            job.fail(error)
        }
    }

    private companion object {
        val TERMINAL_STATES = setOf(
            PortabilityJobState.COMPLETED,
            PortabilityJobState.FAILED,
            PortabilityJobState.CANCELLED,
        )
        val ACTIVE_STATES = PortabilityJobState.entries.toSet() - TERMINAL_STATES
    }
}

private fun PortabilityDetection.toItem(adapterVersion: Int) = PortabilityDetectionItem(
    format,
    formatVersion,
    adapterVersion,
    confidence,
    evidence,
)
