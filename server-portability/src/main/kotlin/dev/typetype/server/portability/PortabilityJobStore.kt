package dev.typetype.server.portability

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PortabilityJobStore(
    private val root: Path,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retentionMs: Long = DEFAULT_RETENTION_MS,
) : AutoCloseable {
    private val jobs = ConcurrentHashMap<String, PortabilityJob>()

    init {
        Files.createDirectories(root)
    }

    fun create(ownerId: String, kind: PortabilityJobKind, requestId: String? = null): PortabilityJob {
        cleanup()
        val id = UUID.randomUUID().toString()
        val directory = Files.createDirectory(root.resolve(id))
        return PortabilityJob(id, ownerId, kind, directory, requestId, clock).also { jobs[id] = it }
    }

    fun get(ownerId: String, id: String): PortabilityJob = jobs[id]
        ?.takeIf { it.ownerId == ownerId }
        ?: throw PortabilityJobNotFoundException()

    fun remove(ownerId: String, id: String) {
        val job = get(ownerId, id)
        if (jobs.remove(id, job)) job.delete()
    }

    fun cleanup() {
        val oldest = clock() - retentionMs
        jobs.entries.removeIf { entry ->
            val expired = entry.value.isTerminal() && entry.value.isTaskComplete() && entry.value.snapshot().updatedAt < oldest
            if (expired) entry.value.delete()
            expired
        }
    }

    override fun close() {
        jobs.values.forEach(PortabilityJob::delete)
        jobs.clear()
        Files.deleteIfExists(root)
    }

    private companion object {
        const val DEFAULT_RETENTION_MS = 24L * 60L * 60L * 1_000L
    }
}

class PortabilityJobNotFoundException : NoSuchElementException("Portability job not found")
