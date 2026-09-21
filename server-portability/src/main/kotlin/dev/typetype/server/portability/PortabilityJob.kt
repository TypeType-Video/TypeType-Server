package dev.typetype.server.portability

import kotlinx.coroutines.Job
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

class PortabilityJob(
    val id: String,
    val ownerId: String,
    val kind: PortabilityJobKind,
    val directory: Path,
    val requestId: String?,
    private val clock: () -> Long,
) {
    val createdAt = clock()
    private val value = AtomicReference(
        PortabilityJobSnapshot(id, kind, PortabilityJobState.QUEUED, createdAt, createdAt, requestId),
    )
    @Volatile
    var task: Job? = null
    @Volatile
    var spool: PortabilitySpool? = null
    @Volatile
    var artifact: Path? = null

    fun snapshot(): PortabilityJobSnapshot = value.get()

    fun report(): PortabilityJobReport = value.get().let {
        PortabilityJobReport(it.id, it.state, it.requestId, it.preview, it.result, it.errorCode, it.errorMessage)
    }

    fun isTerminal(): Boolean = value.get().state in TERMINAL_STATES

    fun isCancelled(): Boolean = value.get().state == PortabilityJobState.CANCELLED

    fun isTaskComplete(): Boolean = task?.isCompleted != false

    fun updateProgress(progress: PortabilityJobProgress) {
        while (true) {
            val current = value.get()
            if (current.state in TERMINAL_STATES) return
            val next = current.copy(updatedAt = clock(), progress = progress)
            if (value.compareAndSet(current, next)) return
        }
    }

    fun transition(
        expected: Set<PortabilityJobState>,
        state: PortabilityJobState,
        preview: PortabilityPreview? = value.get().preview,
        result: Map<String, Long>? = value.get().result,
        errorCode: String? = null,
        errorMessage: String? = null,
    ) {
        while (true) {
            val current = value.get()
            check(current.state in expected) { "Invalid portability transition ${current.state} -> $state" }
            val next = current.copy(
                state = state,
                updatedAt = clock(),
                preview = preview,
                result = result,
                progress = current.progress,
                errorCode = errorCode,
                errorMessage = errorMessage,
            )
            if (value.compareAndSet(current, next)) return
        }
    }

    fun tryTransition(
        expected: Set<PortabilityJobState>,
        state: PortabilityJobState,
        errorCode: String? = null,
        errorMessage: String? = null,
    ): Boolean {
        while (true) {
            val current = value.get()
            if (current.state !in expected) return false
            val next = current.copy(
                state = state,
                updatedAt = clock(),
                errorCode = errorCode,
                errorMessage = errorMessage,
            )
            if (value.compareAndSet(current, next)) return true
        }
    }

    fun fail(error: Exception) {
        val code = portabilityErrorCode(error)
        logger.error("Portability job failed jobId={} requestId={} code={}", id, requestId ?: "none", code, error)
        tryTransition(
            PortabilityJobState.entries.toSet() - TERMINAL_STATES,
            PortabilityJobState.FAILED,
            errorCode = code,
            errorMessage = portabilityErrorMessage(error),
        )
    }

    fun delete() {
        task?.cancel()
        spool?.delete()
        if (!Files.exists(directory)) return
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(PortabilityJob::class.java)
        val TERMINAL_STATES = setOf(
            PortabilityJobState.COMPLETED,
            PortabilityJobState.FAILED,
            PortabilityJobState.CANCELLED,
        )
    }
}
