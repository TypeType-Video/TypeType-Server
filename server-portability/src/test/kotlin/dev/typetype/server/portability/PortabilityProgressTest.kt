package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PortabilityProgressTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `progress interval publishes every record for small imports`() {
        val job = job()
        val progress = PortabilityProgressReporter(
            job,
            PortabilityProgressPhase.APPLYING,
            PortabilityProgressUnit.RECORDS,
            total = 47L,
            interval = portabilityProgressInterval(47L),
        )

        progress.add()

        assertEquals(1L, job.snapshot().progress?.processed)
        assertEquals(47L, job.snapshot().progress?.total)
    }

    @Test
    fun `progress interval stays bounded for large imports`() {
        assertEquals(1L, portabilityProgressInterval(0L))
        assertEquals(1L, portabilityProgressInterval(47L))
        assertEquals(8L, portabilityProgressInterval(839L))
        assertEquals(100L, portabilityProgressInterval(42_622L))
        assertEquals(100L, portabilityProgressInterval(null))
    }

    private fun job() = PortabilityJob(
        id = "progress-test",
        ownerId = "owner",
        kind = PortabilityJobKind.IMPORT,
        directory = directory,
        requestId = null,
        clock = System::currentTimeMillis,
    )
}
