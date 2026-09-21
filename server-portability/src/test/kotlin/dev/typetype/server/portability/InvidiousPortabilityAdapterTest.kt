package dev.typetype.server.portability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

class InvidiousPortabilityAdapterTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `adapter preserves invidious account categories`() {
        val file = directory.resolve("invidious.json")
        Files.writeString(
            file,
            """{"subscriptions":["UC1"],"watch_history":["video000001"],"preferences":{"thin_mode":true},"playlists":[{"title":"Saved","description":"Keep","privacy":"Private","videos":["video000002"]}]}""",
        )
        val input = PortabilityInputFactory.create(file, file.fileName.toString(), "application/json")
        val spool = PortabilitySpool.create(directory)
        val adapter = InvidiousPortabilityAdapter()

        assertEquals(PortabilityFormat.INVIDIOUS, requireNotNull(adapter.detect(input)).format)
        adapter.decode(input, spool)
        assertEquals(1L, spool.counts()[PortabilityCategory.SUBSCRIPTIONS])
        assertEquals(1L, spool.counts()[PortabilityCategory.HISTORY])
        assertEquals(2L, spool.counts()[PortabilityCategory.PLAYLISTS])
        assertEquals(1L, spool.counts()[PortabilityCategory.SETTINGS])
        assertEquals("missing_history_dates", spool.issues().single().code)

        val output = ByteArrayOutputStream()
        adapter.encode(spool, output, spool.categories())
        val json = output.toString()
        assertTrue(json.contains("\"subscriptions\":[\"UC1\"]"))
        assertTrue(json.contains("\"title\":\"Saved\""))
        spool.delete()
    }
}
