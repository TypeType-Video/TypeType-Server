package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class NewPipePortabilityAdapterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `adapter streams supported subscriptions and reports unsupported services`() {
        val file = directory.resolve("newpipe.json")
        Files.writeString(
            file,
            """{"subscriptions":[{"service_id":0,"url":"https://www.youtube.com/channel/UC1","name":"One"},{"service_id":1,"url":"https://soundcloud.com/two","name":"Two"}],"app_version":"0.28.0","app_version_int":1000}""",
        )
        val input = PortabilityInputFactory.create(file, file.fileName.toString(), "application/json")
        val spool = PortabilitySpool.create(directory)
        val adapter = NewPipePortabilityAdapter()

        assertEquals(PortabilityFormat.NEW_PIPE, requireNotNull(adapter.detect(input)).format)
        adapter.decode(input, spool)
        assertEquals(mapOf(PortabilityCategory.SUBSCRIPTIONS to 1L), spool.counts())
        assertEquals("unsupported_subscription_provider", spool.issues().single().code)

        val output = directory.resolve("export.zip")
        Files.newOutputStream(output).use { adapter.encode(spool, it, setOf(PortabilityCategory.SUBSCRIPTIONS)) }
        val restored = PortabilitySpool.create(directory)
        adapter.decode(PortabilityInputFactory.create(output, "export.zip", "application/zip"), restored)
        assertEquals(1L, restored.counts()[PortabilityCategory.SUBSCRIPTIONS])
        restored.delete()
        spool.delete()
    }

    @Test
    fun `adapter preserves an empty subscription section`() {
        val file = directory.resolve("empty.json")
        Files.writeString(file, """{"subscriptions":[],"app_version":"0.28.0"}""")
        val spool = PortabilitySpool.create(directory)

        NewPipePortabilityAdapter().decode(
            PortabilityInputFactory.create(file, file.fileName.toString(), "application/json"),
            spool,
        )

        assertEquals(mapOf(PortabilityCategory.SUBSCRIPTIONS to 0L), spool.counts())
        spool.delete()
    }
}
