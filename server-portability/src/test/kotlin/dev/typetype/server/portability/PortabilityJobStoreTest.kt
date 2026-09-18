package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PortabilityJobStoreTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `cleanup never removes active jobs`() {
        var now = 1_000L
        val store = PortabilityJobStore(directory.resolve("jobs"), { now }, retentionMs = 100L)
        val job = store.create("owner", PortabilityJobKind.EXPORT)

        now += 1_000L
        store.cleanup()

        assertEquals(job.id, store.get("owner", job.id).id)
        store.close()
    }

    @Test
    fun `cleanup removes completed jobs after retention`() {
        var now = 1_000L
        val store = PortabilityJobStore(directory.resolve("jobs"), { now }, retentionMs = 100L)
        val job = store.create("owner", PortabilityJobKind.EXPORT)
        job.transition(setOf(PortabilityJobState.QUEUED), PortabilityJobState.COMPLETED)

        now += 1_000L
        store.cleanup()

        assertThrows(PortabilityJobNotFoundException::class.java) { store.get("owner", job.id) }
        store.close()
    }
}
